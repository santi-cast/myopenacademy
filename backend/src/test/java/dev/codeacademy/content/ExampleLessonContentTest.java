package dev.codeacademy.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The available module of the kit's example course, installed by the real importer (ExampleContent), through the
 * whole pipeline: exercises, hints, review questions, code folders, an image, Mermaid and every kind of link.
 */
class ExampleLessonContentTest {

	private static final String LESSON = ExampleContent.LESSON;
	private static final Path SOURCE = ExampleContent.COURSES.resolve("bueno/fase-01-primeros-pasos/01-ficheros-json.md");

	private static CourseContent content;
	private static LessonContent lesson;

	@BeforeAll
	static void load() {
		content = ContentLoader.load(ExampleContent.root(), "ejemplo/manifest.json");
		lesson = content.lessons().get(LESSON);
	}

	private static List<String> allHtml() {
		List<String> html = new ArrayList<>(List.of(lesson.bodyHtml()));
		lesson.exercises().forEach(exercise -> {
			html.add(exercise.statementHtml());
			html.add(exercise.solutionHtml());
		});
		return html;
	}

	private static List<String> all(Pattern pattern, String html) {
		List<String> found = new ArrayList<>();
		Matcher matcher = pattern.matcher(html);
		while (matcher.find()) found.add(matcher.group(1));
		return found;
	}

	private static String text(String html) {
		return html.replaceAll("<[^>]*>", "").replace("&#34;", "\"").replace("&#39;", "'").replace("&#43;", "+")
			.replace("&#61;", "=").replace("&#64;", "@").replace("&#96;", "`").replace("&lt;", "<").replace("&gt;", ">")
			.replace("&amp;", "&");
	}

