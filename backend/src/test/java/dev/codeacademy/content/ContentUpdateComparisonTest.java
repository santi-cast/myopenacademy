package dev.codeacademy.content;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentUpdateComparisonTest {

	@Test
	void everyGitSourceProcessGetsTheSameCommandAndEnvironment() {
		ProcessBuilder builder = ContentUpdateComparison.gitProcess(Path.of("/repo"), "cat-file", "--batch");
		assertThat(builder.command()).isEqualTo(List.of(
			"git", "--no-replace-objects", "--no-pager", "-C", "/repo", "cat-file", "--batch"));
		assertThat(builder.redirectError()).isEqualTo(ProcessBuilder.Redirect.DISCARD);
		Map<String, String> git = new TreeMap<>();
		builder.environment().forEach((key, value) -> { if (key.startsWith("GIT_")) git.put(key, value); });
		assertThat(git).isEqualTo(Map.of(
			"GIT_LITERAL_PATHSPECS", "1", "GIT_CONFIG_NOSYSTEM", "1", "GIT_CONFIG_GLOBAL", "/dev/null"));
		System.getenv().forEach((key, value) -> {
			if (!key.startsWith("GIT_")) assertThat(builder.environment()).containsEntry(key, value);
		});
	}
}
