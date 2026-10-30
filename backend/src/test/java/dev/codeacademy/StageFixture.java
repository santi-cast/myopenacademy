package dev.codeacademy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.codeacademy.content.ContentFixture;
import dev.codeacademy.content.ImageFixture;
import dev.codeacademy.content.ZipFixture;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Synthetic controlled v2 project beside a v1 course without a project; no source repository access. */
final class StageFixture {

	static final String FIRST = "demo-stage-first";
	static final String PENDING = "demo-stage-pending";
	static final String LAST = "demo-stage-last";
	static final String IMAGE = "demo-stage-image";
	final ContentFixture content;

	StageFixture(Path temp) throws Exception {
		content = ContentFixture.copyInto(temp);
		content.addResource(IMAGE, "project/image.png", "image/png", ImageFixture.bytes("png"));
		ZipFixture.install(content);
		Files.writeString(content.root.resolve("demo/lessons/first-stage.md"), """
				# First stage

				~~Manual~~ ![diagram](image.png)

				[folder](../fase-01/codigo/hola/)

				## Ejercicios prácticos resueltos

				### Manual exercise

				No automatic evaluation.
				""");
		Files.writeString(content.root.resolve("demo/lessons/last-stage.md"), "# Last stage\n");
		content.editManifest(m -> {
			ArrayNode stages = (ArrayNode) m.get("projectStages");
			stage(stages, FIRST, "First stage", 1, "first", true);
			stage(stages, PENDING, "Pending stage", 2, "pending", false);
			stage(stages, LAST, "Last stage", 3, "last", true);
			ArrayNode links = (ArrayNode) m.get("sourceToId");
			for (String name : List.of("first", "pending", "last")) {
				links.addObject().put("sourcePath", "project/" + name + ".md").put("stageId", "demo-stage-" + name);
			}
			for (var resource : m.get("resources")) {
				String id = resource.get("resourceId").asString();
				if (List.of(IMAGE, ZipFixture.ZIP, "demo-nested", "demo-01.01-r-hello", "demo-01.01-r-other").contains(id)) {
					var node = (ObjectNode) resource;
					node.remove("lessonId");
					node.put("stageId", FIRST);
				}
			}
		});
		ZipFixture.refresh(content);
		Path other = content.root.resolve("other");
		Files.createDirectories(other);
		Files.writeString(other.resolve("manifest.json"), """
				{
				  "schemaVersion": 1,
				  "source": {"name": "Synthetic", "versionRef": "0123456789abcdef0123456789abcdef01234567"},
				  "course": {"id": "other", "namespace": "other", "title": "Other"},
				  "phases": [{"id": "other-phase", "title": "Other phase", "position": 1}],
				  "lessons": [{"id": "other-pending", "title": "Pending", "position": 1,
				    "phaseId": "other-phase", "sourcePath": "pending.md", "sourceAvailable": false,
				    "available": false, "bodyPath": null, "exercises": []}],
				  "resources": [],
				  "sourceToId": [{"sourcePath": "pending.md", "lessonId": "other-pending"}]
				}
				""");
	}

	static ObjectNode stage(ArrayNode stages, String id, String title, int position, String name, boolean available) {
		ObjectNode entry = stages.addObject().put("id", id).put("title", title).put("position", position)
			.put("sourcePath", "project/" + name + ".md").put("sourceAvailable", available).put("available", available);
		if (available) entry.put("bodyPath", "demo/lessons/" + name + "-stage.md");
		else entry.putNull("bodyPath");
		return entry;
	}
}
