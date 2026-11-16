package dev.codeacademy.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hostile snippets from the independent Part 2 verification, inserted into a copy of the example course's lesson body,
 * its first statement and its first solution: each is either rejected by validation or loads with no dangerous tag, attribute
 * or scheme between its markers and no repeated id.
 */
class MaliciousContentTest {

	private static final String LESSON = "ejemplo/lessons/" + ExampleContent.LESSON + ".md";
	private static final Pattern DANGER = Pattern.compile(
			"(?is)<script|<[^>]*\\son[a-z]+\\s*=|javascript:|vbscript:|data:|<svg|<math|<iframe|<object|<embed|<style|style=|<img|href=\"//|<!--|cdata|<form|<input|<base|<meta|<link|<template|<noscript|<textarea|<select|<button|xlink|<\\?|<!doctype|title=|id=\"evil\"");
	private static final Pattern ID = Pattern.compile("\\sid=\"([^\"]*)\"");
	/** Also inserted into the first statement and solution, besides the lesson body. */
	private static final List<String> IN_EXERCISES = List.of("script", "p-onclick", "md-javascript", "comment-block",
			"comment-inline", "md-title-plain", "fence-mermaid-onload", "cdata", "raw-h2-existing-id", "details-ontoggle",
			"md-image", "autolink-javascript", "refdef-javascript", "svg-onload", "raw-a-listed", "nested-unclosed");

	@TempDir
	Path tempDir;

	static Map<String, String> cases() {
		Map<String, String> c = new LinkedHashMap<>();
		c.put("script", "<script>alert(1)</script>");
		c.put("script-mixed-inline", "texto <ScRiPt>alert(1)</sCrIpT> fin");
		c.put("img-onerror", "<img src=x onerror=alert(1)>");
		c.put("p-onclick", "<p onclick=\"alert(1)\">x</p>");
		c.put("md-javascript", "[x](javascript:alert(1))");
		c.put("md-javascript-mixedcase", "[x](JaVaScRiPt:alert(1))");
		c.put("md-entity-dec-scheme", "[x](&#106;avascript:alert(1))");
		c.put("md-entity-hex-scheme", "[x](&#x6A;&#x61;vascript:alert(1))");
		c.put("md-percent-scheme", "[x](java%73cript:alert(1))");
		c.put("raw-tab-scheme", "<a href=\"java&#x09;script:alert(1)\">x</a>");
		c.put("md-data", "[x](data:text/html;base64,PHNjcmlwdD4=)");
		c.put("md-vbscript", "[x](vbscript:msgbox(1))");
		c.put("md-protocol-relative", "[x](//evil.example/)");
		c.put("md-backslash", "[x](/\\evil.example/)");
		c.put("md-https-noslash", "[x](https:evil.example)");
		c.put("md-file", "[x](file:///etc/passwd)");
		c.put("md-mailto-autolink", "<a@b.example>");
		c.put("autolink-javascript", "<javascript:alert(1)>");
		c.put("autolink-https", "<https://dev.java/>");
		c.put("md-UPPER-HTTPS", "[x](HTTPS://dev.java/)");
		c.put("raw-h2-evil-id", "<h2 id=\"evil\">t</h2>");
		c.put("raw-h2-existing-id", "<h2 id=\"3-errores-típicos\">Duplicado</h2>");
		c.put("raw-h2-no-id", "<h2>Encabezado crudo</h2>");
		c.put("svg-onload", "<svg onload=alert(1)><circle/></svg>");
		c.put("svg-script", "<svg><script>alert(1)</script></svg>");
		c.put("math-xlink", "<math><mi xlink:href=\"javascript:alert(1)\">x</mi></math>");
		c.put("iframe", "<iframe src=\"https://evil.example/\"></iframe>");
		c.put("object", "<object data=\"x.swf\"></object>");
		c.put("style-el", "<style>body{display:none}</style>");
		c.put("style-attr", "<p style=\"background:url(javascript:alert(1))\">x</p>");
		c.put("details-ontoggle", "<details ontoggle=\"alert(1)\" open><summary>s</summary>x</details>");
		c.put("details-open", "<details open><summary>s</summary>x</details>");
		c.put("summary-onclick", "<details><summary onclick=\"alert(1)\">s</summary>x</details>");
		c.put("nested-unclosed", "<details><summary>s<details><summary>t");
		c.put("split-script", "<scr<script>ipt>alert(1)</script>");
		c.put("unclosed-attr", "<a href=\"https://x.example/ onmouseover=alert(1)");
		c.put("md-title-quote", "[x](https://dev.java/ \"t\\\" onmouseover=\\\"alert(1)\")");
		c.put("md-title-plain", "[x](https://dev.java/ \"título\")");
		c.put("md-angle-dest-quote", "[x](<https://a.example/\" onmouseover=\"alert(1)>)");
		c.put("fence-mermaid-onload", "```mermaid\" onload=alert(1)\ngraph TD\n```");
		c.put("fence-info-javascript", "```javascript:alert(1)\nx\n```");
		c.put("fence-info-html", "```<img src=x onerror=alert(1)>\nx\n```");
		c.put("fence-Mermaid-upper", "```Mermaid\ngraph TD\n```");
		c.put("comment-block", "<!-- <script>alert(1)</script> -->");
		c.put("comment-inline", "texto <!-- oculto --> fin");
		c.put("cdata", "<![CDATA[<script>alert(1)</script>]]>");
		c.put("processing-instr", "<?php echo 1; ?>");
		c.put("doctype", "<!DOCTYPE html>");
		c.put("refdef-javascript", "[x][r]\n\n[r]: javascript:alert(1)");
		c.put("refdef-https-title", "[x][r2]\n\n[r2]: https://dev.java/ \"t\"");
		c.put("refdef-https", "[x][r3]\n\n[r3]: https://dev.java/");
		c.put("md-image", "![x](https://evil.example/x.png)");
		c.put("md-image-alt-quote", "![x\" onerror=\"alert(1)](https://evil.example/x.png)");
		c.put("raw-a-https-unlisted", "<a href=\"https://evil.example/\">x</a>");
		c.put("raw-a-listed-onclick", "[w](https://dev.java/) <a href=\"https://dev.java/\" onclick=\"alert(1)\">x</a>");
		c.put("raw-a-listed", "[w](https://evil.example/) <a href=\"https://evil.example/\">x</a>");
		c.put("base", "<base href=\"https://evil.example/\">");
		c.put("meta-refresh", "<meta http-equiv=\"refresh\" content=\"0;url=javascript:alert(1)\">");
		c.put("form", "<form action=\"https://evil.example\"><button>x</button></form>");
		c.put("escaped-text", "&lt;script&gt;alert(1)&lt;/script&gt;");
		c.put("heading-span-onclick", "## Título <span onclick=\"alert(1)\">x</span>");
		c.put("code-span-html", "`<script>alert(1)</script>`");
		c.put("table-span-style", "| a |\n|---|\n| <span style=\"x\">b</span> |");
		c.put("md-percent-quote", "[x](https://dev.java/%22onmouseover=alert(1))");
		c.put("noscript-mxss", "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\"></noscript>");
		c.put("textarea", "<textarea><script>alert(1)</script></textarea>");
		c.put("template", "<template><script>alert(1)</script></template>");
		c.put("select-style", "<select><style><img src=x onerror=alert(1)>");
		c.put("raw-internal-a", "<a href=\"/lessons/" + ExampleContent.LESSON + "#1-teoría\">x</a>");
		c.put("md-internal-fragment-ok", "[x](#1-teoría)");
		c.put("md-pending-with-fragment", "[x](02-ficheros-yaml.md#algo-inventado)");
		c.put("md-unknown-fragment", "[x](#no-existe)");
		c.put("md-unknown-file", "[x](no-existe.md)");
		c.put("md-traversal", "[x](../../../../etc/passwd)");
		c.put("md-query", "[x](02-ficheros-yaml.md?x=1)");
		return c;
	}

