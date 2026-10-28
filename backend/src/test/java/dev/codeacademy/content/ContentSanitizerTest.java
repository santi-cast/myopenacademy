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
	private static final Set<String> HREFS = Set.of("/lessons/demo-01.02", "#qué-aprenderás", "https://dev.java/");

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
				<a href="/lessons/demo-01.02">l</a> <a href="#qué-aprenderás">f</a> <a href="https://dev.java/">w</a></p>
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
				"<sub>i</sub>", "<br />", "<a href=\"/lessons/demo-01.02\">l</a>", "<a href=\"#qué-aprenderás\">f</a>",
				"<a href=\"https://dev.java/\" rel=\"noopener noreferrer\">w</a>", "<ol start=\"2\">",
				"<th align=\"left\">", "<td align=\"right\">", "<code class=\"language-c&#43;&#43;\">",
				"<code class=\"language-mermaid\">A --&gt; B</code>", "<details>", "<summary>Ver solución</summary>",
				"<blockquote>", "<hr />");
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
			"<a href=\"/lessons/demo-01.02\" onclick=\"alert(1)\">x</a>",
			"<a href=\"/somewhere/else\">x</a>",
			"<a href=\"https://dev.java/\" target=\"_blank\" rel=\"opener\">x</a>",
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
			"<p>texto <a href=\"/lessons/demo-01.02\" onclick=\"alert(1)\">x</a></p>",
			"<h2 id=\"evil\">t</h2>",
			"<iframe src=\"https://evil.example/\"></iframe>",
			"<details ontoggle=\"alert(1)\">\n<summary>s</summary>\n</details>",
			"![imagen](https://evil.example/x.png)",
			"[dato](data:text/html,hola)",
			"[archivo](file:///etc/passwd)",
			"[vb](VBScript:msgbox(1))",
	})
	void theLoaderRejectsALessonTheSanitizerWouldAlter(String malicious) {
		ContentFixture fixture = ContentFixture.copyInto(tempDir);
		fixture.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Texto de cuerpo.\n\n" + malicious + "\n");

		ContentValidationException ex = fixture.rejected("demo-01.01");

		assertThat(ex.getMessage()).containsAnyOf("sanitizer removed", "disallowed link");
	}

	@Test
	void theLoaderNamesTheExercisePartWhereTheSanitizerRemovedSomething() {
		ContentFixture fixture = ContentFixture.copyInto(tempDir);
		fixture.replace(ContentFixture.INTRO, "Solución uno,", "<span onclick=\"x()\">Solución</span> uno,");

		fixture.rejected("demo-01.01 demo-01.01-e01 solution: sanitizer removed <span>");
	}
}
