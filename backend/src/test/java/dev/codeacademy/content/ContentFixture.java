package dev.codeacademy.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A writable copy of the synthetic valid course in a temporary directory, to break one rule per test. */
public final class ContentFixture {

	static final Path VALID = Path.of("src/test/resources/content/valid");
	static final String MANIFEST = "demo/manifest.json";
	public static final String INTRO = "demo/lessons/demo.01.01.md";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	public final Path root;

	private ContentFixture(Path root) {
		this.root = root;
	}

	public static ContentFixture copyInto(Path tempDir) {
		Path root = tempDir.resolve("content");
		try (Stream<Path> files = Files.walk(VALID)) {
			for (Path source : files.toList()) {
				Path target = root.resolve(VALID.relativize(source).toString());
				if (Files.isDirectory(source)) Files.createDirectories(target);
				else Files.copy(source, target);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return new ContentFixture(root);
	}

	public void editManifest(Consumer<ObjectNode> edit) {
		ObjectNode manifest = (ObjectNode) JSON.readTree(read(MANIFEST));
		edit.accept(manifest);
		write(MANIFEST, JSON.writeValueAsString(manifest));
	}

	public static ObjectNode lesson(ObjectNode manifest, String id) {
		return (ObjectNode) find(manifest.get("lessons"), "id", id);
	}

	public static ObjectNode phase(ObjectNode manifest, String id) {
		return (ObjectNode) find(manifest.get("phases"), "id", id);
	}

	static ObjectNode resource(ObjectNode manifest, String id) {
		return (ObjectNode) find(manifest.get("resources"), "resourceId", id);
	}

	public static ObjectNode link(ObjectNode manifest, String sourcePath) {
		return (ObjectNode) find(manifest.get("sourceToId"), "sourcePath", sourcePath);
	}

	private static JsonNode find(JsonNode array, String field, String value) {
		for (JsonNode node : array) {
			if (value.equals(node.get(field).asString())) return node;
		}
		throw new IllegalArgumentException(value);
	}

	public void replace(String relativePath, String oldText, String newText) {
		String text = read(relativePath);
		assertThat(text).contains(oldText);
		write(relativePath, text.replace(oldText, newText));
	}

	String read(String relativePath) {
		try {
			return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	void write(String relativePath, String text) {
		try {
			Files.writeString(root.resolve(relativePath), text, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Registers a synthetic file and its source mapping together, using only the temporary controlled copy. */
	public void addResource(String id, String sourcePath, String mediaType, byte[] bytes) {
		String localPath = "demo/resources/" + id;
		try {
			Files.write(root.resolve(localPath), bytes);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		editManifest(m -> {
			var resource = ((tools.jackson.databind.node.ArrayNode) m.get("resources")).addObject();
			resource.put("resourceId", id).put("lessonId", "demo.01.01").put("sourcePath", sourcePath)
				.put("localPath", localPath).put("mediaType", mediaType);
			((tools.jackson.databind.node.ArrayNode) m.get("sourceToId")).addObject()
				.put("sourcePath", sourcePath).put("resourceId", id);
		});
	}

	public CourseContent load() {
		return ContentLoader.load(root, MANIFEST);
	}

	/** Loading fails as a whole, and the message names the broken rule. */
	ContentValidationException rejected(String expectedMessagePart) {
		ContentValidationException ex = assertThrows(ContentValidationException.class, this::load);
		assertThat(ex.getMessage()).contains(expectedMessagePart);
		return ex;
	}
}
