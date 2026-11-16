package dev.codeacademy.content;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Public process boundary of the offline candidate generator: synthetic Git objects only, no source course, no
 * database and no Spring context. Every rejection asserts the exact exit code, stdout, stderr and untouched state.
 */
class ContentImportCliTest {

	static final JsonMapper JSON = JsonMapper.builder().build();
	static final String HELP = "Usage: content-import generate --active DIR --source GIT --commit SHA --output DIR --report FILE\n"
		+ "Generate a new candidate copy offline; never applies it and never writes a database.\n";
	static final String INDEX = """
		# Índice del curso

		> **21 fases · 3 módulos · 2 etapas del proyecto evolutivo**

		## Fase 00 — Primera fase

		| # | Módulo | Nivel | Tiempo | Estado |
		|---|---|---|---|---|
		| 01.01 | [Introducción](fase-01/01-intro.md) | Básico · Núcleo | 2 h | ✅ Disponible |
		| 01.02 | [Siguiente](fase-01/02-siguiente.md) | Básico · Núcleo | 2 h | ✅ Disponible |

		## Fase 01 — Segunda fase

		| # | Módulo | Nivel | Tiempo | Estado |
		|---|---|---|---|---|
		| 02.01 | [Pendiente](fase-02/01-pendiente.md) | Básico · Núcleo | 2 h | ⏳ Pendiente |

		## Proyecto evolutivo

		| Etapa | Qué se construye | Tras | Estado |
		|---|---|---|---|
		| ◆ 1 | 🧭 [Etapa inicial](proyecto/01-inicial.md) | fase 01 | ✅ Disponible |
		| ◆ 2 | [Etapa siguiente](proyecto/02-siguiente.md) | fase 02 | ✅ Disponible |
		""";

	/**
	 * The course's own curso.json, committed with every tree: the identity the importer reads. It keeps the legacy
	 * demo.… IDs of the accepted fixture.
	 */
	static final String CURSO = """
		{
		  "formato": 1, "tipo": "curso", "id": "demo", "titulo": "Demo", "version": "1.0.0",
		  "fases": [
		    { "id": "demo.phase-01", "carpeta": "fase-01", "titulo": "Primera fase", "modulos": [
		      { "id": "demo.01.01", "fichero": "fase-01/01-intro.md", "titulo": "Introducción", "ejercicios": [
		        { "id": "demo.01.01.e01", "titulo": "Primer ejercicio" },
		        { "id": "demo.01.01.e02", "titulo": "Segundo ejercicio" } ] },
		      { "id": "demo.01.02", "fichero": "fase-01/02-siguiente.md", "titulo": "Siguiente", "ejercicios": [] } ] },
		    { "id": "demo.phase-02", "carpeta": "fase-02", "titulo": "Segunda fase", "modulos": [
		      { "id": "demo.02.01", "fichero": "fase-02/01-pendiente.md", "titulo": "Pendiente", "disponible": false } ] } ],
		  "proyecto": { "tipo": "etapas", "titulo": "Proyecto", "etapas": [
		    { "id": "demo.stage-01", "fichero": "proyecto/01-inicial.md", "titulo": "Etapa inicial" },
		    { "id": "demo.stage-02", "fichero": "proyecto/02-siguiente.md", "titulo": "Etapa siguiente" } ] }
		}
		""";

	/**
	 * Recorded on the unbatched generator: the same pinned tree must keep these exact bytes. The manifest was recorded
	 * again in H7, when its identity started to come from curso.json (source.name is now the course's own title).
	 */
	static final String SHARED_MANIFEST_SHA =
		"84eb1e33421a22703fafe359c0a257e5b47a95463d493d4ce9761a51e25ce886";
	static final List<String> SHARED_ARCHIVE_SHA = List.of(
		"a253f8f587fd349a355352d11015051ae223261e97e9b37734d837711b068eac",
		"5a38b4b35047d1d7604d2e0dccf3d45f0b3372b465573e1098ee7b19af6cb2e5",
		"d174b72546cc09adacac34534838bc54a2d2f1c221c91a53091fc5283e07a935",
		"7c54110036eb00acc1ec055c22540d48657aadde5a7c109f5d379c848f75c530");

	@TempDir Path temp;
	ContentFixture active;
	Path source;
	Path output;
	Path report;
	String base;
	String commit;
	Map<String, byte[]> files;
	ObjectNode curso;

	@BeforeEach void prepare() throws Exception {
		active = ContentFixture.copyInto(temp.resolve("active"));
		curso = (ObjectNode) JSON.readTree(CURSO);
		source = Files.createDirectory(temp.resolve("source.git"));
		git(null, "init", "--bare", "--quiet", source.toString());
		// The accepted base commit holds exactly the copies of the accepted H4 copy, as the pinned H4 ref does.
		Manifest accepted = active.load().manifest();
		files = new TreeMap<>();
		for (Manifest.Lesson lesson : accepted.lessons()) {
			if (lesson.available()) files.put(lesson.sourcePath(), copy(lesson.bodyPath()));
		}
		for (Manifest.Resource resource : accepted.resources()) {
			files.put(resource.sourcePath(), copy(resource.localPath()));
		}
		base = commit(new TreeMap<>(files), "base\n");
		active.editManifest(manifest -> manifest.withObject("/source").put("versionRef", base));
		// The pinned import commit adds the course index, the project stages and one more folder member.
		files.put("fase-01/codigo/hola/.gitignore", "*.class\n".getBytes(StandardCharsets.UTF_8));
		files.put("00-indice.md", INDEX.getBytes(StandardCharsets.UTF_8));
		files.put("proyecto/01-inicial.md", "# Etapa 1\n\nContenido de la etapa.\n".getBytes(StandardCharsets.UTF_8));
		files.put("proyecto/02-siguiente.md", "# Etapa 2\n\nMás contenido.\n".getBytes(StandardCharsets.UTF_8));
		commit = commit(files, "import\n");
		output = temp.resolve("candidate");
		report = temp.resolve("report.json");
	}

	@Test void helpIsOfflineAndInvalidArgumentsAreExact() throws Exception {
		Map<String, String> before = snapshot(temp);
		assertThat(cli("--help")).isEqualTo(new Result(0, HELP, ""));
		assertThat(cli("--force")).isEqualTo(new Result(2, "", "content-import: invalid arguments; use --help\n"));
		assertThat(cli("generate")).isEqualTo(new Result(2, "", "content-import: invalid arguments; use --help\n"));
		assertThat(cli("generate", "--active", active.root.toString())).isEqualTo(new Result(2, "",
			"content-import: invalid arguments; use --help\n"));
		assertThat(generate("--report", temp.resolve(".env.example").toString()))
			.isEqualTo(new Result(2, "", "content-import: protected paths are not allowed\n"));
		assertThat(snapshot(temp)).isEqualTo(before);
	}

	@Test void sourceInputIsOneFullImmutableCommit() throws Exception {
		Map<String, String> before = snapshot(temp);
		String invalid = "content-import: --commit must be a full 40-character commit hash\n";
		assertThat(generate("--commit", "HEAD")).isEqualTo(new Result(2, "", invalid));
		assertThat(generate("--commit", commit.substring(0, 12))).isEqualTo(new Result(2, "", invalid));
		assertThat(generate("--commit", "master")).isEqualTo(new Result(2, "", invalid));
		assertThat(generate("--commit", "1111111111111111111111111111111111111111"))
			.isEqualTo(new Result(2, "", "content-import: source commit unavailable 1111111111111111111111111111111111111111\n"));
		assertThat(snapshot(temp)).isEqualTo(before);
	}

