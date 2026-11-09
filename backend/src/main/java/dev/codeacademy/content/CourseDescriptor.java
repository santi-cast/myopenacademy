package dev.codeacademy.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A course's {@code curso.json} (FORMATO.md, section 3): the course's own identity and structure, read from the pinned
 * commit. The academy never invents an identifier: phases, modules, exercises and stages keep the IDs the course
 * declares. Only what the academy needs to build its manifest is checked here; the kit checks the whole format.
 */
record CourseDescriptor(int format, String id, String title, String description, String version, String repository,
		List<String> recommended, List<Phase> phases, Project project) {

	static final String PATH = "curso.json";
	static final String INDEX = "00-indice.md";
	static final String GLOSSARY = "glosario.md";

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern COURSE_ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
	private static final Pattern PLAIN_ID = Pattern.compile("[a-z0-9]+([.-][a-z0-9]+)*");

	record Phase(String id, String folder, String title, String challenges, boolean challengesAvailable,
			List<Module> modules) {
	}

	record Module(String id, String file, String title, boolean available, List<Exercise> exercises) {
	}

	record Exercise(String id, String title) {
	}

	/** The portfolio project: {@code etapas} with its stages, or {@code modulo} naming one module of the course. */
	record Project(String type, String title, String module, List<Stage> stages) {
	}

	record Stage(String id, String file, String title, boolean available) {
	}

	/** Parses and checks a descriptor; every problem is added to {@code problems} and the result is then null. */
	static CourseDescriptor parse(byte[] bytes, List<String> problems) {
		JsonNode root;
		try {
			root = JSON.readTree(bytes);
		}
		catch (JacksonException ex) {
			problems.add(PATH + " is not valid JSON");
			return null;
		}
		if (root == null || !root.isObject()) {
			problems.add(PATH + " must be a JSON object");
			return null;
		}
		List<String> found = new ArrayList<>();
		int format = root.path("formato").asInt(0);
		if (format != 1) found.add(PATH + ": formato " + root.path("formato") + " is not supported (expected 1)");
		if (!"curso".equals(text(root, "tipo"))) found.add(PATH + ": tipo must be \"curso\"");
		String id = text(root, "id");
		if (id == null || !COURSE_ID.matcher(id).matches()) {
			found.add(PATH + ": id must be lower-case letters, digits and hyphens: " + id);
			id = null;
		}
		String title = required(root, "titulo", found);
		String version = required(root, "version", found);
		Ids ids = new Ids(id, found);
		List<Phase> phases = new ArrayList<>();
		Set<String> files = new HashSet<>();
		for (JsonNode node : array(root, "fases", found)) {
			String phaseId = ids.add(text(node, "id"), "fase");
			String folder = required(node, "carpeta", found);
			List<Module> modules = new ArrayList<>();
			for (JsonNode module : array(node, "modulos", found)) {
				String moduleId = ids.add(text(module, "id"), "modulo");
				String file = required(module, "fichero", found);
				if (file != null && !files.add(file)) found.add(PATH + ": fichero " + file + " is declared twice");
				boolean available = module.path("disponible").asBoolean(true);
				List<Exercise> exercises = new ArrayList<>();
				for (JsonNode exercise : module.path("ejercicios")) {
					String exerciseId = ids.add(text(exercise, "id"), "ejercicio");
					if (moduleId != null && exerciseId != null && !exerciseId.startsWith(moduleId + ".")
							&& !exerciseId.startsWith(moduleId + "-")) {
						found.add(PATH + ": ejercicio " + exerciseId + " does not belong to " + moduleId);
					}
					exercises.add(new Exercise(exerciseId, required(exercise, "titulo", found)));
				}
				if (!available && !exercises.isEmpty()) found.add(PATH + ": pending module " + moduleId + " has exercises");
				modules.add(new Module(moduleId, file, required(module, "titulo", found), available, List.copyOf(exercises)));
			}
			if (modules.isEmpty()) found.add(PATH + ": fase " + phaseId + " has no modules");
			phases.add(new Phase(phaseId, folder, required(node, "titulo", found), text(node, "retos"),
				node.path("retos_disponibles").asBoolean(true), List.copyOf(modules)));
		}
		if (phases.isEmpty()) found.add(PATH + ": fases must list at least one phase");
		Project project = null;
		JsonNode projectNode = root.path("proyecto");
		if (projectNode.isObject()) {
			String type = text(projectNode, "tipo");
			List<Stage> stages = new ArrayList<>();
			if ("etapas".equals(type)) {
				for (JsonNode stage : array(projectNode, "etapas", found)) {
					String stageId = ids.add(text(stage, "id"), "etapa");
					String file = required(stage, "fichero", found);
					if (file != null && !files.add(file)) found.add(PATH + ": fichero " + file + " is declared twice");
					stages.add(new Stage(stageId, file, required(stage, "titulo", found),
						stage.path("disponible").asBoolean(true)));
				}
			}
			else if (!"modulo".equals(type)) found.add(PATH + ": proyecto.tipo must be \"etapas\" or \"modulo\"");
			project = new Project(type, text(projectNode, "titulo"), text(projectNode, "modulo"), List.copyOf(stages));
		}
		List<String> recommended = new ArrayList<>();
		root.path("conviene_saber_antes").forEach(node -> recommended.add(node.asString()));
		if (!found.isEmpty()) {
			problems.addAll(found);
			return null;
		}
		return new CourseDescriptor(format, id, title, text(root, "descripcion"), version, text(root, "repositorio"),
			List.copyOf(recommended), List.copyOf(phases), project);
	}

	/** Stages of an {@code etapas} project, or none. */
	List<Stage> stages() {
		return project == null ? List.of() : project.stages();
	}

	/**
	 * Every identifier is unique in the course and carries the course prefix: {@code <id>.<name>} (FORMATO.md 3.3).
	 * The legacy {@code <id>-<name>} form is accepted only while the academy still holds content imported before
	 * curso.json (H7, task T2 removes it).
	 */
	private static final class Ids {
		private final String course;
		private final List<String> problems;
		private final Set<String> seen = new HashSet<>();

		Ids(String course, List<String> problems) {
			this.course = course;
			this.problems = problems;
		}

		String add(String id, String what) {
			if (id == null || !PLAIN_ID.matcher(id).matches()) {
				problems.add(PATH + ": " + what + " id must be lower-case letters, digits, hyphens and dots: " + id);
				return id;
			}
			if (course != null && !id.startsWith(course + ".") && !id.startsWith(course + "-")) {
				problems.add(PATH + ": " + what + " id " + id + " does not start with " + course + ".");
			}
			if (!seen.add(id)) problems.add(PATH + ": id " + id + " is declared twice");
			return id;
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || !value.isString() || value.asString().isBlank() ? null : value.asString();
	}

	private static String required(JsonNode node, String field, List<String> problems) {
		String value = text(node, field);
		if (value == null) problems.add(PATH + ": missing " + field + (node.has("id") ? " in " + node.get("id").asString() : ""));
		return value;
	}

	private static Iterable<JsonNode> array(JsonNode node, String field, List<String> problems) {
		JsonNode value = node.get(field);
		if (value == null || !value.isArray()) {
			problems.add(PATH + ": " + field + " must be a list" + (node.has("id") ? " in " + node.get("id").asString() : ""));
			return List.of();
		}
		return value;
	}
}
