package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * H12, T11 (S13; kit 774aeaa, FORMATO.md 7): when the destination of a «📁 Código» link is inside a Maven or Gradle
 * project, its ZIP is the ZIP of the whole project, the outermost one inside codigo/: the folder of a pom.xml with no
 * other pom.xml above it, or the folder with settings.gradle or settings.gradle.kts. It never goes above codigo/, and a
 * link outside any project keeps exactly today's archive. Everything is decided from the Git tree of the pinned commit.
 */
class ProjectZipTest {

	static final String PHASE = "fase-01-primeros-pasos/";
	static final String LESSON = PHASE + "01-ficheros-json.md";
	static final String STAGE = "proyecto/01-primer-catalogo.md";
	/** The projects, relative to the lesson's folder. */
	static final String PROJECTS = "codigo/01-ficheros-json/proyectos/";
	static final String MAVEN = PROJECTS + "tienda/";
	static final String MULTI = PROJECTS + "multimodulo/";
	static final String GROOVY = PROJECTS + "gradle-groovy/";
	static final String KTS = PROJECTS + "gradle-kts/";
	static final String NESTED = PROJECTS + "anidado/";
	static final String LOOSE = PROJECTS + "suelto/";
	static final String OUTSIDE = "extra/fuera/";
	static final String UNIX_EXECUTABLE = "0755";

	@TempDir Path temp;
	Path tree;
	Path repository;
	/** The example course as it is, without projects: the oracle for every archive outside a project. */
	String plain;

	@BeforeEach
	void prepare() throws Exception {
		tree = temp.resolve("arbol");
		Path course = ExampleContent.COURSES.resolve("bueno");
		try (Stream<Path> files = Files.walk(course)) {
			for (Path file : files.toList()) {
				Path copy = tree.resolve(course.relativize(file).toString());
				if (Files.isDirectory(file)) Files.createDirectories(copy);
				else Files.copy(file, copy);
			}
		}
		// Named as the course's repositorio ends, so the list of installed courses can reinstall it.
		repository = temp.resolve("Cursos/curso-ejemplo");
		Files.createDirectories(repository.getParent());
		git(null, "init", "--bare", "--quiet", repository.toString());
		plain = commit("sin proyectos");
	}