	@Test void generatesANewCandidatePreservingAcceptedIdentity() throws Exception {
		Manifest accepted = active.load().manifest();
		Map<String, String> activeBefore = snapshot(active.root);
		// A bare repository has no working tree; an unrelated file at its root is never an import source.
		Files.writeString(source.resolve("untracked.txt"), "ignored");
		Map<String, String> sourceBefore = snapshot(source);
		assertThat(generate()).isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 4\n", ""));
		assertThat(Files.exists(output)).isTrue();
		JsonNode manifest = manifest();
		assertThat(manifest.path("schemaVersion").asInt()).isEqualTo(2);
		assertThat(manifest.path("source").path("versionRef").asString()).isEqualTo(commit);
		assertThat(manifest.path("lessons")).hasSize(3);
		for (int i = 0; i < accepted.lessons().size(); i++) {
			Manifest.Lesson lesson = accepted.lessons().get(i);
			JsonNode generated = manifest.path("lessons").get(i);
			assertThat(generated.path("id").asString()).isEqualTo(lesson.id());
			assertThat(generated.path("title").asString()).isEqualTo(lesson.title());
			assertThat(generated.path("position").asInt()).isEqualTo(lesson.position());
			assertThat(generated.path("phaseId").asString()).isEqualTo(lesson.phaseId());
		}
		JsonNode exercises = manifest.path("lessons").get(0).path("exercises");
		assertThat(exercises).hasSize(2);
		assertThat(exercises.get(0).path("id").asString()).isEqualTo("demo.01.01.e01");
		assertThat(exercises.get(0).path("title").asString()).isEqualTo(accepted.lessons().get(0).exercises().get(0).title());
		// The accepted copied bodies and resource bytes stay literal copies of the pinned tree.
		assertThat(Files.readAllBytes(output.resolve("demo/lessons/demo.01.01.md")))
			.isEqualTo(Files.readAllBytes(active.root.resolve("demo/lessons/demo.01.01.md")));
		for (Manifest.Resource resource : accepted.resources()) {
			JsonNode generated = resource(manifest, resource.sourcePath());
			assertThat(generated.path("resourceId").asString()).isEqualTo(resource.resourceId());
			assertThat(generated.path("localPath").asString()).isEqualTo(resource.localPath());
			assertThat(Files.readAllBytes(output.resolve(resource.localPath())))
				.isEqualTo(Files.readAllBytes(active.root.resolve(resource.localPath())));
		}
		JsonNode stages = manifest.path("projectStages");
		assertThat(stages).hasSize(2);
		assertThat(stages.get(0).path("id").asString()).isEqualTo("demo.stage-01");
		assertThat(stages.get(0).path("title").asString()).isEqualTo("Etapa inicial");
		assertThat(stages.get(0).path("sourcePath").asString()).isEqualTo("proyecto/01-inicial.md");
		assertThat(stages.get(0).path("bodyPath").asString()).isEqualTo("demo/project/demo.stage-01.md");
		assertThat(stages.get(0).path("available").asBoolean()).isTrue();
		assertThat(Files.readString(output.resolve("demo/project/demo.stage-01.md")))
			.isEqualTo("# Etapa 1\n\nContenido de la etapa.\n");
		// The course landing page is recorded so every link to the index resolves without importing it.
		assertThat(link(manifest, "00-indice.md").path("courseId").asString()).isEqualTo("demo");
		JsonNode data = JSON.readTree(Files.readString(report));
		assertThat(data.path("commit").asString()).isEqualTo(commit);
		assertThat(data.path("baseRefs").path("demo").asString()).isEqualTo(base);
		assertThat(data.path("lessons").asInt()).isEqualTo(3);
		assertThat(data.path("stages").asInt()).isEqualTo(2);
		assertThat(data.path("written").asBoolean()).isTrue();
		assertThat(data.path("findings")).isEmpty();
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	@Test void generatedCandidateComparesCleanlyAgainstTheAcceptedBase() throws Exception {
		assertThat(generate().code()).isZero();
		Path comparison = temp.resolve("comparison.json");
		var result = ContentUpdateComparison.compare(
			new ContentUpdateCli.Paths(active.root, output, source, comparison));
		assertThat(result.conflicts()).isEmpty();
		assertThat(result.report()).contains("addition demo.stage-01", "fase-01/codigo/hola/.gitignore");
	}

	@Test void aDeclaredModuleWithoutItsFileIsReportedWithoutWriting() throws Exception {
		// curso.json, not the index, says which modules exist: one that is declared available needs its file.
		files.remove("fase-01/02-siguiente.md");
		String changed = commit(files, "missing module\n");
		Map<String, String> activeBefore = snapshot(active.root);
		Map<String, String> sourceBefore = snapshot(source);
		assertThat(generate("--commit", changed).code()).isEqualTo(3);
		assertThat(findings()).contains("source body unavailable fase-01/02-siguiente.md");
		assertThat(Files.exists(output)).isFalse();
		assertThat(JSON.readTree(Files.readString(report)).path("written").asBoolean()).isFalse();
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	@Test void onlyDottedIdsInTheCourseNamespaceAreAccepted() throws Exception {
		// FORMATO.md 3.3: <course>.<name>; the <course>-<name> form of the old importer is gone (H7, T2).
		module("demo.01.02").put("id", "demo-01.02");
		((ObjectNode) module("demo.01.01").withArray("ejercicios").get(0)).put("id", "demo.01.01-e01");
		String changed = commit(files, "hyphen ids\n");
		assertThat(generate("--commit", changed).code()).isEqualTo(3);
		assertThat(findings()).contains(
			"curso.json: modulo id demo-01.02 does not start with demo.",
			"curso.json: ejercicio demo.01.01-e01 does not belong to demo.01.01");
		assertThat(Files.exists(output)).isFalse();
	}

	@Test void aRetitleInCursoJsonKeepsTheIdAndIsLeftToTheComparison() throws Exception {
		// The title comes from curso.json and the ID does not change with it (FORMATO.md 3.3).
		module("demo.01.02").put("titulo", "Otro título");
		String changed = commit(files, "retitle\n");
		assertThat(generate("--commit", changed).code()).isZero();
		JsonNode lesson = manifest().path("lessons").get(1);
		assertThat(lesson.path("id").asString()).isEqualTo("demo.01.02");
		assertThat(lesson.path("title").asString()).isEqualTo("Otro título");
		// The comparison shows it as a change to review, not a conflict (FORMATO.md 11.2).
		var result = ContentUpdateComparison.compare(
			new ContentUpdateCli.Paths(active.root, output, source, temp.resolve("comparison.json")));
		assertThat(result.conflicts()).isEmpty();
		assertThat(result.report()).contains("retitle demo.01.02: «Siguiente» -> «Otro título»");
	}

	@Test void fragmentsFollowTheSharedHeadingRule() throws Exception {
		assertThat(generate().code()).isZero();
		JsonNode manifest = manifest();
		// The pending target keeps no fragment: the existing lesson route is offered instead of a guessed anchor.
		assertThat(fragments(manifest, "fase-02/01-pendiente.md")).isEmpty();
		assertThat(fragments(manifest, "fase-01/01-intro.md")).containsExactly(
			Map.entry("ejercicio-2--segundo-ejercicio", "ejercicio-2--segundo-ejercicio"),
			Map.entry("qué-aprenderás", "qué-aprenderás"),
			Map.entry("repetida", "repetida"),
			Map.entry("repetida-1", "repetida-1"));
		assertThat(fragments(manifest, "fase-01/02-siguiente.md")).containsExactly(Map.entry("sección-b", "sección-b"));
		assertThat(fragments(manifest, "00-indice.md")).isEmpty();
		// Exercise association is the shared exact-title rule of LessonPipeline, not a second parser.
		CourseContent loaded = ContentLoader.loadAll(output).getFirst();
		assertThat(loaded.lessons().get("demo.01.01").exercises())
			.extracting(ExerciseContent::id, ExerciseContent::title)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("demo.01.01.e01", "Primer ejercicio"),
				org.assertj.core.groups.Tuple.tuple("demo.01.01.e02", "Segundo ejercicio"));
	}

	@Test void stageDocumentsExposeTheirOwnAndCrossStageFragments() throws Exception {
		// A stage links to its own headings and to another stage exactly as a lesson does: the shared heading rule
		// names every anchor, so no fragment has to be guessed and none of them stays unresolved.
		files.put("proyecto/01-inicial.md", """
			# Etapa 1

			## El código

			Contenido con [su propia sección](#el-código) y [el arranque](#paso-5-el-arranque-y-los-datos-de-la-v10).

			## Decisiones de diseño

			Y con [la etapa siguiente](02-siguiente.md#qué-hace-la-v2).

			## Paso 5: el arranque y los datos de la v1.0
			""".getBytes(StandardCharsets.UTF_8));
		files.put("proyecto/02-siguiente.md", """
			# Etapa 2

			## Qué hace la v2

			Más contenido.
			""".getBytes(StandardCharsets.UTF_8));
		// A lesson that becomes available in the pinned tree may point at a stage fragment too: the same mapping
		// answers both directions, and the accepted copy holds no body for it, so nothing is an adaptation.
		module("demo.02.01").put("disponible", true);
		files.put("fase-02/01-pendiente.md", """
			# 02.01 Pendiente

			## Sección nueva

			Ver [las decisiones](../proyecto/01-inicial.md#decisiones-de-diseño).
			""".getBytes(StandardCharsets.UTF_8));
		// The accepted intro already points at that pending lesson, so its fragment must now name a real heading.
		active.replace("demo/lessons/demo.01.01.md", "#algo-que-no-existe", "#sección-nueva");
		files.put("fase-01/01-intro.md", Files.readAllBytes(active.root.resolve("demo/lessons/demo.01.01.md")));
		String changed = commit(files, "stage fragments\n");
		assertThat(generate("--commit", changed))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 4\n", ""));
		JsonNode manifest = manifest();
		// Each document owns its own fragments: a cross-stage link registers its anchor in the target stage.
		assertThat(fragments(manifest, "proyecto/01-inicial.md")).containsOnly(
			Map.entry("decisiones-de-diseño", "decisiones-de-diseño"),
			Map.entry("el-código", "el-código"),
			Map.entry("paso-5-el-arranque-y-los-datos-de-la-v10", "paso-5-el-arranque-y-los-datos-de-la-v10"));
		assertThat(fragments(manifest, "proyecto/02-siguiente.md"))
			.containsOnly(Map.entry("qué-hace-la-v2", "qué-hace-la-v2"));
		CourseContent loaded = ContentLoader.loadAll(output).getFirst();
		assertThat(loaded.stages().get("demo.stage-01").bodyHtml()).contains(
			"<a href=\"#el-código\">su propia sección</a>",
			"<a href=\"#paso-5-el-arranque-y-los-datos-de-la-v10\">el arranque</a>",
			"<a href=\"/stages/demo.stage-02#qué-hace-la-v2\">la etapa siguiente</a>");
		assertThat(loaded.lessons().get("demo.02.01").bodyHtml())
			.contains("<a href=\"/stages/demo.stage-01#decisiones-de-diseño\">las decisiones</a>");
	}

	@Test void aStageFragmentWithoutAHeadingStillFails() throws Exception {
		// Registering stage fragments must not resolve a fragment the document does not have.
		files.put("proyecto/01-inicial.md", """
			# Etapa 1

			Contenido con [la etapa siguiente](02-siguiente.md#no-existe).
			""".getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "missing stage fragment\n");
		Map<String, String> activeBefore = snapshot(active.root);
		Map<String, String> sourceBefore = snapshot(source);
		assertThat(generate("--commit", changed)).isEqualTo(new Result(3, "Candidate not written; findings: 2\n", ""));
		assertThat(findings()).containsExactly(
			"demo.stage-01: unresolved link 02-siguiente.md#no-existe",
			"unsupported destination proyecto/01-inicial.md:3: 02-siguiente.md#no-existe (no heading «no-existe» in proyecto/02-siguiente.md)");
		assertThat(Files.exists(output)).isFalse();
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	@Test void exerciseHeadingsKeepTheirExactTitleAssociation() throws Exception {
		// Both copies must be valid on their own: the accepted lesson gains the exercise, the pinned tree retitles it.
		String exercise = """

			## 5. Ejercicios prácticos resueltos

			#### Ejercicio 1 · Título exacto

			Enunciado.

			<details>
			<summary>Ver solución</summary>

			Respuesta.

			</details>
			""";
		String acceptedBody = active.read("demo/lessons/demo.01.02.md") + exercise;
		active.write("demo/lessons/demo.01.02.md", acceptedBody);
		active.editManifest(manifest -> ((tools.jackson.databind.node.ArrayNode) ContentFixture.lesson(manifest, "demo.01.02")
			.path("exercises")).addObject().put("id", "demo.01.02.e01").put("lessonId", "demo.01.02")
			.put("title", "Título exacto"));
		files.put("fase-01/02-siguiente.md",
			acceptedBody.replace("Ejercicio 1 · Título exacto", "Ejercicio 1 · Título cambiado").getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "retitled exercise\n");
		// A retitled heading is an exercise curso.json does not declare: it is reported, never given a new identity,
		// and the local body keeps its own adaptation report.
		assertThat(generate("--commit", changed)).isEqualTo(new Result(3, "Candidate not written; findings: 2\n", ""));
		assertThat(findings()).containsExactly(
			"demo.01.02: exercise heading «Título cambiado» has no exercise with that title in the manifest",
			"local adaptation would be replaced demo/lessons/demo.01.02.md");
		assertThat(Files.exists(output)).isFalse();
	}

	@Test void solutionIsTheBalancedVerSolucionBlockAndKeepsWhatFollowsIt() throws Exception {
		// A top-level hint block comes first and the solution nests another block: the old first-open/first-close rule
		// ended the solution at the nested close and then reported the remaining content as «content after its solution».
		String body = """
			# 01.02 Siguiente

			## Sección B

			Texto B.

			## 5. Ejercicios prácticos resueltos

			#### Ejercicio 1 · Detalles anidados

			Enunciado con [la teoría](01-intro.md#qué-aprenderás).

			<details>
			<summary>Una pista</summary>

			Pista inicial.

			</details>

			<details>
			<summary>Ver solución</summary>

			Solución con un bloque anidado.

			<details>
			<summary>Ampliar</summary>

			Más detalle de la solución.

			</details>

			Cierre de la solución.

			</details>

			Nota que sigue a la solución.
			""";
		accept("demo.01.02", "fase-01/02-siguiente.md", body, "Detalles anidados");
		String changed = commit(files, "nested solution\n");
		assertThat(generate("--commit", changed))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 4\n", ""));
		ExerciseContent exercise = ContentLoader.loadAll(output).getFirst().lessons().get("demo.01.02").exercises()
			.getFirst();
		// The statement keeps its own hint block; only the solution leaves it.
		assertThat(exercise.statementHtml()).contains("Pista inicial").doesNotContain("Solución con", "Ampliar");
		assertThat(exercise.solutionHtml()).contains("<summary>Ver solución</summary>", "Solución con un bloque anidado",
			"Más detalle de la solución", "Cierre de la solución", "Nota que sigue a la solución");
		assertThat(ContentLoader.loadAll(output).getFirst().lessons().get("demo.01.02").bodyHtml())
			.doesNotContain("Solución con", "Pista inicial");
	}

	@Test void exercisesSectionLeadInStaysInTheLessonBody() throws Exception {
		String body = """
			# 01.02 Siguiente

			## Sección B

			Vuelve a [la introducción](01-intro.md#qué-aprenderás).

			## 5. Ejercicios prácticos resueltos

			Antes de empezar, repasa [la teoría](01-intro.md#qué-aprenderás).

			#### Ejercicio 1 · Con lead-in

			Enunciado.

			<details>
			<summary>Ver solución</summary>

			Solución.

			</details>
			""";
		accept("demo.01.02", "fase-01/02-siguiente.md", body, "Con lead-in");
		String changed = commit(files, "lead-in\n");
		assertThat(generate("--commit", changed))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 4\n", ""));
		LessonContent lesson = ContentLoader.loadAll(output).getFirst().lessons().get("demo.01.02");
		assertThat(lesson.bodyHtml()).contains("Antes de empezar, repasa",
			"<a href=\"/lessons/demo.01.01#qué-aprenderás\">la teoría</a>");
		assertThat(lesson.exercises().getFirst().statementHtml()).doesNotContain("Antes de empezar");
	}

	@Test void fragmentsOnTheIndexAndTheExtractedSectionKeepTheEntryOnly() throws Exception {
		String body = """
			# 01.02 Siguiente

			## Sección B

			Índice con [la sección del proyecto](../00-indice.md#proyecto-evolutivo) y
			[los ejercicios](#5-ejercicios-prácticos-resueltos).

			## 5. Ejercicios prácticos resueltos

			#### Ejercicio 1 · Fragmento extraído

			Enunciado.

			<details>
			<summary>Ver solución</summary>

			Solución.

			</details>
			""";
		accept("demo.01.02", "fase-01/02-siguiente.md", body, "Fragmento extraído");
		active.editManifest(manifest -> ((tools.jackson.databind.node.ObjectNode)
			ContentFixture.link(manifest, "fase-01/02-siguiente.md").get("fragments"))
			.put("5-ejercicios-prácticos-resueltos", "5-ejercicios-prácticos-resueltos"));
		String changed = commit(files, "entry fragments\n");
		assertThat(generate("--commit", changed))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 4\n", ""));
		assertThat(ContentLoader.loadAll(output).getFirst().lessons().get("demo.01.02").bodyHtml())
			.contains("<a href=\"/courses/demo\">la sección del proyecto</a>",
				"<a href=\"/lessons/demo.01.02\">los ejercicios</a>");
		assertThat(fragments(manifest(), "00-indice.md")).isEmpty();
	}

	@Test void inertSanitizerRemovalsAreReportedWithoutBlocking() throws Exception {
		files.put("proyecto/01-inicial.md",
			"# Etapa 1\n\nContenido. <span>Nota</span> con <u>subrayado</u>.\n".getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "inert formatting\n");
		assertThat(generate("--commit", changed)).isEqualTo(new Result(0,
			"Candidate written; lessons: 3; stages: 2; resources: 4; reported: 2\n", ""));
		JsonNode data = JSON.readTree(Files.readString(report));
		assertThat(data.path("findings")).isEmpty();
		assertThat(reported()).containsExactly("proyecto/01-inicial.md: sanitizer removed <span>",
			"proyecto/01-inicial.md: sanitizer removed <u>");
		assertThat(data.path("written").asBoolean()).isTrue();
		String html = ContentLoader.loadAll(output).getFirst().stages().get("demo.stage-01").bodyHtml();
		assertThat(html).contains("Nota con subrayado").doesNotContain("<span", "<u>");
	}

	@Test void aRemovedAnchorIsReportedWhileOtherSanitizerRemovalsStillBlock() throws Exception {
		// A raw-HTML anchor is never rewritten, so its href is not an allowed href. The element and its href leave
		// together: the reader gains no link capability and its text stays, so the pair is reported, not blocking.
		String header = "# Etapa 1\n\nContenido.\n\n";
		files.put("proyecto/01-inicial.md",
			(header + "<a href=\"https://ejemplo.invalid/\">texto del enlace</a>\n").getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "raw anchor\n");
		assertThat(generate("--commit", changed)).isEqualTo(new Result(0,
			"Candidate written; lessons: 3; stages: 2; resources: 4; reported: 2\n", ""));
		assertThat(findings()).isEmpty();
		assertThat(reported()).containsExactly("proyecto/01-inicial.md: sanitizer removed <a>",
			"proyecto/01-inicial.md: sanitizer removed attribute [href] on <a>");
		assertThat(ContentLoader.loadAll(output).getFirst().stages().get("demo.stage-01").bodyHtml())
			.contains("texto del enlace").doesNotContain("<a", "href", "ejemplo.invalid");
		// An anchor that also carries an event handler keeps failing: only the href of an anchor is inert.
		assertBlocked("<a href=\"#inicio\" onclick=\"x()\">x</a>", 1, header,
			"demo.stage-01: sanitizer removed attribute [href, onclick] on <a>");
	}

	@Test void scriptsFramesHandlersRawImagesAndUnknownIdsKeepBlocking() throws Exception {
		String header = "# Etapa 1\n\nContenido.\n\n";
		assertBlocked("<script>alert(1)</script>", 1, header, "demo.stage-01: sanitizer removed <script>");
		assertBlocked("<iframe src=\"https://evil.example/\"></iframe>", 1, header,
			"demo.stage-01: sanitizer removed <iframe>");
		assertBlocked("<p onclick=\"x()\">x</p>", 1, header,
			"demo.stage-01: sanitizer removed attribute [onclick] on <p>");
		assertBlocked("<h2 id=\"evil\">t</h2>", 1, header,
			"demo.stage-01: sanitizer removed attribute [id] on <h2>");
		assertBlocked("<img src=\"x\" onerror=\"x()\">", 2, header, "demo.stage-01: raw HTML images are not allowed",
			"demo.stage-01: sanitizer removed <img>");
	}

	@Test void aPathClaimedByALessonAndAStageYieldsOneResourcePerOwner() throws Exception {
		files.put("proyecto/01-inicial.md", ("# Etapa 1\n\nContenido con "
			+ "[el código compartido](../fase-01/codigo/hola/).\n").getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "shared ownership\n");
		assertThat(generate("--commit", changed))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 7\n", ""));
		JsonNode manifest = manifest();
		List<JsonNode> shared = new ArrayList<>();
		for (JsonNode resource : manifest.path("resources")) {
			if ("fase-01/codigo/hola/Hello.java".equals(resource.path("sourcePath").asString())) shared.add(resource);
		}
		assertThat(shared).hasSize(2);
		assertThat(shared).extracting(resource -> resource.path("resourceId").asString()).doesNotHaveDuplicates();
		assertThat(shared).extracting(resource -> resource.path("localPath").asString()).doesNotHaveDuplicates();
		assertThat(shared.stream().filter(resource -> !resource.path("lessonId").isNull()).count()).isEqualTo(1);
		assertThat(shared.stream().filter(resource -> "demo.01.01".equals(resource.path("lessonId").asString()))
			.count()).isEqualTo(1);
		assertThat(shared.stream().filter(resource -> "demo.stage-01".equals(resource.path("stageId").asString()))
			.count()).isEqualTo(1);
		byte[] accepted = Files.readAllBytes(active.root.resolve("demo/resources/demo.01.01-r-hello.java"));
		for (JsonNode resource : shared) {
			assertThat(Files.readAllBytes(output.resolve(resource.path("localPath").asString()))).isEqualTo(accepted);
		}
		// One source path keeps one mapping: the sorted-first owner answers links to the shared file.
		assertThat(link(manifest, "fase-01/codigo/hola/Hello.java").path("resourceId").asString())
			.isEqualTo(shared.get(0).path("resourceId").asString());
		JsonNode folder = link(manifest, "fase-01/codigo/hola/");
		assertThat(folder.path("resourceIds")).hasSize(6);
		JsonNode archive = resource(manifest, "fase-01/codigo/hola/");
		assertThat(archive.path("resourceId").asString()).isEqualTo(folder.path("zipResourceId").asString());
		assertThat(archive.path("lessonId").asString()).isEqualTo("demo.01.01");
		assertThat(archive.path("stageId").isNull()).isTrue();
		// The archive belongs to the sorted-first owner, so it holds that owner's copy of every shared path.
		assertThat(archive.path("memberResourceIds")).hasSize(3);
		assertThat(folder.path("resourceIds")).containsAll(archive.path("memberResourceIds"));
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
	}

	/**
	 * The real shape of the shared exercise: the lesson claims the nested packages and the stage claims the exercise
	 * root that contains them, so each folder must list every file its owners claim beneath it.
	 */
	String sharedTree() throws Exception {
		// The accepted copy already holds the nested packages the lesson links, exactly as the imported lesson does,
		// so it loads on its own before the stage claims the exercise root that contains them.
		byte[] main = "public class Main {}\n".getBytes(StandardCharsets.UTF_8);
		byte[] book = "public class Book {}\n".getBytes(StandardCharsets.UTF_8);
		byte[] loan = "public class Loan {}\n".getBytes(StandardCharsets.UTF_8);
		active.addResource("demo.01.01-r-main", "fase-01/codigo/hola/src/app/Main.java", "text/plain; charset=utf-8",
			main);
		active.addResource("demo.01.01-r-book", "fase-01/codigo/hola/src/model/Book.java", "text/plain; charset=utf-8",
			book);
		active.addResource("demo.01.01-r-loan", "fase-01/codigo/hola/src/model/Loan.java", "text/plain; charset=utf-8",
			loan);
		String intro = active.read("demo/lessons/demo.01.01.md").replace(
			"📁 **Código:** [`codigo/hola/`](codigo/hola/)",
			"📁 **Código:** [`codigo/hola/src/app/`](codigo/hola/src/app/), "
				+ "[`codigo/hola/src/model/`](codigo/hola/src/model/)");
		active.write("demo/lessons/demo.01.01.md", intro);
		active.editManifest(manifest -> {
			// The accepted folder of the exercise lists every file once, as its own strict rule requires.
			((ArrayNode) ContentFixture.link(manifest, "fase-01/codigo/hola/").get("resourceIds"))
				.add("demo.01.01-r-main").add("demo.01.01-r-book").add("demo.01.01-r-loan");
			folder(manifest, "fase-01/codigo/hola/src/app/", "demo.01.01-r-main");
			folder(manifest, "fase-01/codigo/hola/src/model/", "demo.01.01-r-book", "demo.01.01-r-loan");
		});
		files.put("fase-01/01-intro.md", intro.getBytes(StandardCharsets.UTF_8));
		files.put("fase-01/codigo/hola/src/app/Main.java", main);
		files.put("fase-01/codigo/hola/src/model/Book.java", book);
		files.put("fase-01/codigo/hola/src/model/Loan.java", loan);
		files.put("proyecto/01-inicial.md", ("# Etapa 1\n\nEl ejercicio completo, en "
			+ "[mi biblioteca](../fase-01/codigo/hola/).\n").getBytes(StandardCharsets.UTF_8));
		return commit(files, "shared nested tree\n");
	}

	@Test void aSharedFolderListsTheFilesOfEveryOwnerBeneathIt() throws Exception {
		String changed = sharedTree();
		Map<String, String> activeBefore = snapshot(active.root);
		Map<String, String> sourceBefore = snapshot(source);
		Result result = generate("--commit", changed);
		// The finished candidate must be accepted by the pure runtime loader: a folder entry that lists only the
		// owner who linked it leaves the nested shared tree rejected.
		assertThat(result.err()).as("loader problems").isEmpty();
		assertThat(result)
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 12\n", ""));
		JsonNode manifest = manifest();
		// Every folder lists exactly the files claimed beneath it, each one owned by an owner that claims that folder.
		Map<String, Integer> listed = new TreeMap<>();
		for (String folder : List.of("fase-01/codigo/hola/", "fase-01/codigo/hola/src/app/",
			"fase-01/codigo/hola/src/model/", "fase-01/codigo/solo/")) {
			JsonNode entry = link(manifest, folder);
			assertThat(entry.path("resourceIds")).hasSize(new HashSet<>(ids(entry.path("resourceIds"))).size());
			Set<String> owners = new HashSet<>();
			int beneath = 0;
			for (JsonNode id : entry.path("resourceIds")) {
				JsonNode member = resourceById(manifest, id.asString());
				assertThat(member.path("kind").asString()).isEqualTo("file");
				assertThat(member.path("sourcePath").asString()).startsWith(folder);
				owners.add(member.path("lessonId").isNull() ? member.path("stageId").asString()
					: member.path("lessonId").asString());
				beneath++;
			}
			// At most one lesson and one stage claim a folder: only that dual ownership is approved.
			assertThat(owners).isSubsetOf("demo.01.01", "demo.stage-01");
			listed.put(folder, beneath);
		}
		assertThat(listed).containsOnly(
			Map.entry("fase-01/codigo/hola/", 11),
			Map.entry("fase-01/codigo/hola/src/app/", 2),
			Map.entry("fase-01/codigo/hola/src/model/", 4),
			Map.entry("fase-01/codigo/solo/", 1));
		// One resource per (path, owner), with identifiers and local paths that stay distinct per owner.
		Map<String, Integer> perPath = new TreeMap<>();
		for (JsonNode resource : manifest.path("resources")) {
			if (!"file".equals(resource.path("kind").asString())) continue;
			perPath.merge(resource.path("sourcePath").asString(), 1, Integer::sum);
		}
		assertThat(perPath).containsOnly(
			Map.entry("fase-01/codigo/hola/Hello.java", 2),
			Map.entry("fase-01/codigo/hola/Other.java", 2),
			Map.entry("fase-01/codigo/hola/.gitignore", 1),
			Map.entry("fase-01/codigo/hola/src/app/Main.java", 2),
			Map.entry("fase-01/codigo/hola/src/model/Book.java", 2),
			Map.entry("fase-01/codigo/hola/src/model/Loan.java", 2),
			Map.entry("fase-01/codigo/solo/Solo.java", 1));
		assertThat(manifest.path("resources")).extracting(resource -> resource.path("resourceId").asString())
			.doesNotHaveDuplicates();
		assertThat(manifest.path("resources")).extracting(resource -> resource.path("localPath").asString())
			.doesNotHaveDuplicates();
		// Exactly one archive per folder. The root is claimed by the stage, so its archive is the stage's complete copy;
		// the nested folder claimed by the lesson keeps the lesson's own copies.
		Map<String, Integer> archives = new TreeMap<>();
		for (JsonNode resource : manifest.path("resources")) {
			if ("zip".equals(resource.path("kind").asString())) {
				archives.merge(resource.path("sourcePath").asString(), 1, Integer::sum);
			}
		}
		assertThat(archives).containsOnly(
			Map.entry("fase-01/codigo/hola/", 1),
			Map.entry("fase-01/codigo/hola/src/app/", 1),
			Map.entry("fase-01/codigo/hola/src/model/", 1),
			Map.entry("fase-01/codigo/solo/", 1));
		JsonNode rootFolder = link(manifest, "fase-01/codigo/hola/");
		JsonNode rootArchive = resource(manifest, "fase-01/codigo/hola/");
		assertThat(rootArchive.path("stageId").asString()).isEqualTo("demo.stage-01");
		List<String> rootMembers = ids(rootArchive.path("memberResourceIds"));
		assertThat(rootMembers).hasSize(6);
		for (String id : rootMembers) {
			assertThat(ids(rootFolder.path("resourceIds"))).contains(id);
			assertThat(resourceById(manifest, id).path("stageId").asString()).isEqualTo("demo.stage-01");
			assertThat(resourceById(manifest, id).path("sourcePath").asString()).startsWith("fase-01/codigo/hola/");
		}
		JsonNode nestedArchive = resource(manifest, "fase-01/codigo/hola/src/model/");
		assertThat(nestedArchive.path("lessonId").asString()).isEqualTo("demo.01.01");
		assertThat(ids(nestedArchive.path("memberResourceIds"))).hasSize(2).allSatisfy(id -> assertThat(
			resourceById(manifest, id).path("sourcePath").asString())
			.startsWith("fase-01/codigo/hola/src/model/"));
		CourseContent loaded = ContentLoader.loadAll(output).getFirst();
		assertThat(loaded.stages().get("demo.stage-01").bodyHtml())
			.contains("/api/resources/" + rootArchive.path("resourceId").asString());
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	@Test void aSingleOwnerFolderKeepsListingExactlyItsOwnFile() throws Exception {
		assertThat(generate("--commit", sharedTree()))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 12\n", ""));
		JsonNode manifest = manifest();
		JsonNode folder = link(manifest, "fase-01/codigo/solo/");
		assertThat(ids(folder.path("resourceIds"))).containsExactly("demo.01.01-r-solo");
		JsonNode archive = resource(manifest, "fase-01/codigo/solo/");
		assertThat(archive.path("lessonId").asString()).isEqualTo("demo.01.01");
		assertThat(archive.path("resourceId").asString()).isEqualTo(folder.path("zipResourceId").asString());
		assertThat(ids(archive.path("memberResourceIds"))).containsExactly("demo.01.01-r-solo");
	}

	/**
	 * The whole optimisation must not move one byte: the same multi-file, multi-folder, shared-owner generation keeps
	 * the recorded manifest bytes and the recorded canonical ZIP bytes. The values were recorded before the batched
	 * source reader and the single-pass folder membership existed.
	 */
	@Test void multiFileMultiFolderCandidateKeepsItsRecordedBytes() throws Exception {
		assertThat(generate("--commit", sharedTree()))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 12\n", ""));
		assertThat(sha(Files.readAllBytes(output.resolve("demo/manifest.json")))).isEqualTo(SHARED_MANIFEST_SHA);
		List<String> archives = new ArrayList<>();
		for (JsonNode generated : manifest().path("provenance").path("generated")) {
			archives.add(sha(Files.readAllBytes(output.resolve(generated.path("localPath").asString()))));
		}
		assertThat(archives).as("canonical ZIP bytes").hasSize(SHARED_ARCHIVE_SHA.size())
			.containsExactlyElementsOf(SHARED_ARCHIVE_SHA);
	}

	/**
	 * The committed blobs of one generation are read through a single long-lived `git cat-file --batch` process: no Git
	 * process per source path. The shim only records the command lines Git would run and then execs the real Git.
	 */
	@Test void sourceBlobsAreReadThroughOneBatchObjectStream() throws Exception {
		files.put("fase-01/codigo/hola/Uno.java", "class Uno {}\n".getBytes(StandardCharsets.UTF_8));
		files.put("fase-01/codigo/hola/Dos.java", "class Dos {}\n".getBytes(StandardCharsets.UTF_8));
		files.put("fase-01/codigo/hola/src/Tres.java", "class Tres {}\n".getBytes(StandardCharsets.UTF_8));
		files.put("proyecto/01-inicial.md", ("# Etapa 1\n\nContenido con "
			+ "[los archivos](../fase-01/codigo/hola/).\n").getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "many blobs\n");
		Path shim = Files.createDirectory(temp.resolve("bin"));
		Path log = temp.resolve("git-calls.log");
		Files.writeString(shim.resolve("git"), "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$CONTENT_IMPORT_GIT_LOG\"\nexec "
			+ realGit() + " \"$@\"\n");
		// A process search skips a file it cannot execute, so the fixture Git must be runnable.
		Files.setPosixFilePermissions(shim.resolve("git"), PosixFilePermissions.fromString("rwxr-xr-x"));
		Map<String, String> env = Map.of("PATH", shim + ":" + System.getenv("PATH"),
			// The shared Git runner drops every GIT_* override, so the shim log variable is named apart.
			"CONTENT_IMPORT_GIT_LOG", log.toString());
		Result result = cli(env, generateArguments("--commit", changed));
		assertThat(result.err()).as("shimmed generation").isEmpty();
		assertThat(result.code()).isZero();
		assertThat(Files.exists(output.resolve("demo/manifest.json"))).isTrue();
		List<String> calls = Files.readAllLines(log);
		assertThat(calls).filteredOn(call -> call.contains("cat-file blob"))
			.as("one Git process per source path").isEmpty();
		assertThat(calls).filteredOn(call -> call.contains("cat-file --batch"))
			.as("batched object reads").hasSize(1);
		assertThat(calls).filteredOn(call -> call.contains("ls-tree")).hasSize(1);
	}

	/**
	 * One generation writes several folder archives, and the whole manifest is validated once for all of them instead of
	 * once per archive: the count of full-manifest scans is the deterministic measure, not wall clock.
	 */
	@Test void folderArchivesAreValidatedOncePerGeneration() throws Exception {
		assertThat(generate("--commit", sharedTree()).code()).isZero();
		Manifest generated = ContentLoader.loadAll(output).getFirst().manifest();
		List<String> archives = new ArrayList<>();
		for (Manifest.Resource resource : generated.resources()) {
			if ("zip".equals(resource.kind())) archives.add(resource.resourceId());
		}
		assertThat(archives).as("folder archives of this generation").hasSize(4);
		int[] single = {0};
		written(counted(generated, single), List.of(archives.getFirst()));
		int[] perArchive = {0};
		Map<String, byte[]> each = written(counted(generated, perArchive), archives);
		int[] once = {0};
		Map<String, byte[]> together = writtenOnce(counted(generated, once), archives);
		assertThat(single[0]).as("scans of one validated archive write").isPositive();
		assertThat(perArchive[0]).as("scans when every archive validates the manifest")
			.isEqualTo(archives.size() * single[0]);
		assertThat(once[0]).as("scans of the validated-once generation").isEqualTo(single[0]);
		// The optimisation never changes a byte: the same canonical archives from both entry points.
		assertThat(together.keySet()).as("archives written").isEqualTo(each.keySet());
		for (Map.Entry<String, byte[]> archive : each.entrySet()) {
			assertThat(together.get(archive.getKey())).as(archive.getKey()).isEqualTo(archive.getValue());
		}
	}

	/**
	 * Validating once per generation must not weaken anything: an invalid manifest is refused before any archive is
	 * opened, including one whose folder entries do not list exactly the files claimed beneath them.
	 */
	@Test void validatedOnceArchivesStillRefuseAnInvalidManifest() throws Exception {
		assertThat(generate("--commit", sharedTree()).code()).isZero();
		List<String> archives = new ArrayList<>();
		for (Manifest.Resource resource : ContentLoader.loadAll(output).getFirst().manifest().resources()) {
			if ("zip".equals(resource.kind())) archives.add(resource.resourceId());
		}
		ObjectNode omitted = (ObjectNode) manifest();
		ArrayNode listed = (ArrayNode) link(omitted, "fase-01/codigo/hola/").get("resourceIds");
		listed.remove(listed.size() - 1);
		assertRefusedBeforeWriting(JSON.treeToValue(omitted, Manifest.class), archives,
			// The omitted file breaks the folder entry and, with it, the archive that decorates that folder.
			"ZIP must decorate the folder that lists its members: demo.stage-01-zip-hola-d34d1a2a",
			"folder fase-01/codigo/hola/ does not list exactly its resources",
			"folder ZIP members must be a subset of its folder files: fase-01/codigo/hola/");
		// A folder that lists a file nobody claims beneath it is refused by the same single validation.
		ObjectNode unclaimed = (ObjectNode) manifest();
		((ArrayNode) link(unclaimed, "fase-01/codigo/hola/").get("resourceIds")).add("demo.01.01-r-solo");
		assertRefusedBeforeWriting(JSON.treeToValue(unclaimed, Manifest.class), archives,
			"folder fase-01/codigo/hola/ does not list exactly its resources");
	}

	/** A folder archive is still refused when the manifest is not valid, and no target is opened for it. */
	void assertRefusedBeforeWriting(Manifest invalid, List<String> archives, String... problems) {
		List<ByteArrayOutputStream> opened = new ArrayList<>();
		ContentValidationException refused = assertThrows(ContentValidationException.class,
			() -> FolderZip.writeAll(invalid, output, archives, localPath -> {
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				opened.add(stream);
				return stream;
			}));
		assertThat(refused.problems()).containsExactly(problems);
		assertThat(opened).as("archives opened before the refusal").isEmpty();
	}

	/** The canonical bytes of the named archives, through the per-call validated entry point, keyed by local path. */
	Map<String, byte[]> written(Manifest manifest, List<String> archives) throws Exception {
		Map<String, byte[]> written = new TreeMap<>();
		for (String id : archives) {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			FolderZip.write(manifest, this.output, id, output);
			written.put(localPath(manifest, id), output.toByteArray());
		}
		return written;
	}

	static String localPath(Manifest manifest, String resourceId) {
		return manifest.resources().stream().filter(resource -> resource.resourceId().equals(resourceId))
			.findFirst().orElseThrow(() -> new IllegalArgumentException(resourceId)).localPath();
	}

	/** The canonical bytes of the same archives through the validated-once entry point. */
	Map<String, byte[]> writtenOnce(Manifest manifest, List<String> archives) throws Exception {
		Map<String, ByteArrayOutputStream> opened = new TreeMap<>();
		FolderZip.writeAll(manifest, output, archives, localPath -> {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			opened.put(localPath, stream);
			return stream;
		});
		Map<String, byte[]> written = new TreeMap<>();
		for (Map.Entry<String, ByteArrayOutputStream> entry : opened.entrySet()) {
			written.put(entry.getKey(), entry.getValue().toByteArray());
		}
		return written;
	}

	/**
	 * A manifest whose `sourceToId` list counts every full scan of it: validating the manifest once costs one scan, and
	 * the per-archive entry point costs one scan per archive.
	 */
	static Manifest counted(Manifest manifest, int[] scans) {
		List<Manifest.SourceLink> links = manifest.sourceToId();
		List<Manifest.SourceLink> counting = new AbstractList<>() {
			@Override public Manifest.SourceLink get(int index) { return links.get(index); }

			@Override public int size() { return links.size(); }

			@Override public Iterator<Manifest.SourceLink> iterator() { scans[0]++; return links.iterator(); }

			@Override public Spliterator<Manifest.SourceLink> spliterator() { scans[0]++; return links.spliterator(); }

			@Override public Stream<Manifest.SourceLink> stream() { scans[0]++; return links.stream(); }
		};
		return new Manifest(manifest.schemaVersion(), manifest.source(), manifest.course(), manifest.phases(),
			manifest.lessons(), manifest.resources(), counting, manifest.projectStages(), manifest.provenance());
	}

	/** The real Git binary, resolved before the shim is placed first on `PATH`. */
	static String realGit() throws Exception {
		Process process = new ProcessBuilder("sh", "-c", "command -v git").start();
		String path = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
		assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
		assertThat(path).isNotEmpty();
		return path;
	}

	/** A shared folder that omits a claimed file must still be rejected by the pure loader. */
	@Test void aSharedFolderThatOmitsAClaimedFileIsStillRejected() throws Exception {
		assertThat(generate("--commit", sharedTree()).code()).isZero();
		ObjectNode generated = (ObjectNode) manifest();
		ArrayNode listed = (ArrayNode) link(generated, "fase-01/codigo/hola/").get("resourceIds");
		// Omit one file of the lesson, which is not a member of the stage's archive: only the folder rule breaks.
		List<String> kept = new ArrayList<>();
		String omitted = null;
		for (JsonNode id : listed) {
			if (omitted == null && "demo.01.01".equals(resourceById(generated, id.asString()).path("lessonId").asString())) {
				omitted = id.asString();
				continue;
			}
			kept.add(id.asString());
		}
		assertThat(omitted).as("one listed file of the lesson").isNotNull();
		listed.removeAll();
		kept.forEach(listed::add);
		rewrite(generated);
		assertThat(assertThrows(ContentValidationException.class, () -> ContentLoader.loadAll(output)).problems())
			.containsExactly("folder fase-01/codigo/hola/ does not list exactly its resources");
	}

	/** A shared folder that lists a file nobody claims beneath it must still be rejected by the pure loader. */
	@Test void aSharedFolderThatListsAFileNobodyClaimsIsStillRejected() throws Exception {
		assertThat(generate("--commit", sharedTree()).code()).isZero();
		ObjectNode generated = (ObjectNode) manifest();
		// demo.01.01-r-solo is a registered file of another folder: it claims nothing beneath this one.
		((ArrayNode) link(generated, "fase-01/codigo/hola/").get("resourceIds")).add("demo.01.01-r-solo");
		rewrite(generated);
		assertThat(assertThrows(ContentValidationException.class, () -> ContentLoader.loadAll(output)).problems())
			.containsExactly("folder fase-01/codigo/hola/ does not list exactly its resources");
	}

	@Test void resourceIdentityInTheComparisonIsScopedToItsOwner() throws Exception {
		files.put("proyecto/01-inicial.md", ("# Etapa 1\n\nContenido con "
			+ "[el código compartido](../fase-01/codigo/hola/).\n").getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "shared comparison\n");
		assertThat(generate("--commit", changed).code()).isZero();
		Path comparison = temp.resolve("shared-comparison.json");
		var result = ContentUpdateComparison.compare(new ContentUpdateCli.Paths(active.root, output, source, comparison));
		assertThat(result.conflicts()).isEmpty();
		assertThat(result.report()).contains("addition demo.stage-01");
	}

	@Test void exerciseIdsComeOnlyFromCursoJsonAndAreNeverInvented() throws Exception {
		// The pending lesson becomes available in the pinned tree only, with two exercise headings: the accepted copy
		// holds no body for it, so nothing is an adaptation. Their IDs can only come from curso.json.
		module("demo.02.01").put("disponible", true);
		files.put("fase-02/01-pendiente.md", """
			# 02.01 Pendiente

			## Sección nueva

			Antes de empezar, [el primer ejercicio](#ejercicio-1--redacta-el-informe).

			## 5. Ejercicios prácticos resueltos

			#### Ejercicio 1 · Redacta el informe

			Enunciado uno.

			<details>
			<summary>Ver solución</summary>

			Solución uno.

			</details>

			#### Ejercicio 2 · Entrega el informe

			Enunciado dos.

			<details>
			<summary>Ver solución</summary>

			Solución dos.

			</details>
			""".getBytes(StandardCharsets.UTF_8));
		// The accepted intro already points at the new lesson, so its fragment must now name a real heading.
		active.replace("demo/lessons/demo.01.01.md", "#algo-que-no-existe", "#sección-nueva");
		files.put("fase-01/01-intro.md", Files.readAllBytes(active.root.resolve("demo/lessons/demo.01.01.md")));
		String undeclared = commit(files, "undeclared exercises\n");
		assertThat(generate("--commit", undeclared)).isEqualTo(new Result(3, "Candidate not written; findings: 3\n", ""));
		assertThat(findings()).containsExactly(
			"demo.02.01: exercise heading «Entrega el informe» has no exercise with that title in the manifest",
			"demo.02.01: exercise heading «Redacta el informe» has no exercise with that title in the manifest",
			"demo.02.01: unresolved link #ejercicio-1--redacta-el-informe");
		assertThat(Files.exists(output)).isFalse();
		Files.delete(report);
		// Once the course declares them, they keep exactly the declared IDs.
		tools.jackson.databind.node.ArrayNode declared = module("demo.02.01").putArray("ejercicios");
		declared.addObject().put("id", "demo.02.01.redacta").put("titulo", "Redacta el informe");
		declared.addObject().put("id", "demo.02.01.entrega").put("titulo", "Entrega el informe");
		String changed = commit(files, "new exercise\n");
		Result result = generate("--commit", changed);
		assertThat(findings()).as("findings of the real run").isEmpty();
		assertThat(result).isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 4\n", ""));
		JsonNode manifest = manifest();
		JsonNode generated = manifest.path("lessons").get(2).path("exercises");
		assertThat(generated).hasSize(2);
		assertThat(generated).extracting(node -> node.path("id").asString())
			.containsExactly("demo.02.01.redacta", "demo.02.01.entrega");
		assertThat(generated).extracting(node -> node.path("lessonId").asString())
			.containsOnly("demo.02.01");
		assertThat(generated).extracting(node -> node.path("title").asString())
			.containsExactly("Redacta el informe", "Entrega el informe");
		assertThat(manifest.path("lessons").get(0).path("exercises"))
			.extracting(node -> node.path("id").asString()).containsExactly("demo.01.01.e01", "demo.01.01.e02");
		assertThat(fragments(manifest, "fase-02/01-pendiente.md"))
			.containsEntry("ejercicio-1--redacta-el-informe", "ejercicio-1--redacta-el-informe");
		assertThat(ContentLoader.loadAll(output).getFirst().lessons().get("demo.02.01").bodyHtml())
			.contains("<a href=\"#demo.02.01.redacta\">el primer ejercicio</a>");
		// Rerun stability: the same pinned tree and accepted copy produce the same candidate.
		Path second = temp.resolve("candidate-rerun");
		Path secondReport = temp.resolve("report-rerun.json");
		assertThat(generate("--commit", changed, "--output", second.toString(), "--report", secondReport.toString()))
			.isEqualTo(new Result(0, "Candidate written; lessons: 3; stages: 2; resources: 4\n", ""));
		assertThat(Files.readAllBytes(second.resolve("demo/manifest.json")))
			.isEqualTo(Files.readAllBytes(output.resolve("demo/manifest.json")));
	}

	@Test void unsupportedDestinationsAreReportedWithPathAndLine() throws Exception {
		// Stages have no accepted copy yet, so the pinned tree is the only place this link can come from.
		String stage = """
			# Etapa 1

			Contenido de la etapa, ver [la introducción](../fase-01/01-intro.md#no-existe).
			""";
		files.put("proyecto/01-inicial.md", stage.getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "broken fragment\n");
		assertThat(generate("--commit", changed)).isEqualTo(new Result(3, "Candidate not written; findings: 2\n", ""));
		assertThat(findings()).containsExactly(
			"demo.stage-01: unresolved link ../fase-01/01-intro.md#no-existe",
			"unsupported destination proyecto/01-inicial.md:3: ../fase-01/01-intro.md#no-existe (no heading «no-existe» in fase-01/01-intro.md)");
		assertThat(Files.exists(output)).isFalse();
	}

	@Test void resourcesFoldersZipAndProvenanceAreRecorded() throws Exception {
		assertThat(generate().code()).isZero();
		JsonNode manifest = manifest();
		JsonNode folder = link(manifest, "fase-01/codigo/hola/");
		JsonNode members = folder.path("resourceIds");
		assertThat(members).hasSize(3);
		String zipId = folder.path("zipResourceId").asString();
		JsonNode archive = resource(manifest, "fase-01/codigo/hola/");
		assertThat(archive.path("kind").asString()).isEqualTo("zip");
		assertThat(archive.path("mediaType").asString()).isEqualTo("application/zip");
		assertThat(archive.path("fileName").asString()).isEqualTo("hola.zip");
		assertThat(archive.path("memberResourceIds")).isEqualTo(members);
		assertThat(archive.path("lessonId").asString()).isEqualTo("demo.01.01");
		assertThat(archive.path("resourceId").asString()).isEqualTo(zipId);
		// Every reachable member is copied, including dotfiles, and never silently skipped.
		String dotfileId = null;
		for (JsonNode id : members) {
			JsonNode member = resourceById(manifest, id.asString());
			if (member.path("sourcePath").asString().endsWith("/.gitignore")) dotfileId = id.asString();
		}
		assertThat(dotfileId).isNotNull();
		JsonNode dotfile = resourceById(manifest, dotfileId);
		assertThat(Files.readString(output.resolve(dotfile.path("localPath").asString()))).isEqualTo("*.class\n");
		JsonNode provenance = manifest.path("provenance");
		assertThat(provenance.path("files")).hasSize(8);
		for (JsonNode file : provenance.path("files")) {
			assertThat(file.path("sourceSha256").asString()).hasSize(64);
			assertThat(file.path("copySha256").asString()).isEqualTo(file.path("sourceSha256").asString());
			assertThat(file.path("transformation").isNull()).isTrue();
		}
		assertThat(provenance.path("generated")).hasSize(2);
		for (JsonNode generated : provenance.path("generated")) {
			byte[] bytes = Files.readAllBytes(output.resolve(generated.path("localPath").asString()));
			assertThat(generated.path("copySha256").asString()).isEqualTo(sha(bytes));
			assertThat(generated.path("inputResourceIds")).isNotEmpty();
		}
		// The runtime loader revalidates the canonical ZIP bytes and every registered copy.
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
	}

	@Test void aDirectLinkToAProtectedFileIsReportedAndKeepsOnlyItsText() throws Exception {
		// The linked file is excluded by the same policy as a folder member: reported, never read, and the run goes on.
		files.put("fase-01/codigo/hola/.env", "SECRETO_DIDACTICO=uno\n".getBytes(StandardCharsets.UTF_8));
		files.put("proyecto/01-inicial.md",
			"# Etapa 1\n\nCopia [la configuración](../fase-01/codigo/hola/.env) antes.\n".getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "protected direct link\n");
		Map<String, String> activeBefore = snapshot(active.root);
		Map<String, String> sourceBefore = snapshot(source);
		assertThat(generate("--commit", changed)).isEqualTo(new Result(0,
			"Candidate written; lessons: 3; stages: 2; resources: 4; reported: 1\n", ""));
		assertThat(findings()).isEmpty();
		assertThat(reported()).containsExactly("protected source path excluded by policy: fase-01/codigo/hola/.env");
		String html = ContentLoader.loadAll(output).getFirst().stages().get("demo.stage-01").bodyHtml();
		assertThat(html).contains("la configuración").doesNotContain(".env", "<a");
		try (var all = Files.walk(output)) {
			for (Path path : all.filter(Files::isRegularFile).toList()) {
				assertThat(new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1))
					.as(path.toString()).doesNotContain("SECRETO_DIDACTICO");
			}
		}
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	@Test void protectedSourcePathsAreExcludedAndReportedWithoutSerializingTheirBytes() throws Exception {
		// Policy: a protected source path is never read nor serialized. The folder ships without that example file and
		// the omission is reported with its source path; nothing is substituted for it and the run is not aborted.
		files.put("fase-01/codigo/hola/.env", "SECRETO_DIDACTICO=uno\n".getBytes(StandardCharsets.UTF_8));
		files.put("fase-01/codigo/hola/.env.example", "OTRO_SECRETO=dos\n".getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "protected members\n");
		Map<String, String> activeBefore = snapshot(active.root);
		Map<String, String> sourceBefore = snapshot(source);
		assertThat(generate("--commit", changed)).isEqualTo(new Result(0,
			"Candidate written; lessons: 3; stages: 2; resources: 4; reported: 2\n", ""));
		assertThat(findings()).isEmpty();
		assertThat(reported()).containsExactly(
			"protected source path excluded by policy: fase-01/codigo/hola/.env",
			"protected source path excluded by policy: fase-01/codigo/hola/.env.example");
		assertThat(JSON.readTree(Files.readString(report)).path("written").asBoolean()).isTrue();
		// Never serialized: no path, manifest entry or stored ZIP copy carries the protected name or its bytes.
		List<String> paths = new ArrayList<>();
		try (var all = Files.walk(output)) {
			for (Path path : all.sorted().toList()) {
				String relative = output.relativize(path).toString();
				paths.add(relative);
				if (Files.isRegularFile(path)) {
					assertThat(new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1))
						.as(relative).doesNotContain("SECRETO_DIDACTICO", "OTRO_SECRETO");
				}
			}
		}
		assertThat(paths).noneMatch(path -> path.contains(".env"));
		JsonNode manifest = manifest();
		assertThat(manifest.path("resources")).noneMatch(resource -> resource.path("sourcePath").asString().contains(".env"));
		assertThat(manifest.path("sourceToId")).noneMatch(entry -> entry.path("sourcePath").asString().contains(".env"));
		// The claimed folder keeps the three files it may copy, so the example file is absent, not replaced.
		assertThat(link(manifest, "fase-01/codigo/hola/").path("resourceIds")).hasSize(3);
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	@Test void resourceOwnershipStaysExactPerOwner() throws Exception {
		String body = """
			# 01.02 Siguiente

			## Sección A

			Texto A con [Solo.java](codigo/solo/Solo.java).

			## Sección B

			Vuelve a [la introducción](01-intro.md#qué-aprenderás).
			""";
		files.put("fase-01/02-siguiente.md", body.getBytes(StandardCharsets.UTF_8));
		active.write("demo/lessons/demo.01.02.md", body);
		String changed = commit(files, "shared file\n");
		assertThat(generate("--commit", changed)).isEqualTo(new Result(3, "Candidate not written; findings: 1\n", ""));
		assertThat(findings())
			.containsExactly("resource fase-01/codigo/solo/Solo.java is claimed by demo.01.01 and demo.01.02");
		assertThat(Files.exists(output)).isFalse();
	}

	@Test void localAdaptationsAreNeverReplaced() throws Exception {
		active.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Adaptado localmente.");
		Map<String, String> activeBefore = snapshot(active.root);
		Map<String, String> sourceBefore = snapshot(source);
		assertThat(generate()).isEqualTo(new Result(3, "Candidate not written; findings: 1\n", ""));
		assertThat(findings()).containsExactly("local adaptation would be replaced demo/lessons/demo.01.01.md");
		assertThat(Files.exists(output)).isFalse();
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	@Test void outputMustBeNewAndDisjoint() throws Exception {
		Files.createDirectory(output);
		Files.writeString(output.resolve("notes.txt"), "kept");
		Path link = temp.resolve("candidate-link");
		Files.createSymbolicLink(link, output);
		Map<String, String> before = snapshot(temp);
		assertThat(generate()).isEqualTo(new Result(2, "", "content-import: output must not exist\n"));
		assertThat(snapshot(temp)).isEqualTo(before);
		assertThat(generate("--output", active.root.resolve("nested").toString()))
			.isEqualTo(new Result(2, "", "content-import: paths must not overlap\n"));
		assertThat(generate("--output", temp.resolve("missing/deep").toString()))
			.isEqualTo(new Result(2, "", "content-import: directories must exist\n"));
		assertThat(generate("--output", link.toString()))
			.isEqualTo(new Result(2, "", "content-import: symbolic links are not allowed\n"));
		assertThat(snapshot(temp)).isEqualTo(before);
	}

	@Test void wrapperHelpAndMissingJarStayOffline() throws Exception {
		Path wrapper = temp.resolve("content-import");
		Files.copy(Path.of("content-import"), wrapper);
		Map<String, String> before = snapshot(temp);
		Process help = new ProcessBuilder("sh", wrapper.toString(), "--help").start();
		assertThat(new String(help.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(HELP);
		assertThat(new String(help.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
		assertThat(help.waitFor()).isZero();
		Process missing = new ProcessBuilder("sh", wrapper.toString(), "generate").start();
		assertThat(new String(missing.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
		assertThat(new String(missing.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(
			"content-import: packaged backend jar missing; build first: cd backend && ./mvnw -DskipTests package\n");
		assertThat(missing.waitFor()).isEqualTo(2);
		assertThat(snapshot(temp)).isEqualTo(before);
	}

	// --- fixtures and process helpers ---

	JsonNode manifest() throws Exception {
		return JSON.readTree(Files.readString(output.resolve("demo/manifest.json")));
	}

	List<String> findings() throws Exception {
		return findings(report);
	}

	/** One accepted folder entry: its source path and the files beneath it, in order. */
	static void folder(ObjectNode manifest, String sourcePath, String... resourceIds) {
		ArrayNode list = ((ArrayNode) manifest.get("sourceToId")).addObject().put("sourcePath", sourcePath)
			.putArray("resourceIds");
		for (String id : resourceIds) list.add(id);
	}

	static List<String> ids(JsonNode array) {
		List<String> result = new ArrayList<>();
		array.forEach(node -> result.add(node.asString()));
		return result;
	}

	void rewrite(JsonNode manifest) throws Exception {
		Files.writeString(output.resolve("demo/manifest.json"), JSON.writeValueAsString(manifest));
	}

	List<String> findings(Path file) throws Exception {
		List<String> result = new ArrayList<>();
		JSON.readTree(Files.readString(file)).path("findings").forEach(node -> result.add(node.asString()));
		return result;
	}

	List<String> reported() throws Exception {
		List<String> result = new ArrayList<>();
		JSON.readTree(Files.readString(report)).path("reported").forEach(node -> result.add(node.asString()));
		return result;
	}

	/** Makes one accepted lesson available with the given body and exercises in both copies, verbatim. */
	void accept(String lessonId, String sourcePath, String body, String... titles) throws Exception {
		active.write("demo/lessons/" + lessonId + ".md", body);
		active.editManifest(manifest -> {
			tools.jackson.databind.node.ArrayNode exercises =
				(tools.jackson.databind.node.ArrayNode) ContentFixture.lesson(manifest, lessonId).path("exercises");
			for (int i = 0; i < titles.length; i++) {
				exercises.addObject().put("id", lessonId + ".e%02d".formatted(i + 1)).put("lessonId", lessonId)
					.put("title", titles[i]);
			}
		});
		// The course declares the same exercises in its curso.json: that is the identity the importer reads.
		tools.jackson.databind.node.ArrayNode declared = module(lessonId).putArray("ejercicios");
		for (int i = 0; i < titles.length; i++) {
			declared.addObject().put("id", lessonId + ".e%02d".formatted(i + 1)).put("titulo", titles[i]);
		}
		files.put(sourcePath, body.getBytes(StandardCharsets.UTF_8));
	}

	/** One payload must stop the whole candidate and leave the active and source snapshots untouched. */
	void assertBlocked(String payload, int count, String header, String... expected) throws Exception {
		files.put("proyecto/01-inicial.md", (header + payload + "\n").getBytes(StandardCharsets.UTF_8));
		String changed = commit(files, "blocked " + payload.hashCode() + "\n");
		Path out = temp.resolve("blocked-" + payload.hashCode());
		Path log = temp.resolve("blocked-" + payload.hashCode() + ".json");
		Map<String, String> activeBefore = snapshot(active.root);
		Map<String, String> sourceBefore = snapshot(source);
		assertThat(generate("--commit", changed, "--output", out.toString(), "--report", log.toString()))
			.isEqualTo(new Result(3, "Candidate not written; findings: " + count + "\n", ""));
		assertThat(findings(log)).containsExactly(expected);
		assertThat(Files.exists(out)).isFalse();
		assertThat(snapshot(active.root)).isEqualTo(activeBefore);
		assertThat(snapshot(source)).isEqualTo(sourceBefore);
	}

	static JsonNode link(JsonNode manifest, String sourcePath) {
		for (JsonNode link : manifest.path("sourceToId")) {
			if (sourcePath.equals(link.path("sourcePath").asString())) return link;
		}
		throw new IllegalArgumentException(sourcePath);
	}

	static JsonNode resource(JsonNode manifest, String sourcePath) {
		for (JsonNode resource : manifest.path("resources")) {
			if (sourcePath.equals(resource.path("sourcePath").asString())) return resource;
		}
		throw new IllegalArgumentException(sourcePath);
	}

	static JsonNode resourceById(JsonNode manifest, String resourceId) {
		for (JsonNode resource : manifest.path("resources")) {
			if (resourceId.equals(resource.path("resourceId").asString())) return resource;
		}
		throw new IllegalArgumentException(resourceId);
	}

	static Map<String, String> fragments(JsonNode manifest, String sourcePath) {
		Map<String, String> result = new TreeMap<>();
		link(manifest, sourcePath).path("fragments").properties()
			.forEach(entry -> result.put(entry.getKey(), entry.getValue().asString()));
		return result;
	}

	byte[] copy(String relativePath) throws Exception {
		if (relativePath == null) return null;
		return Files.readAllBytes(active.root.resolve(relativePath));
	}

	static String sha(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	String commit(Map<String, byte[]> content, String message) throws Exception {
		Map<String, byte[]> withCourse = new TreeMap<>(content);
		withCourse.put("curso.json", JSON.writeValueAsBytes(curso));
		return git(message.getBytes(StandardCharsets.UTF_8), "commit-tree", tree(withCourse)).strip();
	}

	/** The module of curso.json with this ID, to change what the course declares in one test. */
	ObjectNode module(String id) {
		for (JsonNode phase : curso.path("fases")) for (JsonNode module : phase.path("modulos")) {
			if (id.equals(module.path("id").asString())) return (ObjectNode) module;
		}
		throw new IllegalArgumentException(id);
	}

	String tree(Map<String, byte[]> content) throws Exception {
		Map<String, byte[]> direct = new TreeMap<>();
		Map<String, Map<String, byte[]>> directories = new TreeMap<>();
		for (Map.Entry<String, byte[]> entry : content.entrySet()) {
			int slash = entry.getKey().indexOf('/');
			if (slash < 0) direct.put(entry.getKey(), entry.getValue());
			else directories.computeIfAbsent(entry.getKey().substring(0, slash), key -> new TreeMap<>())
				.put(entry.getKey().substring(slash + 1), entry.getValue());
		}
		StringBuilder input = new StringBuilder();
		for (Map.Entry<String, byte[]> entry : direct.entrySet()) {
			String blob = git(entry.getValue(), "hash-object", "-w", "--stdin").strip();
			input.append("100644 blob ").append(blob).append('\t').append(entry.getKey()).append('\0');
		}
		for (Map.Entry<String, Map<String, byte[]>> entry : directories.entrySet()) {
			input.append("040000 tree ").append(tree(entry.getValue())).append('\t').append(entry.getKey()).append('\0');
		}
		return git(input.toString().getBytes(StandardCharsets.UTF_8), "mktree", "-z").strip();
	}

	String git(byte[] input, String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of("git", "-C", source.toString()));
		command.addAll(List.of(args));
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().putAll(Map.of("GIT_AUTHOR_NAME", "Fixture", "GIT_AUTHOR_EMAIL", "fixture@example.invalid",
			"GIT_COMMITTER_NAME", "Fixture", "GIT_COMMITTER_EMAIL", "fixture@example.invalid",
			// Fixed dates keep every fixture commit hash, and so every recorded candidate byte, reproducible.
			"GIT_AUTHOR_DATE", "2026-01-01T00:00:00 +0000", "GIT_COMMITTER_DATE", "2026-01-01T00:00:00 +0000"));
		Process process = builder.start();
		if (input != null) process.getOutputStream().write(input);
		process.getOutputStream().close();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
		assertThat(process.exitValue()).as(err).isZero();
		return out;
	}

	record Result(int code, String out, String err) {}

	Result generate(String... overrides) throws Exception {
		return cli(generateArguments(overrides));
	}

	String[] generateArguments(String... overrides) {
		Map<String, String> options = new TreeMap<>(Map.of("--active", active.root.toString(),
			"--source", source.toString(), "--commit", commit, "--output", output.toString(),
			"--report", report.toString()));
		for (int i = 0; i < overrides.length; i += 2) options.put(overrides[i], overrides[i + 1]);
		List<String> args = new ArrayList<>(List.of("generate"));
		options.forEach((name, value) -> {
			args.add(name);
			args.add(value);
		});
		return args.toArray(String[]::new);
	}

	Result cli(String... args) throws Exception {
		return cli(Map.of(), args);
	}

	/** One CLI process with an explicit environment: a fixture `git` shim can then count the Git calls it makes. */
	Result cli(Map<String, String> environment, String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(),
			"-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
			"-Duser.home=" + System.getProperty("user.home"),
			"-cp", System.getProperty("java.class.path"), "dev.codeacademy.content.ContentImportCli"));
		command.addAll(List.of(args));
		command.addAll(0, List.of("sh", "-c", "umask 022; exec \"$@\"", "content-import-test"));
		ProcessBuilder builder = new ProcessBuilder(command);
		// Preserve assigned scratch locations explicitly, without ambient launcher banners on stderr.
		for (String variable : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
			builder.environment().remove(variable);
		}
		builder.environment().putAll(environment);
		Process process = builder.start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
		return new Result(process.exitValue(), out, err);
	}

	Map<String, String> snapshot(Path root) throws Exception {
		Map<String, String> result = new TreeMap<>();
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted().toList()) {
				String relative = root.relativize(path).toString();
				result.put(relative, Files.isSymbolicLink(path) ? "link:" + Files.readSymbolicLink(path)
					: Files.isDirectory(path) ? "directory" : sha(Files.readAllBytes(path)));
			}
		}
		return result;
	}
}
