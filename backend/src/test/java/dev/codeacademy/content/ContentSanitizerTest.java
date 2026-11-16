package dev.codeacademy.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The allow-list removes anything executable, and the loader refuses lessons that would be altered by it. */
class ContentSanitizerTest {

	private static final Set<String> IDS = Set.of("qué-aprenderás");
	private static final Set<String> HREFS = Set.of("/lessons/demo.01.02", "#qué-aprenderás", "https://dev.java/");

	@TempDir
	Path tempDir;

	private static String sanitize(String html, List<String> removed) {
		return ContentSanitizer.sanitize(html, IDS, HREFS, removed);
	}

	@Test
	void keepsWhatThePipelineProduces() {
		String html = """
				<h2 id="qué-aprenderás">Qué <code>main</code></h2>
				<p><strong>a</strong> <em>b</em> <del>c</del> <kbd>d</kbd> x<sup>2</sup> y<sub>i</sub><br />
				<a href="/lessons/demo.01.02">l</a> <a href="#qué-aprenderás">f</a> <a href="https://dev.java/">w</a></p>
				<blockquote><p>q</p></blockquote><hr />
				<ol start="2"><li>uno</li></ol><ul><li>dos</li></ul>
				<table><thead><tr><th align="left">a</th></tr></thead><tbody><tr><td align="right">1</td></tr></tbody></table>
				<pre><code class="language-c++">x &lt; y</code></pre><pre><code class="language-mermaid">A --&gt; B</code></pre>
				<details>
				<summary>Ver solución</summary>
				<p>s</p>
				</details>
				""";
		List<String> removed = new ArrayList<>();

		String clean = sanitize(html, removed);

		assertThat(removed).isEmpty();
		assertThat(clean).contains("<h2 id=\"qué-aprenderás\">", "<del>c</del>", "<kbd>d</kbd>", "<sup>2</sup>",
				"<sub>i</sub>", "<br />", "<a href=\"/lessons/demo.01.02\">l</a>", "<a href=\"#qué-aprenderás\">f</a>",
				"<a href=\"https://dev.java/\" target=\"_blank\" rel=\"noopener noreferrer\">w</a>", "<ol start=\"2\">",
				"<th align=\"left\">", "<td align=\"right\">", "<code class=\"language-c&#43;&#43;\">",
				"<code class=\"language-mermaid\">A --&gt; B</code>", "<details>", "<summary>Ver solución</summary>",
				"<blockquote>", "<hr />");
		// Only absolute http/https links open a new tab; internal and fragment links stay in the page.
		assertThat(clean.split("target=", -1)).hasSize(2);
	}

	@Test
	void externalLinksOpenInANewTabWithoutOpenerWhileOtherLinksDoNot() {
		Set<String> hrefs = Set.of("http://example.org/a", "https://dev.java/", "/lessons/demo.01.02", "#qué-aprenderás",
				"/courses/demo", "/api/resources/demo.01.01-r-hello");
		List<String> removed = new ArrayList<>();

		String clean = ContentSanitizer.sanitize("""
				<p><a href="http://example.org/a">h</a> <a href="https://dev.java/">s</a>
				<a href="/lessons/demo.01.02">l</a> <a href="#qué-aprenderás">f</a> <a href="/courses/demo">c</a>
				<a href="/api/resources/demo.01.01-r-hello">Hello.java</a></p>
				""", IDS, hrefs, removed);

		assertThat(removed).isEmpty();
		assertThat(clean).contains("<a href=\"http://example.org/a\" target=\"_blank\" rel=\"noopener noreferrer\">h</a>",
				"<a href=\"https://dev.java/\" target=\"_blank\" rel=\"noopener noreferrer\">s</a>",
				"<a href=\"/lessons/demo.01.02\">l</a>", "<a href=\"#qué-aprenderás\">f</a>", "<a href=\"/courses/demo\">c</a>",
				"<a href=\"/api/resources/demo.01.01-r-hello\">Hello.java</a>");
	}

	@Test
	void aResourceLinkMayNameUnicodeLettersWhileEveryOtherFormStaysStrict() {
		String unicode = "/api/resources/java.12.16-r-presupuesto-año-2026txt-0979a0bb";
		List<String> bad = List.of("/api/resources/a/b", "/api/resources/a%2Fb", "/api/resources/a b",
				"/api/resources/año\u0000", "/lessons/demo.año", "/stages/demo.año", "/courses/demo.año");
		Set<String> hrefs = new java.util.HashSet<>(bad);
		hrefs.add(unicode);
		List<String> removed = new ArrayList<>();

		String clean = ContentSanitizer.sanitize("<p><a href=\"" + unicode + "\">presupuesto año 2026.txt</a></p>", IDS,
				hrefs, removed);

		assertThat(removed).isEmpty();
		assertThat(clean).contains("<a href=\"/api/resources/java.12.16-r-presupuesto-a").contains("presupuesto año 2026.txt</a>");
		for (String href : bad) {
			List<String> dropped = new ArrayList<>();
			ContentSanitizer.sanitize("<p><a href=\"" + href + "\">x</a></p>", IDS, hrefs, dropped);
			assertThat(dropped).as(href).isNotEmpty();
		}
	}

