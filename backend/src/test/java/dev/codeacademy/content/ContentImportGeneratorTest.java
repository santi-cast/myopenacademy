package dev.codeacademy.content;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure source-inventory rules of the offline generator: Markdown parsing, GitHub anchors, table inventory and media
 * types. No Git repository, filesystem, database or Spring context is involved.
 */
class ContentImportGeneratorTest {

	static final String INDEX = """
		# Índice del curso

		- [Fase 00 — Primera fase](#fase-00--primera-fase) · 2 módulos
		- [Proyecto evolutivo](#proyecto-evolutivo) · 2 etapas

		---

		## Fase 00 — Primera fase

		| # | Módulo | Nivel | Tiempo | Estado |
		|---|---|---|---|---|
		| 00.01 | 🧭 [Introducción](fase-00/01-intro.md) | Básico · Núcleo | 2 h | ✅ Disponible |
		| 00.02 | [Siguiente](fase-00/02-siguiente.md) | Básico · Núcleo | 3 h | ⏳ Pendiente |

		---

		## Proyecto evolutivo

		| Etapa | Qué se construye | Tras | Estado |
		|---|---|---|---|
		| ◆ 1 | 🧭 [Biblioteca en consola](proyecto/01-consola.md) | fase 02 | ✅ Disponible |
		| ◆ 2 | [Interfaz web](proyecto/02-web.md) | fase 13 | ⏳ Pendiente |

		---

		## Ficheros especiales

		| Fichero | Para qué sirve |
		|---|---|
		| [`README.md`](README.md) | Portada |
		""";

	@Test
	void lessonRowsComeOnlyFromModuleTables() {
		Map<String, ContentImportDocuments.LessonRow> rows = ContentImportDocuments.lessons(INDEX);
		assertThat(rows).containsOnlyKeys("fase-00/01-intro.md", "fase-00/02-siguiente.md");
		assertThat(rows.get("fase-00/01-intro.md").title()).isEqualTo("Introducción");
		assertThat(rows.get("fase-00/01-intro.md").available()).isTrue();
		assertThat(rows.get("fase-00/02-siguiente.md").available()).isFalse();
	}

	@Test
	void stageRowsComeOnlyFromTheEvolvedProjectSection() {
		List<ContentImportDocuments.StageRow> stages = ContentImportDocuments.stages(INDEX);
		assertThat(stages).containsExactly(
			new ContentImportDocuments.StageRow(1, "Biblioteca en consola", "proyecto/01-consola.md", true),
			new ContentImportDocuments.StageRow(2, "Interfaz web", "proyecto/02-web.md", false));
	}

	@Test
	void anchorsFollowTheSharedGitHubHeadingRule() {
		String markdown = "# Título\n\n## Repetida\n\n## Repetida\n\n### ¿Qué aprenderás?\n\n"
			+ "## 5. Ejercicios prácticos resueltos\n\n#### Ejercicio 1 · Primer `ejercicio`\n";
		assertThat(ContentImportDocuments.anchors(markdown)).containsExactly("título", "repetida", "repetida-1",
			"qué-aprenderás", "5-ejercicios-prácticos-resueltos", "ejercicio-1--primer-ejercicio");
	}

	@Test
	void referencesCarryTheirFirstLineAndImageKind() {
		String markdown = "# A\n\nVer [B](b.md#frag) y ![i](img.png).\n\nCierra en [C](c.md).\n";
		List<ContentImportDocuments.Reference> references = ContentImportDocuments.references(markdown);
		assertThat(references).extracting(ContentImportDocuments.Reference::destination)
			.containsExactly("b.md#frag", "img.png", "c.md");
		assertThat(references).extracting(ContentImportDocuments.Reference::line).containsExactly(3, 3, 5);
		assertThat(references).extracting(ContentImportDocuments.Reference::image).containsExactly(false, true, false);
	}

