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

/** The committed java-02.01 lesson through the whole pipeline. */
class RealLessonContentTest {

	private static final Path CONTENT = Path.of("../content");
	private static final String LESSON = "java-02.01";

	private static CourseContent content;
	private static LessonContent lesson;

	@BeforeAll
	static void load() {
		content = ContentLoader.load(CONTENT, "java/manifest.json");
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

	@Test
	void onlyTheImportedLessonIsRendered() {
		assertThat(content.lessons()).containsOnlyKeys(LESSON);
		assertThat(lesson.lessonId()).isEqualTo(LESSON);
	}

	@Test
	void nineExercisesWithManifestIdsAndPlainTextTitles() {
		assertThat(lesson.exercises()).extracting(ExerciseContent::id)
			.containsExactly("java-02.01-e01", "java-02.01-e02", "java-02.01-e03", "java-02.01-e04", "java-02.01-e05",
					"java-02.01-e06", "java-02.01-e07", "java-02.01-e08", "java-02.01-e09");
		assertThat(lesson.exercises()).extracting(ExerciseContent::title)
			.containsExactly("La ficha de la tienda", "Predice la salida", "Arregla el aviso",
					"El aviso de la copia de seguridad", "De compacto a clásico", "Comentarios que ayudan",
					"Diagnóstico a distancia", "¿Qué main se ejecuta?", "La pantalla de inicio de «Mi Biblioteca»");
		assertThat(lesson.exercises()).allSatisfy(exercise -> {
			assertThat(exercise.lessonId()).isEqualTo(LESSON);
			assertThat(exercise.courseId()).isEqualTo("java");
		});
	}

	@Test
	void everyStatementAndSolutionIsNonEmptyAndTheSolutionKeepsItsDetails() {
		assertThat(lesson.exercises()).allSatisfy(exercise -> {
			assertThat(text(exercise.statementHtml())).isNotBlank();
			assertThat(exercise.statementHtml()).doesNotContain("<details", "Ver solución");
			assertThat(exercise.solutionHtml()).startsWith("<details>").contains("<summary>Ver solución</summary>");
			assertThat(exercise.solutionHtml().strip()).endsWith("</details>");
			assertThat(text(exercise.solutionHtml()).replace("Ver solución", "")).isNotBlank();
		});
	}

	@Test
	void theBodyDoesNotDuplicateTheExercises() {
		String body = lesson.bodyHtml();
		assertThat(body).doesNotContain("Ejercicios prácticos resueltos", "Ver solución", "<details", "Ejercicio 1 ·",
				"Básico</h", "Caso real</h");
		Pattern paragraph = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);
		for (ExerciseContent exercise : lesson.exercises()) {
			for (String part : List.of(exercise.statementHtml(), exercise.solutionHtml())) {
				List<String> distinctive = all(paragraph, part).stream().filter(p -> text(p).length() >= 60).toList();
				assertThat(distinctive).as(exercise.id()).isNotEmpty();
				distinctive.forEach(p -> assertThat(body).as(exercise.id()).doesNotContain(p));
			}
		}
		assertThat(body).contains("Chuleta").contains("Buenas prácticas");
	}

	@Test
	void theFragmentLinkToExerciseEightPointsToTheExerciseId() {
		assertThat(lesson.bodyHtml()).contains("<a href=\"#java-02.01-e08\">ejercicio 8</a>");
		assertThat(String.join("", allHtml())).doesNotContain("ejercicio-8--");
	}

	@Test
	void linksToPendingLessonsDropTheirFragment() {
		Pattern lessonHref = Pattern.compile("href=\"(/lessons/[^\"]*)\"");
		List<String> pendingFaseOne = allHtml().stream().flatMap(html -> all(lessonHref, html).stream())
			.filter(href -> href.startsWith("/lessons/java-01.")).toList();
		// 12 links to fase-01 lessons, 7 of them carried a fragment in the source.
		assertThat(pendingFaseOne).hasSize(12).allSatisfy(href -> assertThat(href).doesNotContain("#"));
		assertThat(new HashSet<>(pendingFaseOne)).containsExactlyInAnyOrder("/lessons/java-01.01", "/lessons/java-01.02",
				"/lessons/java-01.03", "/lessons/java-01.04", "/lessons/java-01.05", "/lessons/java-01.09");
		assertThat(String.join("", allHtml())).doesNotContain("21-tu-primer-proyecto-en-intellij",
				"12-el-recorrido-completo", "17-javac-a-fondo");
	}