	@Test
	void unicodeResourceLinksRejectLookalikeSeparatorsInvisiblesAndAnythingOutsideTheSet() {
		String p = "/api/resources/";
		for (String href : List.of(p + "a／b", p + "a∕b", p + "a⁄b", p + "a‮b", p + "a​b", p + "a﻿b",
				p + "̃", p + "ñ", p + "a?b", p + "a#b", p + "a\\b", p + "a:b", p + "a\u0085", p + "a b", p)) {
			List<String> removed = new ArrayList<>();
			ContentSanitizer.sanitize("<p><a href=\"" + href + "\">x</a></p>", IDS, Set.of(href), removed);
			assertThat(removed).as(href).isNotEmpty();
		}
		String real = p + "java.12.16-r-presupuesto-año-2026txt-0979a0bb";
		List<String> removed = new ArrayList<>();
		String clean = ContentSanitizer.sanitize("<p><a href=\"" + real + "\">ok</a> <a href=\"" + p + "ajeno-ñ\">no</a></p>", IDS,
				Set.of(real), removed);
		assertThat(clean).isEqualTo("<p><a href=\"" + real + "\">ok</a> no</p>");
		assertThat(removed).containsExactly("<a>", "attribute [href] on <a>");
	}

	@Test
	void sourceTargetAndRelAreDiscardedAndReplacedByThePolicy() {
		List<String> removed = new ArrayList<>();

		String clean = sanitize("<p><a href=\"https://dev.java/\" target=\"_self\" rel=\"opener\">x</a> "
				+ "<a href=\"/lessons/demo.01.02\" target=\"_blank\">y</a></p>", removed);

		assertThat(removed).isNotEmpty();
		assertThat(clean).isEqualTo("<p><a href=\"https://dev.java/\" target=\"_blank\" rel=\"noopener noreferrer\">x</a> "
				+ "<a href=\"/lessons/demo.01.02\">y</a></p>");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"<script>alert(1)</script>",
			"<ScRiPt>alert(1)</sCrIpT>",
			"<script>alert(1)",
			"<img src=x onerror=alert(1)>",
			"<a href=\"javascript:alert(1)\">x</a>",
			"<a href=\"JaVaScRiPt:alert(1)\">x</a>",
			"<a href=\"&#106;avascript:alert(1)\">x</a>",
			"<a href=\"java&#x09;script:alert(1)\">x</a>",
			"<a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">x</a>",
			"<a href=\"vbscript:msgbox(1)\">x</a>",
			"<a href=\"//evil.example/\">x</a>",
			"<a href=\"/lessons/demo.01.02\" onclick=\"alert(1)\">x</a>",
			"<a href=\"/somewhere/else\">x</a>",
			"<h2 id=\"evil\">t</h2>",
			"<p id=\"qué-aprenderás\">t</p>",
			"<svg><script>alert(1)</script></svg>",
			"<svg onload=alert(1)>",
			"<iframe src=\"https://evil.example/\"></iframe>",
			"<style>body{display:none}</style>",
			"<p style=\"color:red\">t</p>",
			"<details ontoggle=\"alert(1)\" open><summary>s</summary></details>",
			"<DETAILS OnToggle=alert(1)>x</DETAILS>",
			"<form action=\"/x\"><input name=\"a\"></form>",
			"<object data=\"x\"></object><embed src=\"x\">",
			"<code class=\"language-x onmouseover\">x</code>",
			"<math><mi xlink:href=\"javascript:alert(1)\">x</mi></math>",
	})
	void removesExecutableOrUnknownMarkup(String malicious) {
		List<String> removed = new ArrayList<>();

		String clean = sanitize("<p>antes</p>" + malicious + "<p>después</p>", removed).toLowerCase();

		assertThat(removed).as(malicious).isNotEmpty();
		// An unclosed <script> swallows the rest of the input, so only the text before it must survive.
		assertThat(clean).contains("antes").doesNotContain("<script", "<img", "onerror", "onload", "onclick",
				"ontoggle", "onmouseover", "javascript", "data:", "vbscript", "//evil", "<svg", "<iframe", "<style",
				"style=", "<form", "<input", "<object", "<embed", "<math", "id=\"evil\"", "<p id", "target=", "opener\"",
				"/somewhere");
		if (!malicious.equals("<script>alert(1)")) assertThat(clean).contains("después");
	}

	@Test
	void escapedEntitiesStayText() {
		List<String> removed = new ArrayList<>();

		String clean = sanitize("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", removed);

		assertThat(removed).isEmpty();
		assertThat(clean).contains("&lt;script&gt;").doesNotContain("<script");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"<script>alert(1)</script>",
			"<img src=x onerror=alert(1)>",
			"<p>texto <a href=\"/lessons/demo.01.02\" onclick=\"alert(1)\">x</a></p>",
			"<h2 id=\"evil\">t</h2>",
			"<iframe src=\"https://evil.example/\"></iframe>",
			"<details ontoggle=\"alert(1)\">\n<summary>s</summary>\n</details>",
			"![imagen](https://evil.example/x.png)",
			"[dato](data:text/html,hola)",
			"[archivo](file:///etc/passwd)",
			"[vb](VBScript:msgbox(1))",
			// A dropped formatting element is inert only as written without attributes: a handler, an id or a style
			// it carried still fails, even though the element and the attribute both leave the output.
			"<span onclick=\"alert(1)\">x</span>",
			"Texto <span onmouseover=\"x()\">x</span> fin.",
			"<section onclick=\"x()\">x</section>",
			"<div id=\"evil\">x</div>",
			"<div style=\"background:url(https://e.invalid/x.png)\">x</div>",
	})
	void theLoaderRejectsALessonTheSanitizerWouldAlter(String malicious) {
		ContentFixture fixture = ContentFixture.copyInto(tempDir);
		fixture.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Texto de cuerpo.\n\n" + malicious + "\n");

		ContentValidationException ex = fixture.rejected("demo.01.01");

		assertThat(ex.getMessage()).containsAnyOf("sanitizer removed", "disallowed link", "disallowed image");
	}

	@Test
	void theLoaderNamesTheExercisePartWhereTheSanitizerRemovedSomething() {
		ContentFixture fixture = ContentFixture.copyInto(tempDir);
		// The event handler is what keeps failing the load, and the message still names the exercise part.
		fixture.replace(ContentFixture.INTRO, "Solución uno,", "Solución uno con <p onclick=\"x()\">nota</p>,");

		fixture.rejected("demo.01.01 demo.01.01.e01 solution: sanitizer removed attribute [onclick] on <p>");
	}

	@Test
	void inertFormattingRemovalsAreToleratedAndTheRenderedHtmlStaysSanitized() {
		ContentFixture fixture = ContentFixture.copyInto(tempDir);
		fixture.replace(ContentFixture.INTRO, "Solución uno,", "<span>Solución</span> <u>uno</u>,");

		ExerciseContent exercise = fixture.load().lessons().get("demo.01.01").exercises().get(0);

		assertThat(exercise.solutionHtml()).contains("Solución uno").doesNotContain("<span", "<u>");
	}

	@Test
	void anAttributeOnADroppedFormattingElementIsReportedWhileTheOutputStaysTheSame() {
		List<String> bare = new ArrayList<>();
		List<String> written = new ArrayList<>();

		String expected = sanitize("<p>a <span>x</span> <div>y</div></p>", bare);
		String clean = sanitize("<p>a <span onclick=\"alert(1)\">x</span> <div id=\"evil\" style=\"color:red\">y</div></p>",
				written);

		assertThat(clean).isEqualTo(expected);
		assertThat(bare).allSatisfy(removed -> assertThat(ContentSanitizer.inert(removed)).as(removed).isTrue());
		assertThat(written).contains("attribute [onclick] on <span>", "attribute [id, style] on <div>")
			.anySatisfy(removed -> assertThat(ContentSanitizer.inert(removed)).as(removed).isFalse());
	}

	@Test
	void onlyInertFormattingTagsAreTolerated() {
		assertThat(ContentSanitizer.inert("<span>")).isTrue();
		assertThat(ContentSanitizer.inert("<u>")).isTrue();
		assertThat(ContentSanitizer.inert("<div>")).isTrue();
		// A removed anchor is inert formatting: the element and its href are gone, its text stays, and no link
		// capability reaches the reader. Anything else an anchor loses, an event handler above all, keeps failing.
		assertThat(ContentSanitizer.inert("<a>")).isTrue();
		assertThat(ContentSanitizer.inert("attribute [href] on <a>")).isTrue();
		for (String removed : List.of("<script>", "<iframe>", "<frame>", "<img>", "<object>", "<style>",
				"<form>", "attribute [onclick] on <p>", "attribute [id] on <h2>", "attribute [style] on <p>", "<svg>",
				"attribute [href, onclick] on <a>", "attribute [onclick] on <a>", "attribute [name] on <a>",
				"attribute [target] on <a>", "attribute [id] on <a>", "<button>")) {
			assertThat(ContentSanitizer.inert(removed)).as(removed).isFalse();
		}
	}
}
