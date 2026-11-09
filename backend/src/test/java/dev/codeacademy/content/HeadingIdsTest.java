package dev.codeacademy.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Heading IDs must match GitHub, as frozen from ancla() of the source course and of the kit (see the fixture header). */
class HeadingIdsTest {

	private static final Path FIXTURE = Path.of("src/test/resources/content/anchors-expected.tsv");
	/** The module of the kit's example course whose headings the fixture's «real» section freezes. */
	private static final Path REAL_LESSON = ExampleContent.COURSES.resolve("bueno/fase-01-primeros-pasos/01-ficheros-json.md");

	/** section → ordered (heading text, expected id) rows. */
	static Map<String, List<String[]>> fixture() throws IOException {
		Map<String, List<String[]>> sections = new LinkedHashMap<>();
		for (String line : Files.readAllLines(FIXTURE, StandardCharsets.UTF_8)) {
			if (line.startsWith("#") || line.isBlank()) continue;
			String[] columns = line.split("\t", -1);
			assertThat(columns).hasSize(3);
			sections.computeIfAbsent(columns[0], key -> new ArrayList<>()).add(new String[] { columns[1], columns[2] });
		}
		return sections;
	}

	static List<String> expectedIds(String section) throws IOException {
		return fixture().get(section).stream().map(row -> row[1]).toList();
	}

	@Test
	void syntheticHeadingsMatchTheFrozenAnchors() throws IOException {
		List<String[]> rows = fixture().get("synthetic");
		StringBuilder markdown = new StringBuilder();
		rows.forEach(row -> markdown.append("## ").append(row[0]).append("\n\n"));

		assertThat(HeadingIds.forMarkdown(markdown.toString()))
			.containsExactlyElementsOf(rows.stream().map(row -> row[1]).toList());
	}

	@Test
	void aTabInsideAHeadingIsDroppedLikeAncla() {
		// Frozen from ancla() at 0c2df357; kept out of the TSV, which cannot hold a literal tab.
		assertThat(HeadingIds.slug("Tab\tdentro")).isEqualTo("tabdentro");
	}

	@Test
	void everyHeadingOfTheRealLessonMatchesTheFrozenAnchorsInOrder() throws IOException {
		String lesson = Files.readString(REAL_LESSON, StandardCharsets.UTF_8);

		assertThat(HeadingIds.forMarkdown(lesson)).containsExactlyElementsOf(expectedIds("real"));
	}

	@Test
	void slugDropsMarksAndLowercasesSingletons() {
		assertThat(HeadingIds.slug("an\u0303o")).isEqualTo("ano");
		assertThat(HeadingIds.slug("\u212B")).isEqualTo("\u00E5");
		assertThat(Normalizer.isNormalized(HeadingIds.slug("\u1F71"), Normalizer.Form.NFC)).isFalse();
	}
}