	@Test
	void aLinkIntoAPackageOfAMavenProjectGetsTheWholeProjectWithMvnwExecutable() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		JsonNode manifest = ExecutableZipTest.manifest(output);
		JsonNode zip = zipOf(manifest, MAVEN + "src/main/java/es/tienda/");
		assertThat(zip.path("sourcePath").asString()).isEqualTo(PHASE + MAVEN);
		assertThat(zip.path("fileName").asString()).isEqualTo("tienda.zip");
		byte[] bytes = Files.readAllBytes(output.resolve(zip.path("localPath").asString()));
		assertThat(names(bytes)).containsExactly(".mvn/wrapper/maven-wrapper.properties", "mvnw", "pom.xml",
			"src/main/java/es/tienda/App.java", "src/main/java/es/tienda/modelo/Precio.java",
			"src/test/java/es/tienda/AppTest.java");
		assertThat(mode(bytes, "mvnw")).isEqualTo(UNIX_EXECUTABLE);
		assertThat(mode(bytes, "pom.xml")).isNull();
		// The linked folder still lists only its own files after the archive of the project.
		String html = lessonHtml(output);
		assertThat(html).contains("/api/resources/" + zip.path("resourceId").asString());
		for (JsonNode link : manifest.path("sourceToId")) {
			if (!(PHASE + MAVEN + "src/main/java/es/tienda/").equals(link.path("sourcePath").asString())) continue;
			List<String> listed = new ArrayList<>();
			link.path("resourceIds").forEach(id -> listed.add(id.asString()));
			assertThat(listed).hasSize(2).allMatch(id -> id.contains("-r-appjava-") || id.contains("-r-preciojava-"));
		}
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
	}

	@Test
	void aLinkIntoAModuleOfAMultiModuleProjectGetsTheRootThatGroupsTheModules() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		JsonNode manifest = ExecutableZipTest.manifest(output);
		JsonNode zip = zipOf(manifest, MULTI + "modelo/src/main/java/es/modelo/");
		assertThat(zip.path("sourcePath").asString()).isEqualTo(PHASE + MULTI);
		assertThat(names(Files.readAllBytes(output.resolve(zip.path("localPath").asString())))).containsExactly(
			"app/pom.xml", "app/src/main/java/es/app/Main.java", "modelo/pom.xml",
			"modelo/src/main/java/es/modelo/Libro.java", "pom.xml");
	}

	@Test
	void aGradleProjectIsTheFolderWithSettingsGradleOrSettingsGradleKts() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		JsonNode manifest = ExecutableZipTest.manifest(output);
		JsonNode groovy = zipOf(manifest, GROOVY + "app/src/main/java/es/app/");
		assertThat(groovy.path("sourcePath").asString()).isEqualTo(PHASE + GROOVY);
		byte[] bytes = Files.readAllBytes(output.resolve(groovy.path("localPath").asString()));
		assertThat(names(bytes)).containsExactly("app/build.gradle", "app/src/main/java/es/app/Main.java", "gradlew",
			"settings.gradle");
		assertThat(mode(bytes, "gradlew")).isEqualTo(UNIX_EXECUTABLE);
		JsonNode kts = zipOf(manifest, KTS + "src/main/java/es/k/");
		assertThat(kts.path("sourcePath").asString()).isEqualTo(PHASE + KTS);
		assertThat(names(Files.readAllBytes(output.resolve(kts.path("localPath").asString())))).containsExactly(
			"build.gradle.kts", "settings.gradle.kts", "src/main/java/es/k/Main.java");
	}

	@Test
	void aNestedPomXmlBelongsToTheOutermostProject() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		JsonNode manifest = ExecutableZipTest.manifest(output);
		// The link goes to the folder of the inner pom.xml itself: the outer one wins, not the first one found.
		JsonNode zip = zipOf(manifest, NESTED + "interno/");
		assertThat(zip.path("sourcePath").asString()).isEqualTo(PHASE + NESTED);
		assertThat(names(Files.readAllBytes(output.resolve(zip.path("localPath").asString())))).containsExactly(
			"interno/pom.xml", "interno/src/main/java/es/i/Interno.java", "pom.xml");
	}

	@Test
	void outsideAnyProjectAndNeverAboveCodigoTheArchiveIsTheLinkedFolder() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		JsonNode manifest = ExecutableZipTest.manifest(output);
		// A Gradle subproject without settings above it is not a project for this rule; neither is a pom.xml at the
		// course root, in the phase folder or in codigo/ itself, nor a project outside codigo/.
		for (String folder : List.of(LOOSE + "src/main/java/es/s/", OUTSIDE + "src/main/java/es/f/")) {
			JsonNode zip = zipOf(manifest, folder);
			assertThat(zip.path("sourcePath").asString()).isEqualTo(PHASE + folder);
			assertThat(names(Files.readAllBytes(output.resolve(zip.path("localPath").asString())))).containsExactly("Main.java");
		}
	}

	@Test
	void aLinkOutsideAnyProjectKeepsTheSameResourceAndTheSameBytes() throws Exception {
		Path before = generate(plain, "antes");
		Path after = generate(withProjects(true), "despues");
		JsonNode old = ExecutableZipTest.manifest(before);
		JsonNode now = ExecutableZipTest.manifest(after);
		int archives = 0;
		for (JsonNode resource : old.path("resources")) {
			if (!"zip".equals(resource.path("kind").asString())) continue;
			archives++;
			JsonNode same = ExecutableZipTest.resource(now, resource.path("sourcePath").asString());
			assertThat(same).isEqualTo(resource);
			assertThat(Files.readAllBytes(after.resolve(same.path("localPath").asString())))
				.isEqualTo(Files.readAllBytes(before.resolve(resource.path("localPath").asString())));
		}
		assertThat(archives).isEqualTo(6);
	}

	@Test
	void severalLinksIntoOneProjectShareOneArchive() throws Exception {
		Path output = generate(withProjects(true), "contenido");
		JsonNode manifest = ExecutableZipTest.manifest(output);
		// Two folders of the lesson and one of the stage, all inside the same Maven project.
		JsonNode main = zipOf(manifest, MAVEN + "src/main/java/es/tienda/");
		assertThat(zipOf(manifest, MAVEN + "src/test/java/es/tienda/")).isEqualTo(main);
		assertThat(zipOf(manifest, MAVEN + "src/main/java/es/tienda/modelo/")).isEqualTo(main);
		List<JsonNode> archives = new ArrayList<>();
		for (JsonNode resource : manifest.path("resources")) {
			if ("zip".equals(resource.path("kind").asString())
					&& resource.path("sourcePath").asString().startsWith(PHASE + MAVEN)) archives.add(resource);
		}
		assertThat(archives).containsExactly(main);
		int generated = 0;
		for (JsonNode file : manifest.path("provenance").path("generated")) {
			if (file.path("localPath").asString().equals(main.path("localPath").asString())) generated++;
		}
		assertThat(generated).isEqualTo(1);
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
	}

	@Test
	void generatingTwiceGivesTheSameBytes() throws Exception {
		String commit = withProjects(true);
		assertThat(ExecutableZipTest.snapshot(generate(commit, "segundo")))
			.isEqualTo(ExecutableZipTest.snapshot(generate(commit, "primero")));
	}

	@Test
	void theLoaderRefusesALinkDecoratedWithTheArchiveOfAFolderThatIsNotItsProject() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		String maven = zipOf(ExecutableZipTest.manifest(output), MAVEN + "src/main/java/es/tienda/").path("resourceId").asString();
		String loose = PHASE + LOOSE + "src/main/java/es/s/";
		ExecutableZipTest.edit(output, manifest -> {
			for (JsonNode link : manifest.path("sourceToId")) {
				if (loose.equals(link.path("sourcePath").asString())) ((ObjectNode) link).put("zipResourceId", maven);
			}
		});
		assertThat(ExecutableZipTest.rejected(output)).contains("folder ZIP does not match ordered members: " + loose);
	}

	@Test
	void theLoaderRefusesALinkDecoratedWithTheArchiveOfAFolderAboveItsProject() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		// The lesson also links the folder that holds every project: above the project, never its archive.
		String above = zipOf(ExecutableZipTest.manifest(output), PROJECTS).path("resourceId").asString();
		String folder = PHASE + MAVEN + "src/main/java/es/tienda/";
		ExecutableZipTest.edit(output, manifest -> {
			for (JsonNode link : manifest.path("sourceToId")) {
				if (folder.equals(link.path("sourcePath").asString())) ((ObjectNode) link).put("zipResourceId", above);
			}
		});
		assertThat(ExecutableZipTest.rejected(output)).contains("folder ZIP does not match ordered members: " + folder);
	}

	@Test
	void theLoaderRefusesALinkDecoratedWithTheArchiveOfAnotherProject() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		// A folder inside the Maven project, decorated with the archive of the Gradle Kotlin project: not its outermost.
		String other = zipOf(ExecutableZipTest.manifest(output), KTS + "src/main/java/es/k/").path("resourceId").asString();
		String folder = PHASE + MAVEN + "src/test/java/es/tienda/";
		ExecutableZipTest.edit(output, manifest -> {
			for (JsonNode link : manifest.path("sourceToId")) {
				if (folder.equals(link.path("sourcePath").asString())) ((ObjectNode) link).put("zipResourceId", other);
			}
		});
		Map<String, String> copy = ExecutableZipTest.snapshot(output);
		assertThat(ExecutableZipTest.rejected(output)).contains("folder ZIP does not match ordered members: " + folder);
		assertThat(ExecutableZipTest.snapshot(output)).isEqualTo(copy);
	}

	@Test
	void theLoaderRefusesAProjectArchiveWhoseOwnerDoesNotClaimTheLinkedFolder() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		JsonNode generated = ExecutableZipTest.manifest(output);
		String zip = zipOf(generated, MAVEN + "src/main/java/es/tienda/").path("resourceId").asString();
		String stage = generated.path("projectStages").get(0).path("id").asString();
		String folder = PHASE + MAVEN + "src/main/java/es/tienda/";
		// Only the lesson claims the project and its folders; the project's archive now says the stage owns it.
		ExecutableZipTest.edit(output, manifest -> {
			for (JsonNode resource : manifest.path("resources")) {
				if (zip.equals(resource.path("resourceId").asString())) {
					((ObjectNode) resource).putNull("lessonId").put("stageId", stage);
				}
			}
		});
		Map<String, String> copy = ExecutableZipTest.snapshot(output);
		assertThat(ExecutableZipTest.rejected(output))
			.contains("folder ZIP must be owned by an owner that claims the folder: " + folder);
		assertThat(ExecutableZipTest.snapshot(output)).isEqualTo(copy);
	}

	@Test
	void twoFoldersOfOneOwnerInsideAProjectShareItsArchiveWithoutListingItsMembers() throws Exception {
		Path output = generate(withProjects(false), "contenido");
		JsonNode manifest = ExecutableZipTest.manifest(output);
		JsonNode main = zipOf(manifest, MAVEN + "src/main/java/es/tienda/");
		assertThat(zipOf(manifest, MAVEN + "src/test/java/es/tienda/")).isEqualTo(main);
		assertThat(main.path("sourcePath").asString()).isEqualTo(PHASE + MAVEN);
		// Neither folder lists every member of the project's archive: it is checked once, by the project's own entry.
		for (String folder : List.of(MAVEN + "src/main/java/es/tienda/", MAVEN + "src/test/java/es/tienda/")) {
			for (JsonNode link : manifest.path("sourceToId")) {
				if (!(PHASE + folder).equals(link.path("sourcePath").asString())) continue;
				List<String> listed = new ArrayList<>();
				link.path("resourceIds").forEach(id -> listed.add(id.asString()));
				List<String> members = new ArrayList<>();
				main.path("memberResourceIds").forEach(id -> members.add(id.asString()));
				assertThat(listed).isNotEmpty().isSubsetOf(members).hasSizeLessThan(members.size());
			}
		}
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
	}

	@Test
	void anUpdateThatTurnsAFolderIntoAProjectIsAChangeAndAppliesTheProjectArchive() throws Exception {
		// The same folders, first without the build files (the archive is the linked folder), then with them.
		String before = withProjects(false, false);
		String after = withProjects(false);
		Path empty = Files.createDirectory(temp.resolve("vacio"));
		Path active = generate(before, empty, "activa");
		String folderZip = zipOf(ExecutableZipTest.manifest(active), MAVEN + "src/main/java/es/tienda/")
			.path("resourceId").asString();
		Path candidate = generate(after, active, "candidata");
		String projectZip = zipOf(ExecutableZipTest.manifest(candidate), MAVEN + "src/main/java/es/tienda/")
			.path("resourceId").asString();
		assertThat(projectZip).isNotEqualTo(folderZip);
		Path report = temp.resolve("informe-actualizacion.json");
		assertThat(update("compare", active, candidate, report))
			.isEqualTo(new ExecutableZipTest.Result(0, "Comparison written; conflicts: 0\n", ""));
		JsonNode written = ExecutableZipTest.JSON.readTree(report.toFile());
		assertThat(written.path("conflicts")).isEmpty();
		List<String> changes = new ArrayList<>();
		written.path("changes").forEach(change -> changes.add(change.asString()));
		// A ZIP holds no progress: the archive the link no longer uses goes, the project's comes.
		assertThat(changes).contains("removal " + folderZip).anyMatch(change -> change.startsWith("addition " + projectZip + ": "));
		assertThat(update("apply", active, candidate, report).code()).isZero();
		JsonNode manifest = ExecutableZipTest.manifest(active);
		JsonNode zip = zipOf(manifest, MAVEN + "src/main/java/es/tienda/");
		assertThat(zip.path("sourcePath").asString()).isEqualTo(PHASE + MAVEN);
		assertThat(mode(Files.readAllBytes(active.resolve(zip.path("localPath").asString())), "mvnw")).isEqualTo(UNIX_EXECUTABLE);
		assertThat(ContentLoader.loadAll(active)).hasSize(1);
	}

	@Test
	void reinstallingTheListKeepsTheProjectArchive() throws Exception {
		String commit = withProjects(true);
		git(repository, "tag", "v1.0.0", commit);
		Path empty = Files.createDirectory(temp.resolve("vacio"));
		Path active = temp.resolve("activa");
		assertThat(cli("generate", "--active", empty.toString(), "--source", repository.toString(), "--tag", "v1.0.0",
			"--output", active.toString(), "--report", temp.resolve("informe-activa.json").toString()).code()).isZero();
		Path list = temp.resolve("cursos.json");
		assertThat(ContentCoursesCliTest.run("list", "--active", active.toString(), "--output", list.toString()).code()).isZero();
		Path output = temp.resolve("reinstalada");
		assertThat(ContentCoursesCliTest.run("reinstall", "--list", list.toString(), "--sources",
			repository.getParent().toString(), "--output", output.toString(), "--report", temp.resolve("r.json").toString()))
			.isEqualTo(new ContentCoursesCliTest.Result(0, "Copy written; courses: 1\n", ""));
		assertThat(ExecutableZipTest.snapshot(output)).isEqualTo(ExecutableZipTest.snapshot(active));
		JsonNode zip = zipOf(ExecutableZipTest.manifest(output), MULTI + "modelo/src/main/java/es/modelo/");
		assertThat(zip.path("sourcePath").asString()).isEqualTo(PHASE + MULTI);
	}

	/** The example course with every project and its links; {@code stage} adds a stage link into the Maven project. */
	String withProjects(boolean stage) throws Exception {
		return withProjects(stage, true);
	}

	/** {@code build}: whether the build files that make each folder a project are committed. */
	String withProjects(boolean stage, boolean build) throws Exception {
		Map<String, String> files = new TreeMap<>();
		files.put(MAVEN + "mvnw", "#!/bin/sh\necho mvnw\n");
		files.put(MAVEN + ".mvn/wrapper/maven-wrapper.properties", "distributionType=only-script\n");
		files.put(MAVEN + "src/main/java/es/tienda/App.java", "package es.tienda;\n");
		files.put(MAVEN + "src/main/java/es/tienda/modelo/Precio.java", "package es.tienda.modelo;\n");
		files.put(MAVEN + "src/test/java/es/tienda/AppTest.java", "package es.tienda;\n");
		files.put(MULTI + "modelo/src/main/java/es/modelo/Libro.java", "package es.modelo;\n");
		files.put(MULTI + "app/src/main/java/es/app/Main.java", "package es.app;\n");
		files.put(GROOVY + "gradlew", "#!/bin/sh\necho gradlew\n");
		files.put(GROOVY + "app/src/main/java/es/app/Main.java", "package es.app;\n");
		files.put(KTS + "src/main/java/es/k/Main.java", "package es.k;\n");
		files.put(NESTED + "interno/src/main/java/es/i/Interno.java", "package es.i;\n");
		files.put(LOOSE + "build.gradle", "plugins { id 'java' }\n");
		files.put(LOOSE + "src/main/java/es/s/Main.java", "package es.s;\n");
		files.put(OUTSIDE + "src/main/java/es/f/Main.java", "package es.f;\n");
		if (build) {
			for (String pom : List.of(MAVEN, MULTI, MULTI + "modelo/", MULTI + "app/", NESTED, NESTED + "interno/", OUTSIDE)) {
				files.put(pom + "pom.xml", "<project/>\n");
			}
			files.put(GROOVY + "settings.gradle", "include 'app'\n");
			files.put(GROOVY + "app/build.gradle", "plugins { id 'java' }\n");
			files.put(KTS + "settings.gradle.kts", "rootProject.name = \"k\"\n");
			files.put(KTS + "build.gradle.kts", "plugins { java }\n");
			// Never above codigo/: the course root, the phase folder and codigo/ itself do not count.
			files.put("../pom.xml", "<project/>\n");
			files.put("pom.xml", "<project/>\n");
			files.put("codigo/pom.xml", "<project/>\n");
			files.put("codigo/settings.gradle", "\n");
		}
		for (var file : files.entrySet()) {
			Path path = tree.resolve(PHASE).resolve(file.getKey()).normalize();
			Files.createDirectories(path.getParent());
			Files.writeString(path, file.getValue());
		}
		StringBuilder links = new StringBuilder("\n");
		for (String folder : List.of(MAVEN + "src/main/java/es/tienda/", MAVEN + "src/test/java/es/tienda/",
				MULTI + "modelo/src/main/java/es/modelo/", GROOVY + "app/src/main/java/es/app/", KTS + "src/main/java/es/k/",
				NESTED + "interno/", LOOSE + "src/main/java/es/s/", OUTSIDE + "src/main/java/es/f/", PROJECTS)) {
			links.append("\n📁 **Código:** [`").append(folder).append("`](").append(folder).append(")\n");
		}
		Files.writeString(tree.resolve(LESSON), links, StandardOpenOption.APPEND);
		if (stage) {
			String folder = "../" + PHASE + MAVEN + "src/main/java/es/tienda/modelo/";
			Files.writeString(tree.resolve(STAGE), "\n📁 **Código:** [`" + folder + "`](" + folder + ")\n",
				StandardOpenOption.APPEND);
		}
		git(repository, "--work-tree=" + tree, "add", "--all");
		git(repository, "--work-tree=" + tree, "update-index", "--chmod=+x", "--", PHASE + MAVEN + "mvnw",
			PHASE + GROOVY + "gradlew");
		git(repository, "--work-tree=" + tree, "commit", "--quiet", "--message=proyectos");
		String commit = git(repository, "rev-parse", "HEAD").strip();
		// Back to the plain course on disk: each commit is built from it, and nothing is read from the disk anyway.
		git(repository, "--work-tree=" + tree, "checkout", "--quiet", plain, "--", LESSON, STAGE);
		return commit;
	}

	String commit(String message) throws Exception {
		git(repository, "--work-tree=" + tree, "add", "--all");
		git(repository, "--work-tree=" + tree, "commit", "--quiet", "--allow-empty", "--message=" + message);
		return git(repository, "rev-parse", "HEAD").strip();
	}

	/** The archive the sourceToId entry of a folder (relative to the lesson's folder) is decorated with. */
	static JsonNode zipOf(JsonNode manifest, String folder) {
		String path = PHASE + folder;
		for (JsonNode link : manifest.path("sourceToId")) {
			if (!path.equals(link.path("sourcePath").asString())) continue;
			String id = link.path("zipResourceId").asString();
			for (JsonNode resource : manifest.path("resources")) {
				if (id.equals(resource.path("resourceId").asString())) return resource;
			}
			throw new AssertionError("no ZIP resource " + id);
		}
		throw new AssertionError("no sourceToId entry " + path);
	}

	static List<String> names(byte[] zip) throws Exception {
		List<String> names = new ArrayList<>();
		try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (ZipEntry entry; (entry = input.getNextEntry()) != null;) names.add(entry.getName());
		}
		return names;
	}

	/** The Unix permissions of an entry as four octal digits, or null when the entry carries no Unix mode. */
	static String mode(byte[] zip, String name) {
		ExecutableZipTest.Central entry = ExecutableZipTest.central(zip).get(name);
		if (entry.madeBy() >> 8 != ExecutableZipTest.UNIX) return null;
		return String.format("%04o", (entry.external() >>> 16) & 07777);
	}

	static String lessonHtml(Path root) {
		return ContentLoader.loadAll(root).get(0).lessons().get("ejemplo.ficheros-json").bodyHtml();
	}

	Path generate(String commit, String name) throws Exception {
		return generate(commit, Files.createDirectories(temp.resolve("vacio-" + name)), name);
	}

	/** content-import in its own process, as the maintainer runs it. */
	Path generate(String commit, Path active, String name) throws Exception {
		Path output = temp.resolve(name);
		var result = cli("generate", "--active", active.toString(), "--source", repository.toString(), "--commit", commit,
			"--output", output.toString(), "--report", temp.resolve("informe-" + name + ".json").toString());
		assertThat(result.code()).as(result.out() + result.err() + Files.readString(temp.resolve("informe-" + name + ".json")))
			.isZero();
		return output;
	}

	ExecutableZipTest.Result cli(String... args) throws Exception {
		return run("dev.codeacademy.content.ContentImportCli", List.of(args));
	}

	ExecutableZipTest.Result update(String operation, Path active, Path candidate, Path report) throws Exception {
		return run("dev.codeacademy.content.ContentUpdateCli", List.of(operation, "--active", active.toString(),
			"--candidate", candidate.toString(), "--source", repository.toString(), "--report", report.toString()));
	}

	ExecutableZipTest.Result run(String main, List<String> args) throws Exception {
		List<String> command = new ArrayList<>(List.of("sh", "-c", "umask 022; exec \"$@\"", "content-test",
			Path.of(System.getProperty("java.home"), "bin/java").toString(),
			"-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-Duser.home=" + System.getProperty("user.home"),
			"-cp", System.getProperty("java.class.path"), main));
		command.addAll(args);
		ProcessBuilder builder = new ProcessBuilder(command);
		for (String variable : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
			builder.environment().remove(variable);
		}
		// Both streams go to files, so a full pipe can never block the process.
		Path out = Files.createTempFile(temp, "salida", ".txt");
		Path err = Files.createTempFile(temp, "errores", ".txt");
		Process process = builder.redirectOutput(out.toFile()).redirectError(err.toFile()).start();
		assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
		return new ExecutableZipTest.Result(process.exitValue(), Files.readString(out, StandardCharsets.UTF_8),
			Files.readString(err, StandardCharsets.UTF_8));
	}

	static String git(Path repository, String... args) throws Exception {
		return ExecutableZipTest.git(repository, args);
	}
}
