package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * H12, T15 (S17 and S19): the optional academia_minima of curso.json, with the cases of the kit 1.1.0
 * (test_formato_academia.py and the notes for the academy in its ESTADO.md). Each case is checked against a fixed
 * academy version, so the rule does not depend on the version this build happens to be.
 */
class AcademiaMinimaTest {

	static final JsonMapper JSON = JsonMapper.builder().build();
	static final String FORMAT = " must be MAJOR.MINOR.PATCH, for example 1.1.0";

	/** The problems of a minimal valid course with this academia_minima (a JSON literal), for academy 1.1.0. */
	static List<String> problems(String literal) throws Exception {
		ObjectNode curso = (ObjectNode) JSON.readTree("""
			{"formato": 1, "tipo": "curso", "id": "demo", "titulo": "Demo", "version": "1.0.0",
			 "fases": [{"id": "demo.f1", "carpeta": "fase-01-a", "titulo": "Uno",
			   "modulos": [{"id": "demo.m1", "fichero": "fase-01-a/01-a.md", "titulo": "A"}]}]}
			""");
		if (literal != null) curso.set("academia_minima", JSON.readTree(literal));
		List<String> problems = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		CourseDescriptor descriptor = CourseDescriptor.parse(JSON.writeValueAsBytes(curso), "1.1.0", problems, warnings);
		assertThat(warnings).isEmpty();
		assertThat(descriptor == null).isEqualTo(!problems.isEmpty());
		return problems;
	}

	@Test
	void withoutTheFieldNothingChanges() throws Exception {
		assertThat(problems(null)).isEmpty();
	}

	@Test
	void theValidVersionsUpToThisAcademyAreAccepted() throws Exception {
		for (String version : List.of("1.1.0", "0.9.0", "1.0.10", "1.0.0", "0.0.0")) {
			assertThat(problems('"' + version + '"')).as(version).isEmpty();
		}
	}

	@Test
	void aValidVersionAboveThisAcademyIsRefusedComparingEachNumberAsAnInteger() throws Exception {
		// 1.10.0 > 1.9.0 > 1.1.0: as numbers, not as text.
		for (String version : List.of("1.10.0", "10.0.0", "1.1.1", "2.0.0", "1.2.0", "99999999999999999999.0.0")) {
			assertThat(problems('"' + version + '"')).as(version)
				.containsExactly("curso.json: course requires academy " + version + " or later; this academy is 1.1.0");
		}
	}

	@Test
	void theKitsInvalidValuesAreFindingsShownAsTheKitShowsThem() throws Exception {
		// Shown as they are, unless they are not a text or would not be seen whole: then as in JSON.
		List<String[]> cases = List.of(
			new String[] {"\"1.1\"", "1.1"}, new String[] {"\"01.1.0\"", "01.1.0"}, new String[] {"\"1.01.0\"", "1.01.0"},
			new String[] {"\"1.1.00\"", "1.1.00"}, new String[] {"\"1.1.0-beta\"", "1.1.0-beta"},
			new String[] {"\"v1.1.0\"", "v1.1.0"}, new String[] {"\"1.1.0.0\"", "1.1.0.0"},
			new String[] {"\"١.١.٠\"", "١.١.٠"},
			new String[] {"\" 1.1.0\"", "\" 1.1.0\""}, new String[] {"\"1.1.0\\n\"", "\"1.1.0\\n\""},
			new String[] {"\"\"", "\"\""}, new String[] {"1", "1"}, new String[] {"null", "null"},
			new String[] {"1.1", "1.1"});
		for (String[] value : cases) {
			assertThat(problems(value[0])).as(value[0]).containsExactly("curso.json: academia_minima (" + value[1] + ")" + FORMAT);
		}
	}

	@Test
	void theVersionsCompareMajorThenMinorThenPatch() {
		assertThat(AcademyVersion.compare("1.10.0", "1.9.0")).isPositive();
		assertThat(AcademyVersion.compare("1.9.0", "1.10.0")).isNegative();
		assertThat(AcademyVersion.compare("1.1.0", "1.1.0")).isZero();
		assertThat(AcademyVersion.compare("2.0.0", "1.99.99")).isPositive();
		assertThat(AcademyVersion.compare("1.0.10", "1.0.9")).isPositive();
		assertThat(AcademyVersion.compare("99999999999999999999.0.0", "9.0.0")).isPositive();
	}

	@Test
	void theAcademyVersionComesFromThePomAndTheFrontendSaysTheSame() throws Exception {
		Matcher pom = Pattern.compile("</parent>\\s*<groupId>[^<]+</groupId>\\s*<artifactId>[^<]+</artifactId>\\s*<version>([^<]+)</version>")
			.matcher(Files.readString(Path.of("pom.xml")));
		assertThat(pom.find()).isTrue();
		assertThat(AcademyVersion.current()).isEqualTo(pom.group(1));
		assertThat(JSON.readTree(Files.readString(Path.of("../frontend/package.json"))).path("version").asString())
			.isEqualTo(AcademyVersion.current());
	}
}
