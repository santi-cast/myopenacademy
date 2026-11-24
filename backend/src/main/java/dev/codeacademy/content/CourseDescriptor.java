package dev.codeacademy.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
		List<String> recommended, List<Phase> phases, Project project, List<Part> parts) {

	static final String PATH = "curso.json";
	static final String INDEX = "00-indice.md";
	static final String GLOSSARY = "glosario.md";

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern COURSE_ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
	private static final Pattern PLAIN_ID = Pattern.compile("[a-z0-9]+([.-][a-z0-9]+)*");
	private static final Set<String> PART_FIELDS = Set.of("id", "titulo", "fases");
	/**
	 * Markdown in a title (FORMATO.md 3.2, «Los títulos son texto plano»): the same patterns, in the same order, as
	 * FORMATO_EN_TITULO of the kit's formato_academia.py; {@code \w} is Unicode there, as here.
	 */
	private static final Map<Pattern, String> MARKDOWN_IN_TITLE = markdownInTitle();

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

	/** One named stretch of the course (FORMATO.md 3.2, {@code partes}): its phase IDs in the order it lists them. */
	record Part(String id, String title, List<String> phases) {
	}

	/** Parses and checks a descriptor; every problem is added to {@code problems} and the result is then null. */
	static CourseDescriptor parse(byte[] bytes, List<String> problems, List<String> warnings) {
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
					if (moduleId != null && exerciseId != null && !exerciseId.startsWith(moduleId + ".")) {
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
		List<Part> parts = parts(root, phases, ids, found, warnings);
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
			List.copyOf(recommended), List.copyOf(phases), project, parts);
	}

	/**
	 * The optional {@code partes} (FORMATO.md 3.2), checked case by case as {@code comprobar_partes} of the kit: each
	 * parte is an object with an {@code id} like any other of the course, a plain-text {@code titulo} and the list of
	 * its {@code fases}; each fase exists and is in one parte only, and with partes every fase is in one. An unknown
	 * field is a warning. Then each parte is a stretch, as {@code comprobar_tramos_de_partes} of the kit (revision 1.1).
	 */
	private static List<Part> parts(JsonNode root, List<Phase> phases, Ids ids, List<String> found,
			List<String> warnings) {
		if (!root.has("partes")) return List.of();
		Set<String> phaseIds = new HashSet<>();
		for (Phase phase : phases) phaseIds.add(phase.id());
		Map<String, String> seen = new HashMap<>();
		List<Part> parts = new ArrayList<>();
		List<Stretch> stretches = new ArrayList<>();
		JsonNode list = root.get("partes");
		if (!list.isArray()) found.add(PATH + ": partes must be a list");
		int position = 0;
		for (JsonNode node : list.isArray() ? list : List.<JsonNode>of()) {
			position++;
			if (!node.isObject()) {
				found.add(PATH + ": each parte must be an object");
				continue;
			}
			String id = ids.add(text(node, "id"), "parte");
			String where = "parte " + (id != null ? id : "#" + position);
			for (String field : new TreeSet<>(node.propertyNames())) {
				if (!PART_FIELDS.contains(field)) warnings.add(PATH + ": " + where + ": unknown field «" + field + "» (ignored)");
			}
			String title = text(node, "titulo");
			if (!node.has("titulo")) found.add(PATH + ": missing titulo in " + where);
			else if (title == null) found.add(PATH + ": titulo must be a non-empty text in " + where);
			else {
				String markdown = markdown(title);
				if (markdown != null) {
					found.add(PATH + ": " + where + ": titulo «" + title + "» has Markdown formatting (" + markdown
						+ "); titles are plain text");
				}
			}
			List<String> listed = new ArrayList<>();
			List<String> named = new ArrayList<>();
			JsonNode fases = node.get("fases");
			if (fases == null) found.add(PATH + ": missing fases in " + where);
			else if (!fases.isArray()) found.add(PATH + ": fases must be a list in " + where);
			else {
				for (JsonNode fase : fases) {
					String phaseId = fase.isString() ? fase.asString() : fase.toString();
					if (fase.isString()) named.add(phaseId);
					if (!fase.isString() || !phaseIds.contains(phaseId)) {
						found.add(PATH + ": " + where + ": fase " + phaseId + " does not exist");
					}
					else if (seen.containsKey(phaseId)) {
						found.add(PATH + ": " + where + ": fase " + phaseId + " is already in parte " + seen.get(phaseId)
							+ " (each fase goes in only one)");
					}
					else {
						seen.put(phaseId, id != null ? id : "#" + position);
						listed.add(phaseId);
					}
				}
				stretches.add(new Stretch(id != null ? id : "#" + position, where, fases.isEmpty(), named));
			}
			parts.add(new Part(id, title, List.copyOf(listed)));
		}
		for (Phase phase : phases) {
			if (phase.id() != null && !seen.containsKey(phase.id())) {
				found.add(PATH + ": fase " + phase.id() + " is in no parte (with partes, each fase goes in one)");
			}
		}
		stretches(stretches, phases, seen, found);
		return List.copyOf(parts);
	}

	/** A parte with a list of fases, for the stretch rules: its owner key in {@code seen} and the fases it names. */
	private record Stretch(String key, String where, boolean empty, List<String> named) {
	}

	/**
	 * Each parte is a stretch of consecutive fases in the course order, and the partes follow each other (FORMATO.md
	 * 3.2, revision 1.1), as {@code comprobar_tramos_de_partes} of the kit: a parte is seen only with the fases that
	 * exist and are its own (the others already have their finding); an out-of-order parte is not also checked for
	 * gaps; the order of the partes compares the first fase of each with that of the previous parte with fases.
	 */
	private static void stretches(List<Stretch> stretches, List<Phase> phases, Map<String, String> seen,
			List<String> found) {
		List<String> order = new ArrayList<>();
		Map<String, Integer> position = new HashMap<>();
		for (Phase phase : phases) {
			if (phase.id() != null) position.put(phase.id(), order.size());
			order.add(phase.id());
		}
		String previous = null;
		int previousFirst = -1;
		for (Stretch stretch : stretches) {
			if (stretch.empty()) {
				found.add(PATH + ": " + stretch.where() + " has no fases (each parte groups at least one)");
				continue;
			}
			List<String> own = stretch.named().stream()
				.filter(f -> position.containsKey(f) && stretch.key().equals(seen.get(f))).toList();
			if (own.isEmpty()) continue;
			int disorder = -1;
			for (int i = 1; i < own.size() && disorder < 0; i++) {
				if (position.get(own.get(i)) < position.get(own.get(i - 1))) disorder = i;
			}
			if (disorder > 0) {
				found.add(PATH + ": " + stretch.where() + ": its fases are not in the course order (fase "
					+ own.get(disorder - 1) + " is listed before fase " + own.get(disorder) + ")");
			}
			else {
				for (int i = 1; i < own.size(); i++) {
					int before = position.get(own.get(i - 1));
					if (position.get(own.get(i)) != before + 1) {
						String between = order.get(before + 1);
						String of = seen.containsKey(between) ? ", of parte " + seen.get(between) : "";
						found.add(PATH + ": " + stretch.where() + ": its fases are not consecutive (between fase "
							+ own.get(i - 1) + " and fase " + own.get(i) + " is fase " + between + of + ")");
						break;
					}
				}
			}
			int first = own.stream().mapToInt(position::get).min().getAsInt();
			if (previous != null && first < previousFirst) {
				found.add(PATH + ": parte " + previous + " comes before " + stretch.where()
					+ " in partes, but its fases come after");
			}
			previous = stretch.key();
			previousFirst = first;
		}
	}

	/** What Markdown formatting a title carries, or null for plain text (formato_en_titulo of the kit). */
	static String markdown(String title) {
		for (Map.Entry<Pattern, String> entry : MARKDOWN_IN_TITLE.entrySet()) {
			if (entry.getKey().matcher(title).find()) return entry.getValue();
		}
		return null;
	}

	private static Map<Pattern, String> markdownInTitle() {
		Map<Pattern, String> patterns = new java.util.LinkedHashMap<>();
		patterns.put(Pattern.compile("`"), "code in backticks");
		patterns.put(Pattern.compile("\\*"), "bold or italics with *");
		patterns.put(Pattern.compile("__|(^|[^\\w])_[^_\\s]([^_]*[^_\\s])?_($|[^\\w])", Pattern.UNICODE_CHARACTER_CLASS),
			"bold or italics with _");
		patterns.put(Pattern.compile("~~"), "strikethrough");
		patterns.put(Pattern.compile("\\[[^\\]]*\\]\\([^)]*\\)"), "a link");
		patterns.put(Pattern.compile("<[a-zA-Z/][^>]*>"), "HTML");
		return java.util.Collections.unmodifiableMap(patterns);
	}

	/** Stages of an {@code etapas} project, or none. */
	List<Stage> stages() {
		return project == null ? List.of() : project.stages();
	}

	/**
	 * Every identifier is unique in the course and carries the course prefix: {@code <id>.<name>} (FORMATO.md 3.3).
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
			if (course != null && !id.startsWith(course + ".")) {
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
