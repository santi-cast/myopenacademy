package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * H12, T17 (S19; kit 1.1.0, FORMATO.md 7, and raiz_del_proyecto / es_carpeta_de_codigo of the kit's java.py): which
 * project a linked folder belongs to. A Gradle build without any settings.gradle* on the path is a project at its
 * outermost build.gradle*; with one, the build files do not count. Only the codigo/ that hangs directly from a phase
 * (fase-NN-*) or from proyecto/ counts; a folder named codigo further down is just a name.
 */
class CodeProjectsTest {

	static final String CODE = "fase-01-java/codigo/";

	static String root(String folder, String... files) {
		return CodeProjects.root(folder, Set.of(files)::contains);
	}

	@Test
	void aBuildGradleWithoutSettingsIsTheProject() {
		assertThat(root(CODE + "01-x/app/src/", CODE + "01-x/app/build.gradle.kts")).isEqualTo(CODE + "01-x/app/");
		assertThat(root(CODE + "01-x/app/src/", CODE + "01-x/app/build.gradle")).isEqualTo(CODE + "01-x/app/");
		// The linked folder may be the project folder itself.
		assertThat(root(CODE + "01-x/app/", CODE + "01-x/app/build.gradle")).isEqualTo(CODE + "01-x/app/");
	}

	@Test
	void withoutSettingsTheOutermostBuildGradleWins() {
		assertThat(root(CODE + "01-x/app/src/", CODE + "01-x/build.gradle.kts", CODE + "01-x/app/build.gradle.kts"))
			.isEqualTo(CODE + "01-x/");
	}

	@Test
	void withSettingsOnThePathTheBuildFilesDoNotCount() {
		assertThat(root(CODE + "01-x/app/src/", CODE + "01-x/settings.gradle.kts", CODE + "01-x/app/build.gradle.kts"))
			.isEqualTo(CODE + "01-x/");
		// A settings below an outer build.gradle: the outer build.gradle no longer counts, the settings does.
		assertThat(root(CODE + "01-x/app/src/", CODE + "01-x/build.gradle", CODE + "01-x/app/settings.gradle"))
			.isEqualTo(CODE + "01-x/app/");
		// A settings off the path (a sibling folder) does not take the build files away.
		assertThat(root(CODE + "01-x/app/src/", CODE + "01-x/otro/settings.gradle", CODE + "01-x/app/build.gradle"))
			.isEqualTo(CODE + "01-x/app/");
	}

	@Test
	void mavenAndGradleMixedGiveTheOutermostOfAll() {
		String examples = CODE + "02-x/ejemplos/";
		assertThat(root(examples + "2.1-tienda/src/", examples + "2.1-tienda/pom.xml", examples + "settings.gradle.kts"))
			.isEqualTo(examples);
		assertThat(root(examples + "2.1-tienda/src/", examples + "pom.xml", examples + "2.1-tienda/settings.gradle.kts"))
			.isEqualTo(examples);
		// A Gradle build without settings above a pom.xml is the outermost project.
		assertThat(root(examples + "2.1-tienda/src/", examples + "build.gradle", examples + "2.1-tienda/pom.xml"))
			.isEqualTo(examples);
	}

	@Test
	void codigoItselfNeverCountsNorAnythingAboveIt() {
		for (String file : new String[] {CODE + "build.gradle", CODE + "settings.gradle", CODE + "pom.xml",
				"fase-01-java/build.gradle", "build.gradle"}) {
			assertThat(root(CODE + "01-x/src/", file)).as(file).isNull();
		}
		// A settings.gradle in codigo/ itself is off the path, so it does not take a build.gradle below away.
		assertThat(root(CODE + "01-x/src/", CODE + "settings.gradle", CODE + "01-x/build.gradle")).isEqualTo(CODE + "01-x/");
	}

	@Test
	void aFolderNamedCodigoFurtherDownIsJustAName() {
		// The kit's case: a settings.gradle.kts in 01-x and a build.gradle.kts in 01-x/codigo/app.
		assertThat(root(CODE + "01-x/codigo/app/src/", CODE + "01-x/settings.gradle.kts",
			CODE + "01-x/codigo/app/build.gradle.kts")).isEqualTo(CODE + "01-x/");
		// A Java package called codigo inside a Maven project.
		assertThat(root(CODE + "01-x/src/main/java/es/codigo/", CODE + "01-x/pom.xml")).isEqualTo(CODE + "01-x/");
	}

	@Test
	void onlyTheCodigoOfAPhaseOrOfProyectoCounts() {
		assertThat(root("proyecto/codigo/etapa-01/src/", "proyecto/codigo/etapa-01/pom.xml"))
			.isEqualTo("proyecto/codigo/etapa-01/");
		assertThat(root("fase-01/codigo/x/src/", "fase-01/codigo/x/pom.xml")).isEqualTo("fase-01/codigo/x/");
		for (String folder : new String[] {"fase-01-java/ejemplos/codigo/x/", "otra/codigo/x/", "fase-1-java/codigo/x/",
				"fase-01java/codigo/x/", "a/fase-01-java/codigo/x/", "a/proyecto/codigo/x/", "codigo/x/"}) {
			assertThat(root(folder + "src/", folder + "pom.xml", folder + "build.gradle")).as(folder).isNull();
		}
	}

	@Test
	void outsideAProjectTheAnswerIsNull() {
		assertThat(root(CODE + "01-x/src/")).isNull();
		assertThat(root("fase-01-java/extra/src/", "fase-01-java/extra/pom.xml")).isNull();
	}
}