	static Stream<Arguments> placements() {
		List<Arguments> placements = new ArrayList<>();
		cases().forEach((name, snippet) -> {
			for (String where : List.of("body", "statement", "solution")) {
				if (where.equals("body") || IN_EXERCISES.contains(name)) placements.add(Arguments.of(name, where, snippet));
			}
		});
		return placements.stream();
	}

	@ParameterizedTest(name = "{0} in {1}")
	@MethodSource("placements")
	void isRejectedOrLoadsWithoutDanger(String name, String where, String snippet) throws IOException {
		Path root = ExampleContent.copyInto(tempDir.resolve("content"));
		Path lesson = root.resolve(LESSON);
		String markdown = Files.readString(lesson, StandardCharsets.UTF_8);
		String block = "\n\nMARKA\n\n" + snippet + "\n\nMARKB\n\n";
		markdown = switch (where) {
			case "body" -> markdown + block;
			case "statement" -> {
				int eol = markdown.indexOf('\n', markdown.indexOf("#### Ejercicio 1 · "));
				yield markdown.substring(0, eol) + block + markdown.substring(eol + 1);
			}
			default -> {
				int eol = markdown.indexOf('\n', markdown.indexOf("<summary>Ver solución</summary>"));
				yield markdown.substring(0, eol) + block + markdown.substring(eol + 1);
			}
		};
		Files.writeString(lesson, markdown, StandardCharsets.UTF_8);

		LessonContent loaded;
		try {
			loaded = ContentLoader.loadAll(root).stream()
				.filter(course -> course.lessons().containsKey(ExampleContent.LESSON)).findFirst().orElseThrow()
				.lessons().get(ExampleContent.LESSON);
		}
		catch (ContentValidationException rejected) {
			return;
		}
		String html = switch (where) {
			case "body" -> loaded.bodyHtml();
			case "statement" -> loaded.exercises().get(0).statementHtml();
			default -> loaded.exercises().get(0).solutionHtml();
		};
		int start = html.indexOf("MARKA");
		int end = html.indexOf("MARKB");
		String fragment = start >= 0 && end > start ? html.substring(start, end + 5) : html;
		Matcher danger = DANGER.matcher(fragment);
		assertThat(danger.find() ? danger.group() : null).as(fragment).isNull();

		List<String> fields = new ArrayList<>(List.of(loaded.bodyHtml()));
		loaded.exercises().forEach(exercise -> fields.addAll(List.of(exercise.statementHtml(), exercise.solutionHtml())));
		Set<String> ids = new HashSet<>();
		for (String field : fields) {
			Matcher id = ID.matcher(field);
			while (id.find()) assertThat(ids.add(id.group(1))).as("repeated id " + id.group(1)).isTrue();
		}
	}
}