	@Test
	void folderLinksBecomeOneLinkPerFileNamedLikeTheOriginalFile() {
		String all = String.join("", allHtml());
		assertThat(lesson.bodyHtml()).contains(
				"<code>codigo/01-primer-programa/ejemplos/2.1-hola-mundo/</code> (<a href=\"/api/resources/java-02.01-r-2.1-hello\">Hello.java</a>)");
		assertThat(all).contains("(<a href=\"/api/resources/java-02.01-r-ej04-backup-notice\">BackupNotice.java</a>, "
				+ "<a href=\"/api/resources/java-02.01-r-ej04-backup-notice-readable\">BackupNoticeReadable.java</a>)");
		Pattern resourceLink = Pattern.compile("<a href=\"/api/resources/([^\"]+)\">([^<]*)</a>");
		Matcher matcher = resourceLink.matcher(all);
		int links = 0;
		while (matcher.find()) {
			ResourceFile resource = content.resources().get(matcher.group(1));
			assertThat(resource).as(matcher.group(1)).isNotNull();
			assertThat(matcher.group(2)).isEqualTo(resource.originalFileName()).doesNotContain("java-02.01-r-");
			links++;
		}
		assertThat(links).isGreaterThanOrEqualTo(22);
		assertThat(all).doesNotContain("href=\"codigo/", "href=\"../", ".md\"");
		assertThat(new LinkedHashSet<>(lesson.resourceIds())).hasSize(22).isEqualTo(content.resources().keySet());
	}

	@Test
	void resourceLookupKeepsTheOriginalFileName() {
		ResourceFile hello = content.resources().get("java-02.01-r-2.1-hello");
		assertThat(hello.originalFileName()).isEqualTo("Hello.java");
		assertThat(hello.mediaType()).isEqualTo("text/plain; charset=utf-8");
		assertThat(hello.localPath()).isRegularFile().endsWith(Path.of("java-02.01-r-2.1-hello.java"));
		assertThat(content.resources().get("java-02.01-r-ej08-programa-b-report").originalFileName()).isEqualTo("Report.java");
	}

	@Test
	void mermaidBlocksKeepTheirLanguageClassAndEscapedSource() {
		Pattern mermaid = Pattern.compile("<pre><code class=\"language-mermaid\">(.*?)</code></pre>", Pattern.DOTALL);
		List<String> blocks = all(mermaid, lesson.bodyHtml());
		assertThat(blocks).hasSize(2);
		assertThat(blocks.get(1)).contains("Hello.java&lt;br/&gt;(texto que escribes tú)").doesNotContain("<br", "<");
		assertThat(text(blocks.get(1))).contains("A[\"Hello.java<br/>(texto que escribes tú)\"] -->|javac comprueba y traduce|");
		assertThat(lesson.bodyHtml()).contains("<code class=\"language-java\">");
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
		assertThat(ids).contains("qué-aprenderás", "114-ampliación-cómo-elige-java-qué-main-ejecutar")
			.doesNotContain("-básico", "5-ejercicios-prácticos-resueltos", "ejercicio-8--qué-main-se-ejecuta");
	}

	@Test
	void everyHrefHasAnAllowedForm() {
		Set<String> bodyIds = new HashSet<>(all(Pattern.compile("\\sid=\"([^\"]*)\""), lesson.bodyHtml()));
		Set<String> exerciseIds = new HashSet<>(lesson.exercises().stream().map(ExerciseContent::id).toList());
		Pattern anchor = Pattern.compile("<a ([^>]*)>");
		int checked = 0;
		for (String html : allHtml()) {
			for (String attributes : all(anchor, html)) {
				Matcher href = Pattern.compile("^href=\"([^\"]*)\"( target=\"_blank\" rel=\"noopener noreferrer\")?$")
					.matcher(attributes);
				assertThat(href.matches()).as(attributes).isTrue();
				String value = href.group(1);
				boolean external = value.startsWith("https://") || value.startsWith("http://");
				assertThat(href.group(2) != null).as(attributes).isEqualTo(external);
				if (value.startsWith("#")) {
					String target = value.substring(1);
					assertThat(bodyIds.contains(target) || exerciseIds.contains(target)).as(value).isTrue();
				}
				else if (value.startsWith("/lessons/")) {
					assertThat(value).matches("/lessons/java-\\d\\d\\.\\d\\d");
				}
				else if (value.startsWith("/api/resources/")) {
					assertThat(content.resources()).containsKey(value.substring("/api/resources/".length()));
				}
				else if (!external) {
					assertThat(value).isEqualTo("/courses/java");
				}
				checked++;
			}
		}
		assertThat(checked).isGreaterThan(80);
		// java-02.01 has no external links today: no anchor of the lesson opens a new tab.
		assertThat(String.join("", allHtml())).doesNotContain("target=", "rel=");
		assertThat(lesson.bodyHtml()).contains("<a href=\"/courses/java\">Índice</a>")
			.contains("<a href=\"#28-ampliación-qué-main-se-ejecuta\">");
	}

	@Test
	void realLessonFixtureCoversEveryHeadingOfTheSource() throws IOException {
		String source = Files.readString(CONTENT.resolve("java/lessons/java-02.01.md"), StandardCharsets.UTF_8);
		assertThat(HeadingIdsTest.expectedIds("real")).hasSize(64);
		assertThat(HeadingIds.forMarkdown(source)).hasSize(64);
	}
}