	@Test
	void exerciseTitlesComeFromTheExercisesSectionWithTheRuntimeRule() {
		String markdown = """
			# Lección

			## 5. Ejercicios prácticos resueltos

			### 🟢 Básico

			#### Ejercicio 1 · Primer `título`

			Enunciado uno.

			#### Ejercicio 2 · Segundo ejercicio

			Enunciado dos.

			## 6. Chuleta

			#### Ejercicio 9 · Fuera de la sección
			""";

		assertThat(LessonPipeline.exerciseTitles(markdown)).containsExactly("Primer título", "Segundo ejercicio");
		assertThat(LessonPipeline.exerciseTitles("# Sin ejercicios\n\nTexto.\n")).isEmpty();
	}

	/** A sorted map that counts how many times its entries are scanned. */
	static final class Counting extends TreeMap<String, List<String>> {

		int scans;

		@Override
		public Set<Map.Entry<String, List<String>>> entrySet() {
			scans++;
			return super.entrySet();
		}
	}

	/**
	 * One folder entry lists every file its owners claim beneath it, at any depth, and one generation scans the claimed
	 * resources once instead of once per folder: a folder count and a resource count must never multiply.
	 */
	@Test
	void folderMembershipIsOnePassOverTheClaimedResources() {
		Counting claimed = new Counting();
		claimed.put("carpeta/a/Uno.java", List.of("demo-01.01-r-uno"));
		claimed.put("carpeta/a/src/Dos.java", List.of("demo-01.01-r-dos", "demo-stage-01-r-dos"));
		claimed.put("carpeta/ab/Tres.java", List.of("demo-01.01-r-tres"));
		claimed.put("carpeta/c/Cuatro.java", List.of("demo-stage-01-r-cuatro"));
		Map<String, List<String>> members = ContentImportGenerator.folderMembers(claimed,
			List.of("carpeta/a/", "carpeta/a/src/", "carpeta/c/"), Function.identity());
		assertThat(members).containsOnly(
			Map.entry("carpeta/a/", List.of("demo-01.01-r-uno", "demo-01.01-r-dos", "demo-stage-01-r-dos")),
			Map.entry("carpeta/a/src/", List.of("demo-01.01-r-dos", "demo-stage-01-r-dos")),
			Map.entry("carpeta/c/", List.of("demo-stage-01-r-cuatro")));
		// A prefix that is not a folder component is not containment: carpeta/ab/ is not below carpeta/a/.
		assertThat(members.get("carpeta/a/")).doesNotContain("demo-01.01-r-tres");
		assertThat(claimed.scans).as("passes over the claimed resources").isEqualTo(1);
	}

	/** A folder nobody claims keeps no entry, and a claim deeper than every folder contributes to none. */
	@Test
	void folderMembershipIgnoresUnclaimedFoldersAndUncontainedClaims() {
		Map<String, List<String>> members = ContentImportGenerator.folderMembers(
			new TreeMap<>(Map.of("carpeta/a/Uno.java", List.of("demo-01.01-r-uno"))),
			List.of("carpeta/b/"), Function.identity());
		assertThat(members).containsOnly(Map.entry("carpeta/b/", List.of()));
	}

	@Test
	void mediaTypesFollowTheSourceExtension() {
		assertThat(ContentImportDocuments.mediaType("fase-01/codigo/Hello.java")).isEqualTo("text/plain; charset=utf-8");
		assertThat(ContentImportDocuments.mediaType("fase-01/codigo/foto.PNG")).isEqualTo("image/png");
		assertThat(ContentImportDocuments.mediaType("fase-01/diagrama.svg")).isEqualTo("image/svg+xml");
		assertThat(ContentImportDocuments.mediaType("fase-01/guia.pdf")).isEqualTo("application/pdf");
		assertThat(ContentImportDocuments.mediaType("fase-01/codigo/.gitignore")).isEqualTo("text/plain; charset=utf-8");
		assertThat(ContentImportDocuments.mediaType("fase-01/codigo/Main.class")).isEqualTo("application/octet-stream");
		assertThat(ContentImportDocuments.image("fase-01/foto.jpeg")).isTrue();
		assertThat(ContentImportDocuments.image("fase-01/notas.md")).isFalse();
	}
}