	/** The phase and the modules exactly as curso.json declares them, the pending one included. */
	@Test
	void theManifestHasThePhaseAndModulesOfCursoJson() {
		assertThat(content.manifest().phases())
			.containsExactly(new Manifest.Phase("ejemplo.fase.primeros-pasos", "Primeros pasos", 1));
		assertThat(content.manifest().lessons()).extracting(Manifest.Lesson::id, Manifest.Lesson::title,
				Manifest.Lesson::position, Manifest.Lesson::phaseId, Manifest.Lesson::available)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(LESSON, "Ficheros JSON", 1, "ejemplo.fase.primeros-pasos", true),
				org.assertj.core.groups.Tuple.tuple(ExampleContent.PENDING, "Ficheros YAML", 2,
					"ejemplo.fase.primeros-pasos", false));
	}

	@Test
	void everyAvailableLessonAndStageIsRendered() {
		assertThat(content.lessons().keySet()).containsExactly(LESSON);
		assertThat(content.manifest().projectStages()).extracting(Manifest.Stage::id).containsExactly(ExampleContent.STAGE);
		assertThat(content.stages().keySet()).containsExactly(ExampleContent.STAGE);
		assertThat(lesson.lessonId()).isEqualTo(LESSON);
	}

	@Test
	void twoExercisesWithTheIdsAndTitlesOfCursoJson() {
		assertThat(lesson.exercises()).extracting(ExerciseContent::id).containsExactly(ExampleContent.E01, ExampleContent.E02);
		assertThat(lesson.exercises()).extracting(ExerciseContent::title)
			.containsExactly("Un pedido de la tienda", "La coma de más");
		assertThat(lesson.exercises()).allSatisfy(exercise -> {
			assertThat(exercise.lessonId()).isEqualTo(LESSON);
			assertThat(exercise.courseId()).isEqualTo(ExampleContent.COURSE);
		});
	}

	/** The hints stay folded in the statement, before the solution; the solution keeps its own details. */
	@Test
	void everyStatementKeepsItsHintsAndEverySolutionItsDetails() {
		assertThat(lesson.exercises()).allSatisfy(exercise -> {
			assertThat(text(exercise.statementHtml())).isNotBlank();
			assertThat(exercise.statementHtml()).contains("<details>\n<summary>Pista 1</summary>").doesNotContain("Ver solución");
			assertThat(exercise.solutionHtml()).startsWith("<details>").contains("<summary>Ver solución</summary>");
			assertThat(exercise.solutionHtml().strip()).endsWith("</details>");
			assertThat(text(exercise.solutionHtml()).replace("Ver solución", "")).isNotBlank();
		});
		// A details nested in a solution stays inside it.
		assertThat(lesson.exercises().get(0).solutionHtml()).contains("<summary>Ver el pedido con <code>total</code> en céntimos</summary>");
	}

	@Test
	void theBodyDoesNotDuplicateTheExercises() {
		String body = lesson.bodyHtml();
		assertThat(body).doesNotContain("Ejercicios prácticos resueltos", "Ver solución", "Pista 1", "Ejercicio 1 ·",
				"Básico</h", "Caso real</h");
		Pattern paragraph = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);
		for (ExerciseContent exercise : lesson.exercises()) {
			for (String part : List.of(exercise.statementHtml(), exercise.solutionHtml())) {
				List<String> paragraphs = all(paragraph, part).stream().filter(p -> !text(p).isBlank()).toList();
				assertThat(paragraphs).as(exercise.id()).isNotEmpty();
				paragraphs.forEach(p -> assertThat(body).as(exercise.id()).doesNotContain(p));
			}
		}
		assertThat(body).contains("Chuleta").contains("Buenas prácticas");
	}

	/** The review questions stay folded at the end of the body (revised by the author, 9 November 2026). */
	@Test
	void theReviewQuestionsStayFoldedInTheBody() {
		assertThat(lesson.bodyHtml()).contains("<h2 id=\"7-preguntas-de-repaso\">7. Preguntas de repaso</h2>")
			.contains("<details>\n<summary>Ver respuesta</summary>")
			.contains("<a href=\"#12-objetos-y-listas\">1.2. Objetos y listas</a>");
	}

	@Test
	void theBodyKeepsTheThematicBreakOfTheSource() {
		assertThat(lesson.bodyHtml().split("<hr />", -1)).hasSize(2);
		assertThat(lesson.bodyHtml()).doesNotContainPattern("<hr />\\s*<hr />");
	}

	/** A link to a pending module leads to it without a fragment; the project stage links the available module. */
	@Test
	void linksToLessonsNameTheirIdsFromCursoJson() {
		Pattern lessonHref = Pattern.compile("href=\"(/lessons/[^\"]*)\"");
		List<String> links = allHtml().stream().flatMap(html -> all(lessonHref, html).stream()).toList();
		assertThat(links).containsExactly("/lessons/" + ExampleContent.PENDING);
		assertThat(all(lessonHref, content.stages().get(ExampleContent.STAGE).bodyHtml()))
			.containsExactly("/lessons/" + LESSON);
	}

	/** Until H8, links to another course and to a glossary keep their GitHub address and open a new tab. */
	@Test
	void linksToAnotherCourseKeepTheirGitHubAddress() {
		assertThat(lesson.bodyHtml()).contains("<a href=\"https://github.com/myopenacademy/ejemplo-destino/blob/main/"
				+ "fase-01-tu-primer-proyecto/01-mi-primer-repositorio.md#11-qué-es-un-repositorio\" target=\"_blank\" "
				+ "rel=\"noopener noreferrer\">Mi primer repositorio, 1.1</a>")
			.contains("<a href=\"https://github.com/myopenacademy/ejemplo-destino/blob/main/glosario.md#repositorio\" "
				+ "target=\"_blank\" rel=\"noopener noreferrer\">repositorio</a>");
	}

	@Test
	void folderLinksBecomeOneLinkPerFileNamedLikeTheOriginalFile() {
		String all = String.join("", allHtml());
		// The folder text itself links its ZIP, followed by one link per file.
		assertThat(lesson.bodyHtml()).contains(
				"<a href=\"/api/resources/ejemplo.ficheros-json-zip-22-contar-clientes-07172478\"><code>codigo/01-ficheros-json/ejemplos/2.2-contar-clientes/</code></a>"
						+ " (<a href=\"/api/resources/ejemplo.ficheros-json-r-argstxt-1ad58592\">args.txt</a>, "
						+ "<a href=\"/api/resources/ejemplo.ficheros-json-r-clientesjson-df94918b\">clientes.json</a>, "
						+ "<a href=\"/api/resources/ejemplo.ficheros-json-r-contarsh-ef17ab44\">contar.sh</a>)");
		Pattern resourceLink = Pattern.compile("<a href=\"/api/resources/([^\"]+)\">([^<]*)</a>");
		Matcher matcher = resourceLink.matcher(all);
		int links = 0;
		while (matcher.find()) {
			ResourceFile resource = content.resources().get(matcher.group(1));
			assertThat(resource).as(matcher.group(1)).isNotNull();
			assertThat(matcher.group(2)).isEqualTo(resource.originalFileName()).doesNotContain("-r-");
			links++;
		}
		assertThat(links).isEqualTo(11);
		Pattern zipLink = Pattern.compile("<a href=\"/api/resources/([^\"]+)\"><code>([^<]*)</code></a>");
		Matcher zips = zipLink.matcher(all);
		int zipLinks = 0;
		while (zips.find()) {
			ResourceFile zip = content.resources().get(zips.group(1));
			assertThat(zip).as(zips.group(1)).isNotNull();
			assertThat(zip.mediaType()).isEqualTo("application/zip");
			assertThat(zips.group(2)).endsWith("/").endsWith(zip.originalFileName().replaceFirst("\\.zip$", "/"));
			zipLinks++;
		}
		assertThat(zipLinks).isEqualTo(5);
		assertThat(all).doesNotContain("href=\"codigo/", "href=\"../", ".md\"");
		// Eleven code files, one image and five folder ZIPs; the lesson owns exactly its manifest resources.
		List<String> owned = content.manifest().resources().stream().filter(r -> LESSON.equals(r.lessonId()))
			.map(Manifest.Resource::resourceId).toList();
		assertThat(owned).hasSize(17);
		assertThat(owned.stream().filter(id -> id.startsWith(LESSON + "-zip-"))).hasSize(5);
		assertThat(new LinkedHashSet<>(lesson.resourceIds())).isEqualTo(new LinkedHashSet<>(owned));
		assertThat(content.resources().keySet()).containsAll(owned);
	}

	@Test
	void resourceLookupKeepsTheOriginalFileName() {
		ResourceFile script = content.resources().get("ejemplo.ficheros-json-r-contarsh-ef17ab44");
		assertThat(script.originalFileName()).isEqualTo("contar.sh");
		assertThat(script.mediaType()).isEqualTo("text/plain; charset=utf-8");
		assertThat(script.localPath()).isRegularFile().endsWith(Path.of("ejemplo.ficheros-json-r-contarsh-ef17ab44"));
		ResourceFile image = content.resources().get("ejemplo.ficheros-json-r-estructurasvg-1e17e92d");
		assertThat(image.originalFileName()).isEqualTo("estructura.svg");
		assertThat(image.mediaType()).isEqualTo("image/svg+xml");
	}

	/**
	 * Files whose names have non-ASCII letters and spaces (año-pequeño.csv, canción del año.txt, in NFC): their IDs
	 * keep the letters and turn spaces into hyphens, their links name them as they are, and their folder ZIP stores
	 * them under the same names, in UTF-8 and NFC.
	 */
	@Test
	void filesWithNonAsciiLettersAndSpacesKeepTheirNamesInTheLinkAndTheZip() throws IOException {
		String id = "ejemplo.ficheros-json-r-año-pequeñocsv-8ab61787";
		ResourceFile file = content.resources().get(id);
		assertThat(file.originalFileName()).isEqualTo("a\u00f1o-peque\u00f1o.csv");
		String withSpaces = "ejemplo.ficheros-json-r-canción-del-añotxt-e0a88b36";
		assertThat(content.resources().get(withSpaces).originalFileName()).isEqualTo("canci\u00f3n del a\u00f1o.txt");
		// Spaces never reach an ID: the generator turns them into hyphens, so no address has one to encode.
		assertThat(content.resources().keySet()).noneMatch(resource -> resource.contains(" "));
		assertThat(lesson.bodyHtml()).contains("(<a href=\"/api/resources/" + id + "\">año-pequeño.csv</a>, "
				+ "<a href=\"/api/resources/" + withSpaces + "\">canción del año.txt</a>, "
				+ "<a href=\"/api/resources/ejemplo.ficheros-json-r-clientejson-a65f1ea7\">cliente.json</a>)");
		ResourceFile zip = content.resources().get("ejemplo.ficheros-json-zip-21-un-cliente-1a94325a");
		try (var archive = new java.util.zip.ZipFile(zip.localPath().toFile(), StandardCharsets.UTF_8)) {
			assertThat(archive.stream().map(java.util.zip.ZipEntry::getName))
				.containsExactlyInAnyOrder("a\u00f1o-peque\u00f1o.csv", "canci\u00f3n del a\u00f1o.txt", "cliente.json");
			assertThat(archive.getInputStream(archive.getEntry("a\u00f1o-peque\u00f1o.csv")).readAllBytes())
				.isEqualTo(Files.readAllBytes(file.localPath()));
			assertThat(archive.getInputStream(archive.getEntry("canci\u00f3n del a\u00f1o.txt")).readAllBytes())
				.isEqualTo(Files.readAllBytes(content.resources().get(withSpaces).localPath()));
		}
		// The stored name is UTF-8 in NFC (ñ = c3 b1), never the decomposed n + U+0303.
		String raw = new String(Files.readAllBytes(zip.localPath()), StandardCharsets.ISO_8859_1);
		assertThat(raw).contains(new String("a\u00f1o-peque\u00f1o.csv".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1))
			.contains(new String("canci\u00f3n del a\u00f1o.txt".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1))
			.doesNotContain(new String("o\u0301".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1))
			.doesNotContain(new String("n\u0303".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
	}

	/**
	 * Verification files are files of their folder (FORMATO.md 7.1): the 2.4 example, added by the kit in 27893e8, ships
	 * its servidor.json (the local HTTP server for curl) and sesion.txt in the learner's ZIP, byte for byte.
	 */
	@Test
	void theCurlExampleFolderShipsWholeInItsZip() throws IOException {
		Path folder = ExampleContent.COURSES.resolve(
			"bueno/fase-01-primeros-pasos/codigo/01-ficheros-json/ejemplos/2.4-preguntar-a-una-api");
		ResourceFile zip = content.resources().values().stream()
			.filter(resource -> resource.resourceId().startsWith(LESSON + "-zip-24-preguntar-a-una-api-")).findFirst().orElseThrow();
		assertThat(zip.originalFileName()).isEqualTo("2.4-preguntar-a-una-api.zip");
		try (var archive = new java.util.zip.ZipFile(zip.localPath().toFile(), StandardCharsets.UTF_8)) {
			assertThat(archive.stream().map(java.util.zip.ZipEntry::getName)).containsExactlyInAnyOrder("servidor.json", "sesion.txt");
			for (String name : List.of("servidor.json", "sesion.txt")) {
				assertThat(archive.getInputStream(archive.getEntry(name)).readAllBytes()).as(name)
					.isEqualTo(Files.readAllBytes(folder.resolve(name)));
			}
		}
	}

	@Test
	void theImageIsServedAsAResource() {
		assertThat(lesson.bodyHtml()).contains(
				"<img src=\"/api/resources/ejemplo.ficheros-json-r-estructurasvg-1e17e92d\" alt=\"Estructura de un objeto JSON\" />");
	}

	@Test
	void mermaidBlocksKeepTheirLanguageClassAndEscapedSource() {
		Pattern mermaid = Pattern.compile("<pre><code class=\"language-mermaid\">(.*?)</code></pre>", Pattern.DOTALL);
		List<String> blocks = all(mermaid, lesson.bodyHtml());
		assertThat(blocks).hasSize(1);
		assertThat(blocks.get(0)).doesNotContain("<");
		assertThat(text(blocks.get(0))).contains("A[\"Objeto &quot;cliente&quot;\"] --> B[Clave]");
		assertThat(lesson.bodyHtml()).contains("<code class=\"language-json\">", "<code class=\"language-bash\">");
	}

	@Test
	void everyIdIsAGeneratedHeadingIdOnAHeading() throws IOException {
		Set<String> generated = new HashSet<>(HeadingIdsTest.expectedIds("real"));
		Pattern idAttribute = Pattern.compile("<([a-z0-9]+)[^>]*\\sid=\"([^\"]*)\"");
		List<String> ids = new ArrayList<>();
		for (String html : allHtml()) {
			Matcher matcher = idAttribute.matcher(html);
			while (matcher.find()) {
				assertThat(matcher.group(1)).matches("h[1-6]");
				ids.add(matcher.group(2));
			}
		}
		assertThat(ids).doesNotHaveDuplicates().isNotEmpty();
		assertThat(generated).containsAll(ids);
		assertThat(ids).contains("qué-aprenderás", "31-la-coma-de-más")
			.doesNotContain("-básico", "5-ejercicios-prácticos-resueltos", "ejercicio-1--un-pedido-de-la-tienda");
	}

	@Test
	void everyHrefHasAnAllowedForm() {
		Set<String> bodyIds = new HashSet<>(all(Pattern.compile("\\sid=\"([^\"]*)\""), lesson.bodyHtml()));
		Set<String> exerciseIds = new HashSet<>(lesson.exercises().stream().map(ExerciseContent::id).toList());
		Pattern anchor = Pattern.compile("<a ([^>]*)>");
		int checked = 0;
		int external = 0;
		for (String html : allHtml()) {
			for (String attributes : all(anchor, html)) {
				Matcher href = Pattern.compile("^href=\"([^\"]*)\"( target=\"_blank\" rel=\"noopener noreferrer\")?$")
					.matcher(attributes);
				assertThat(href.matches()).as(attributes).isTrue();
				String value = href.group(1);
				boolean isExternal = value.startsWith("https://") || value.startsWith("http://");
				assertThat(href.group(2) != null).as(attributes).isEqualTo(isExternal);
				if (isExternal) external++;
				if (value.startsWith("#")) {
					String target = value.substring(1);
					assertThat(bodyIds.contains(target) || exerciseIds.contains(target)).as(value).isTrue();
				}
				else if (value.startsWith("/lessons/")) {
					assertThat(value.substring("/lessons/".length())).isIn(content.manifest().lessons().stream()
						.map(Manifest.Lesson::id).toList());
				}
				else if (value.startsWith("/api/resources/")) {
					assertThat(content.resources()).containsKey(value.substring("/api/resources/".length()));
				}
				else if (!isExternal) {
					assertThat(value).isEqualTo("/courses/ejemplo");
				}
				checked++;
			}
		}
		assertThat(checked).isEqualTo(25);
		assertThat(external).isEqualTo(2);
		// The navigation line: the index and, until H8 gives the challenges their page, the index for them too.
		assertThat(lesson.bodyHtml()).contains("<a href=\"/courses/ejemplo\">Índice</a>")
			.contains("<a href=\"/courses/ejemplo\">Siguiente: Retos de la fase</a>");
	}

	@Test
	void realLessonFixtureCoversEveryHeadingOfTheSource() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
		assertThat(HeadingIdsTest.expectedIds("real")).hasSize(23);
		assertThat(HeadingIds.forMarkdown(source)).hasSize(23);
	}

	/** The copy is the source byte for byte: the installed body is the module of the pinned commit. */
	@Test
	void theInstalledBodyIsTheSourceModule() throws IOException {
		assertThat(ExampleContent.root().resolve("ejemplo/lessons/" + LESSON + ".md")).hasSameBinaryContentAs(SOURCE);
	}
}
