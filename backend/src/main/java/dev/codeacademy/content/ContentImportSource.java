package dev.codeacademy.content;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * One immutable source revision, read only through Git objects. Never reads HEAD, the index, a branch, a tag or any
 * working-tree file, and never asks Git for an archive of the repository root: the tree is listed once and blobs are
 * read through a single long-lived {@code git cat-file --batch} process, so the number of Git processes a generation
 * starts does not grow with the number of files in the course.
 */
final class ContentImportSource implements AutoCloseable {

	private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

	private final String commit;
	/** Regular blobs of the tree in path order: a folder query is a range of this map, never a scan. */
	private final NavigableMap<String, String> blobs;
	private final NavigableSet<String> others;
	/** Every folder that holds at least one regular blob, with its trailing slash. */
	private final Set<String> directories;
	private final Process batch;
	private final OutputStream requests;
	private final InputStream replies;

	private ContentImportSource(String commit, NavigableMap<String, String> blobs,
			NavigableSet<String> others, Set<String> directories, Process batch) {
		this.commit = commit;
		this.blobs = blobs;
		this.others = others;
		this.directories = directories;
		this.batch = batch;
		this.requests = batch.getOutputStream();
		this.replies = new BufferedInputStream(batch.getInputStream(), 1 << 16);
	}

	/** FORMATO.md 11.1: a published version is tagged v + MAJOR.MINOR.PATCH, e.g. v2.1.0. */
	static final java.util.regex.Pattern VERSION_TAG =
		java.util.regex.Pattern.compile("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

	/**
	 * The full commit a version tag names, lightweight or annotated. Only refs/tags/ is read, so a branch with the same
	 * name never answers, and the repository is only read (Git objects and refs).
	 */
	static String resolveTag(Path repository, String tag) throws IOException {
		if (tag == null || !VERSION_TAG.matcher(tag).matches()) {
			throw new IllegalArgumentException("--tag must be a version tag like v1.2.3");
		}
		byte[] resolved = ContentUpdateComparison.git(repository, "rev-parse", "--verify", "--quiet",
			"refs/tags/" + tag + "^{commit}");
		String commit = resolved == null ? "" : new String(resolved, StandardCharsets.UTF_8).strip();
		if (!COMMIT.matcher(commit).matches()) throw new IllegalArgumentException("source tag unavailable " + tag);
		return commit;
	}

	static ContentImportSource open(Path repository, String commit) throws IOException {
		if (commit == null || !COMMIT.matcher(commit).matches()) {
			throw new IllegalArgumentException("--commit must be a full 40-character commit hash");
		}
		byte[] type = ContentUpdateComparison.git(repository, "cat-file", "-t", commit);
		if (type == null || !new String(type, StandardCharsets.UTF_8).strip().equals("commit")) {
			throw new IllegalArgumentException("source commit unavailable " + commit);
		}
		byte[] listing = ContentUpdateComparison.git(repository, "ls-tree", "-r", "-z", "--full-tree", commit);
		if (listing == null) throw new IOException("source commit tree cannot be listed");
		NavigableMap<String, String> blobs = new TreeMap<>();
		NavigableSet<String> others = new TreeSet<>();
		Set<String> directories = new TreeSet<>();
		for (String entry : new String(listing, StandardCharsets.UTF_8).split("\u0000")) {
			if (entry.isEmpty()) continue;
			int tab = entry.indexOf('\t');
			String[] header = entry.substring(0, tab).split(" ");
			String path = entry.substring(tab + 1);
			if (header.length == 3 && header[1].equals("blob")
					&& (header[0].equals("100644") || header[0].equals("100755"))) {
				blobs.put(path, header[2]);
				// One pass over the whole tree: every folder above a regular file becomes an exact folder answer.
				for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
					directories.add(path.substring(0, slash + 1));
				}
			}
			else {
				others.add(path);
			}
		}
		return new ContentImportSource(commit, blobs, others, Set.copyOf(directories),
			// The shared reader only returns complete output, so the streaming object reader starts its own process.
			ContentUpdateComparison.gitProcess(repository, "cat-file", "--batch").start());
	}

	String commit() {
		return commit;
	}

	boolean file(String path) {
		return blobs.containsKey(path);
	}

	boolean directory(String path) {
		return directories.contains(path);
	}

	/** Every tree entry below a folder, sorted: regular files and the symbolic links or submodules that are refused. */
	SortedSet<String> entries(String folder) {
		SortedSet<String> entries = new TreeSet<>();
		for (String path : blobs.tailMap(folder, true).keySet()) {
			if (!path.startsWith(folder)) break;
			entries.add(path);
		}
		for (String path : others.tailSet(folder, true)) {
			if (!path.startsWith(folder)) break;
			entries.add(path);
		}
		return entries;
	}

	/** The committed bytes of one regular blob, or null when the path is not a regular blob of this revision. */
	byte[] read(String path) throws IOException {
		String object = blobs.get(path);
		if (object == null) return null;
		requests.write((object + "\n").getBytes(StandardCharsets.US_ASCII));
		requests.flush();
		String[] header = header(object).split(" ");
		// The batch protocol answers `<object> <type> <size>` with the exact bytes, or `<object> missing`.
		if (header.length == 2 && header[1].equals("missing")) return null;
		if (header.length != 3 || !header[1].equals("blob")) throw new IOException("source object is not a blob: " + path);
		long size;
		try {
			size = Long.parseLong(header[2]);
		}
		catch (NumberFormatException ex) {
			throw new IOException("malformed source object header: " + path, ex);
		}
		if (size < 0 || size > Integer.MAX_VALUE) throw new IOException("source object too large: " + path);
		byte[] bytes = replies.readNBytes((int) size);
		if (bytes.length != (int) size) throw new IOException("truncated source object: " + path);
		if (replies.read() != '\n') throw new IOException("malformed source object: " + path);
		return bytes;
	}

	private String header(String object) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		for (int value = replies.read(); value >= 0; value = replies.read()) {
			if (value == '\n') return line.toString(StandardCharsets.UTF_8);
			line.write(value);
		}
		throw new IOException("source object stream ended: " + object);
	}

	/** Ends the object stream: the generation read every blob it needed and no process outlives it. */
	@Override
	public void close() {
		try {
			requests.close();
			replies.transferTo(OutputStream.nullOutputStream());
		}
		catch (IOException | RuntimeException ex) {
			// Discarding the reader: a closed pipe is never a content problem.
		}
		finally {
			try {
				if (!batch.waitFor(5, TimeUnit.SECONDS)) batch.destroyForcibly();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				batch.destroyForcibly();
			}
		}
	}
}
