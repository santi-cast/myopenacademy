package dev.codeacademy.content;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Synthetic v2 folders; no source repository access. */
public final class ZipFixture {
	public static final String ZIP = "demo-folder-zip";
	public static final String LOCAL = "demo/resources/folder.zip";
	public static final String FOLDER = "fase-01/codigo/hola/";

	public static void install(ContentFixture fixture) throws Exception {
		fixture.addResource("demo-nested", FOLDER + "nested/útil.java", "text/plain", new byte[] { 0, 1, 2, 3 });
		fixture.editManifest(m -> {
			m.put("schemaVersion", 2);
			m.putArray("projectStages");
			m.get("resources").forEach(r -> ((ObjectNode) r).put("kind", "file"));
			ObjectNode folder = ContentFixture.link(m, FOLDER);
			((ArrayNode) folder.get("resourceIds")).add("demo-nested");
			folder.put("zipResourceId", ZIP);
			ObjectNode zip = ((ArrayNode) m.get("resources")).addObject();
			zip.put("resourceId", ZIP).put("lessonId", "demo-01.01").put("kind", "zip")
				.put("sourcePath", FOLDER).put("localPath", LOCAL).put("mediaType", "application/zip")
				.put("fileName", "hola.zip");
			zip.set("memberResourceIds", folder.get("resourceIds").deepCopy());
		});
		var out = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(out)) {
			for (String id : List.of("demo-01.01-r-hello", "demo-01.01-r-other", "demo-nested")) {
				fixture.editManifest(m -> {
					var resource = ContentFixture.resource(m, id);
					try {
						byte[] bytes = Files.readAllBytes(fixture.root.resolve(resource.get("localPath").asString()));
						ZipEntry entry = new ZipEntry(resource.get("sourcePath").asString().substring(FOLDER.length()));
						entry.setTimeLocal(LocalDateTime.of(1980, 1, 2, 0, 0));
						entry.setMethod(ZipEntry.STORED);
						entry.setSize(bytes.length);
						CRC32 crc = new CRC32();
						crc.update(bytes);
						entry.setCrc(crc.getValue());
						zip.putNextEntry(entry);
						zip.write(bytes);
						zip.closeEntry();
					}
					catch (Exception ex) { throw new IllegalStateException(ex); }
				});
			}
		}
		Files.write(fixture.root.resolve(LOCAL), out.toByteArray());
		refresh(fixture);
	}

	public static void refresh(ContentFixture fixture) {
		fixture.editManifest(m -> {
			ObjectNode provenance = m.putObject("provenance");
			ArrayNode files = provenance.putArray("files");
			ArrayNode generated = provenance.putArray("generated");
			for (String array : List.of("lessons", "projectStages", "resources")) {
				m.get(array).forEach(entry -> {
					var path = entry.get(array.equals("resources") ? "localPath" : "bodyPath");
					if (path == null || path.isNull()) return;
					String local = path.asString();
					try {
						String hash = hash(Files.readAllBytes(fixture.root.resolve(local)));
						if (entry.has("kind") && entry.get("kind").asString().equals("zip")) {
							generated.addObject().put("localPath", local).put("copySha256", hash)
								.set("inputResourceIds", entry.get("memberResourceIds").deepCopy());
						}
						else files.addObject().put("sourcePath", entry.get("sourcePath").asString()).put("localPath", local)
							.put("sourceSha256", hash).put("copySha256", hash).putNull("transformation");
					}
					catch (Exception ex) { throw new IllegalStateException(ex); }
				});
			}
		});
	}

	static String hash(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
