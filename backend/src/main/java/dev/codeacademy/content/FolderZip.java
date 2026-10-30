package dev.codeacademy.content;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Canonical folder ZIP format shared by installation validation and the future offline importer. No directory scan. */
public final class FolderZip {
	private static final LocalDateTime TIME = LocalDateTime.of(1980, 1, 2, 0, 0);

	private FolderZip() {
	}

	/**
	 * Writes ordered registered copied members as UTF-8 STORED entries with fixed metadata. Does not close output.
	 * The caller owns candidate installation and provenance; this method never reads source or writes a file itself.
	 */
	public static void write(Manifest manifest, Path root, String resourceId, OutputStream output) throws IOException {
		List<String> problems = ManifestValidator.validate(manifest);
		if (!problems.isEmpty()) throw new ContentValidationException(problems);
		Manifest.Resource archive = manifest.resources().stream().filter(r -> r.resourceId().equals(resourceId))
			.findFirst().orElseThrow(() -> new ContentValidationException("unknown ZIP resource " + resourceId));
		if (!Integer.valueOf(2).equals(manifest.schemaVersion()) || !"zip".equals(archive.kind())) {
			throw new ContentValidationException("not a v2 ZIP resource: " + resourceId);
		}
		writeMembers(manifest, root.toRealPath(), archive, output);
	}

	/**
	 * Compare canonical bytes, including central-directory attributes, CRCs, sizes and EOF. Never inflate untrusted
	 * archive data: work and memory depend on the registered copies, not attacker-declared expansion lengths.
	 */
	static void validate(Manifest manifest, Path root, Manifest.Resource archive, List<String> problems) {
		Path path = ContentPaths.file(root, archive.localPath(), "ZIP", problems);
		if (path == null) return;
		try (InputStream installed = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
			OutputStream compare = new OutputStream() {
				@Override
				public void write(int value) throws IOException {
					if (installed.read() != (value & 0xff)) throw new IOException("different ZIP byte");
				}

				@Override
				public void write(byte[] bytes, int offset, int length) throws IOException {
					byte[] actual = new byte[Math.min(length, 8192)];
					for (int start = 0; start < length;) {
						int count = Math.min(length - start, actual.length);
						if (installed.readNBytes(actual, 0, count) != count) throw new IOException("short ZIP");
						for (int i = 0; i < count; i++) {
							if (actual[i] != bytes[offset + start + i]) throw new IOException("different ZIP byte");
						}
						start += count;
					}
				}
			};
			writeMembers(manifest, root, archive, compare);
			if (installed.read() != -1) throw new IOException("trailing ZIP bytes");
		}
		catch (IOException ex) { problems.add("ZIP does not match registered copied members: " + archive.resourceId()); }
	}

	private static void writeMembers(Manifest manifest, Path root, Manifest.Resource archive, OutputStream output) throws IOException {
		Map<String, Manifest.Resource> resources = new HashMap<>();
		manifest.resources().forEach(resource -> resources.put(resource.resourceId(), resource));
		// Closing the ZIP frees its native deflater but leaves the caller's stream open.
		try (var zip = new ZipOutputStream(new FilterOutputStream(output) {
			@Override
			public void close() throws IOException {
				flush();
			}

			@Override
			public void write(byte[] bytes, int offset, int length) throws IOException {
				out.write(bytes, offset, length);
			}
		})) {
			for (String id : archive.memberResourceIds()) {
				Manifest.Resource member = resources.get(id);
				List<String> problems = new ArrayList<>();
				Path file = ContentPaths.file(root, member.localPath(), "ZIP member", problems);
				if (file == null) throw new ContentValidationException(problems);
				long size = Files.size(file);
				CRC32 crc = new CRC32();
				try (var input = new CheckedInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS), crc)) {
					copyExactly(input, OutputStream.nullOutputStream(), size);
				}
				ZipEntry entry = new ZipEntry(member.sourcePath().substring(archive.sourcePath().length()));
				entry.setTimeLocal(TIME);
				entry.setMethod(ZipEntry.STORED);
				entry.setSize(size);
				entry.setCrc(crc.getValue());
				zip.putNextEntry(entry);
				try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
					copyExactly(input, zip, size);
				}
				zip.closeEntry();
			}
		}
	}

	private static void copyExactly(InputStream input, OutputStream output, long size) throws IOException {
		byte[] buffer = new byte[8192];
		long remaining = size;
		while (remaining > 0) {
			int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
			if (count < 0) throw new IOException("member changed size");
			output.write(buffer, 0, count);
			remaining -= count;
		}
		if (input.read() != -1) throw new IOException("member changed size");
	}
}
