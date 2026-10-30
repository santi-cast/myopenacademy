package dev.codeacademy.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipContentTest {
	@TempDir Path temp;
	ContentFixture fixture;

	@BeforeEach void setup() throws Exception {
		fixture = ContentFixture.copyInto(temp);
		ZipFixture.install(fixture);
	}

	@Test void loadsFolderZipAndPreservesOrderedIndividualOriginalNames() throws Exception {
		var course = fixture.load();
		String html = course.lessons().get("demo-01.01").bodyHtml();
		assertThat(html).contains("href=\"/api/resources/demo-folder-zip\"");
		assertThat(html.indexOf("/api/resources/demo-folder-zip")).isLessThan(html.indexOf("/api/resources/demo-01.01-r-hello"));
		assertThat(html).contains("Hello.java</a>, <a href=\"/api/resources/demo-01.01-r-other\">Other.java</a>, "
			+ "<a href=\"/api/resources/demo-nested\">útil.java</a>)");
		assertThat(course.resources().get(ZipFixture.ZIP).originalFileName()).isEqualTo("hola.zip");
		try (var zip = new ZipInputStream(Files.newInputStream(fixture.root.resolve(ZipFixture.LOCAL)))) {
			for (String id : java.util.List.of("demo-01.01-r-hello", "demo-01.01-r-other", "demo-nested")) {
				var resource = course.manifest().resources().stream().filter(r -> r.resourceId().equals(id)).findFirst().orElseThrow();
				assertThat(zip.getNextEntry().getName()).isEqualTo(resource.sourcePath().substring(ZipFixture.FOLDER.length()));
				assertThat(zip.readAllBytes()).isEqualTo(Files.readAllBytes(course.resources().get(id).localPath()));
			}
			assertThat(zip.getNextEntry()).isNull();
		}
	}

	@Test void importerHelperIsDeterministicAndNeverInstallsFiles() throws Exception {
		var course = fixture.load();
		Map<String, String> before = snapshot();
		class OpenStream extends java.io.ByteArrayOutputStream {
			boolean closed;
			@Override public void close() { closed = true; }
		}
		var first = new OpenStream();
		var second = new OpenStream();
		var originalZone = java.util.TimeZone.getDefault();
		try {
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
			FolderZip.write(course.manifest(), fixture.root, ZipFixture.ZIP, first);
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
			FolderZip.write(course.manifest(), fixture.root, ZipFixture.ZIP, second);
		}
		finally { java.util.TimeZone.setDefault(originalZone); }
		assertThat(first.closed).isFalse();
		assertThat(second.closed).isFalse();
		assertThat(first.toByteArray()).isEqualTo(second.toByteArray())
			.isEqualTo(Files.readAllBytes(fixture.root.resolve(ZipFixture.LOCAL)));
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test void foldersInStatementsAndSolutionsUseTheSameZipResolution() throws Exception {
		fixture.replace(ContentFixture.INTRO, "Enunciado dos.", "[folder](codigo/hola/)");
		fixture.replace(ContentFixture.INTRO, "Solución dos.", "[folder](codigo/hola/)");
		ZipFixture.refresh(fixture);
		var exercise = fixture.load().lessons().get("demo-01.01").exercises().get(1);
		for (String html : java.util.List.of(exercise.statementHtml(), exercise.solutionHtml())) {
			assertThat(html).contains("<a href=\"/api/resources/demo-folder-zip\">folder</a> (",
				"Hello.java</a>, <a href=\"/api/resources/demo-01.01-r-other\">Other.java</a>");
		}
	}

	@Test void copiedMemberSymlinksAreRejectedWithoutFollowingThem() throws Exception {
		Path member = fixture.root.resolve("demo/resources/demo-nested");
		Path target = fixture.root.resolve("demo/resources/demo-01.01-r-hello.java");
		Files.move(member, fixture.root.resolve("demo/resources/saved-member"));
		Files.createSymbolicLink(member, target);
		Map<String, String> before = snapshot();
		fixture.rejected("goes through a symbolic link");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test void educationalJwtNamedMembersAreIncludedWithoutSilentFiltering() throws Exception {
		byte[] teachingValue = "synthetic teaching value".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		String name = "secretos/mibiblioteca/jwt/secret";
		fixture.addResource("demo-teaching-jwt", ZipFixture.FOLDER + name, "text/plain", teachingValue);
		fixture.editManifest(m -> {
			ContentFixture.resource(m, "demo-teaching-jwt").put("kind", "file");
			((ArrayNode) ContentFixture.resource(m, ZipFixture.ZIP).get("memberResourceIds")).add("demo-teaching-jwt");
			((ArrayNode) ContentFixture.link(m, ZipFixture.FOLDER).get("resourceIds")).add("demo-teaching-jwt");
			try (var output = Files.newOutputStream(fixture.root.resolve(ZipFixture.LOCAL))) {
				Manifest manifest = tools.jackson.databind.json.JsonMapper.builder().build().treeToValue(m, Manifest.class);
				FolderZip.write(manifest, fixture.root, ZipFixture.ZIP, output);
			}
			catch (Exception ex) { throw new IllegalStateException(ex); }
		});
		ZipFixture.refresh(fixture);
		fixture.load();
		boolean found = false;
		try (var zip = new ZipInputStream(Files.newInputStream(fixture.root.resolve(ZipFixture.LOCAL)))) {
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (entry.getName().equals(name)) {
					assertThat(zip.readAllBytes()).isEqualTo(teachingValue);
					found = true;
				}
			}
		}
		assertThat(found).isTrue();
	}

	@Test void plainV1FolderOutputKeepsItsExactExpansion() {
		ContentFixture v1 = ContentFixture.copyInto(temp.resolve("v1"));
		assertThat(v1.load().lessons().get("demo-01.01").bodyHtml()).contains(
			"<code>codigo/hola/</code> (<a href=\"/api/resources/demo-01.01-r-hello\">Hello.java</a>, "
			+ "<a href=\"/api/resources/demo-01.01-r-other\">Other.java</a>)");
	}

	@ParameterizedTest
	@ValueSource(strings = { "self", "order", "duplicate", "missing", "wrongType", "wrongName", "unknownZip",
		"zipOnFile", "fileMetadata", "twoOwners", "memberOwner", "traversal", "absolute", "backslash",
		"generatedInputs", "missingGenerated", "extraGenerated", "duplicateGenerated", "generatedHash", "localCollision" })
	void rejectsInvalidZipContractsWithoutWrites(String rule) throws Exception {
		fixture.editManifest(m -> {
			ObjectNode zip = ContentFixture.resource(m, ZipFixture.ZIP);
			ObjectNode folder = ContentFixture.link(m, ZipFixture.FOLDER);
			ArrayNode members = (ArrayNode) zip.get("memberResourceIds");
			ArrayNode generated = (ArrayNode) m.get("provenance").get("generated");
			switch (rule) {
				case "self" -> members.add(ZipFixture.ZIP);
				case "order" -> { members.remove(0); members.add("demo-01.01-r-hello"); }
				case "duplicate" -> members.add("demo-nested");
				case "missing" -> members.remove(0);
				case "wrongType" -> zip.put("mediaType", "text/plain");
				case "wrongName" -> zip.put("fileName", "../hola.zip");
				case "unknownZip" -> folder.put("zipResourceId", "demo-unknown");
				case "zipOnFile" -> ContentFixture.link(m, "fase-01/codigo/hola/Hello.java").put("zipResourceId", ZipFixture.ZIP);
				case "fileMetadata" -> ContentFixture.resource(m, "demo-nested").put("fileName", "override.java");
				case "twoOwners" -> zip.put("stageId", "demo-stage");
				case "memberOwner" -> ContentFixture.resource(m, "demo-nested").put("lessonId", "demo-01.02");
				case "traversal", "absolute", "backslash" -> {
					String path = switch (rule) { case "traversal" -> ZipFixture.FOLDER + "../evil";
						case "absolute" -> "/evil"; default -> ZipFixture.FOLDER + "nested\\evil"; };
					ContentFixture.resource(m, "demo-nested").put("sourcePath", path);
				}
				case "generatedInputs" -> ((ObjectNode) generated.get(0)).putArray("inputResourceIds").add("demo-nested");
				case "missingGenerated" -> generated.remove(0);
				case "extraGenerated" -> generated.addObject().put("localPath", "demo/extra.zip");
				case "duplicateGenerated" -> generated.add(generated.get(0).deepCopy());
				case "generatedHash" -> ((ObjectNode) generated.get(0)).put("copySha256", "0".repeat(64));
				case "localCollision" -> zip.put("localPath", "demo/resources/demo-nested");
				default -> throw new AssertionError(rule);
			}
		});
		rejectUnchanged();
	}

	@ParameterizedTest @ValueSource(strings = { "payload", "trailing", "truncated", "header", "bomb", "symlink", "duplicate", "traversal", "absolute", "backslash", "self", "compressed" })
	void validatesArchiveBytesEvenWithMatchingInstalledHash(String rule) throws Exception {
		Path path = fixture.root.resolve(ZipFixture.LOCAL);
		byte[] bytes = Files.readAllBytes(path);
		switch (rule) {
			case "payload" -> bytes[40] ^= 1;
			case "header" -> bytes[0] = 0;
			case "trailing" -> bytes = java.util.Arrays.copyOf(bytes, bytes.length + 1);
			case "truncated" -> bytes = java.util.Arrays.copyOf(bytes, bytes.length - 1);
			case "bomb" -> { bytes[22] = (byte) 0xff; bytes[23] = (byte) 0xff; bytes[24] = (byte) 0xff; bytes[25] = 0x7f; }
			case "symlink" -> {
				for (int i = 0; i < bytes.length - 46; i++) if (bytes[i] == 0x50 && bytes[i + 1] == 0x4b && bytes[i + 2] == 1) {
					bytes[i + 5] = 3; bytes[i + 40] = (byte) 0xff; bytes[i + 41] = (byte) 0xa1; break;
				}
			}
			case "compressed" -> {
				var out = new java.io.ByteArrayOutputStream();
				try (var zip = new java.util.zip.ZipOutputStream(out)) {
					zip.putNextEntry(new java.util.zip.ZipEntry("Hello.java"));
					zip.write(new byte[1024 * 1024]);
					zip.closeEntry();
				}
				bytes = out.toByteArray();
			}
			case "duplicate", "traversal", "absolute", "backslash", "self" -> {
				byte[] from = "Other.java".getBytes(java.nio.charset.StandardCharsets.UTF_8);
				String name = switch (rule) {
					case "duplicate" -> "Hello.java";
					case "traversal" -> "../xx.java";
					case "absolute" -> "/evil.java";
					case "backslash" -> "\\evil.java";
					default -> "folder.zip";
				};
				byte[] to = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
				for (int i = 0; i <= bytes.length - from.length; i++) {
					if (java.util.Arrays.equals(java.util.Arrays.copyOfRange(bytes, i, i + from.length), from)) System.arraycopy(to, 0, bytes, i, to.length);
				}
			}
		}
		Files.write(path, bytes);
		ZipFixture.refresh(fixture);
		Map<String, String> before = snapshot();
		fixture.rejected("ZIP does not match registered copied members");
		assertThat(snapshot()).isEqualTo(before);
	}

	@ParameterizedTest @ValueSource(strings = { "fileName", "memberResourceIds", "zipResourceId" })
	void v1RejectsZipFieldsEvenWhenNull(String field) throws Exception {
		ContentFixture v1 = ContentFixture.copyInto(temp.resolve("v1"));
		v1.editManifest(m -> {
			ObjectNode target = field.equals("zipResourceId") ? ContentFixture.link(m, ZipFixture.FOLDER)
				: ContentFixture.resource(m, "demo-01.01-r-hello");
			target.putNull(field);
		});
		fixture = v1;
		Map<String, String> before = snapshot();
		v1.rejected("schemaVersion 1 does not allow");
		assertThat(snapshot()).isEqualTo(before);
	}

	void rejectUnchanged() throws Exception {
		Map<String, String> before = snapshot();
		assertThrows(ContentValidationException.class, fixture::load);
		assertThat(snapshot()).isEqualTo(before);
	}

	Map<String, String> snapshot() throws Exception {
		Map<String, String> result = new LinkedHashMap<>();
		try (var paths = Files.walk(fixture.root)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) result.put(path.toString(), ZipFixture.hash(Files.readAllBytes(path)));
		}
		return result;
	}
}
