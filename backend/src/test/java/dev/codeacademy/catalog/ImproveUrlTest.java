package dev.codeacademy.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** H8, T2: «Mejorar esta lección» edits the file in the course repository; only an https address gives a link. */
class ImproveUrlTest {

	@Test
	void editsTheFileOnMainWithEachSegmentEncoded() {
		assertThat(CatalogController.improveUrl("https://github.com/myopenacademy/curso-inicial",
				"fase-04-formatos-y-diagramas/02-markdown.md"))
			.isEqualTo("https://github.com/myopenacademy/curso-inicial/edit/main/fase-04-formatos-y-diagramas/02-markdown.md");
		assertThat(CatalogController.improveUrl("https://github.com/ana/mi-curso.git/", "fase 1/año ñ.md"))
			.isEqualTo("https://github.com/ana/mi-curso/edit/main/fase%201/a%C3%B1o%20%C3%B1.md");
	}

	@Test
	void anythingButAnHttpsAddressGivesNoLink() {
		for (String repository : new String[] { null, "", "http://github.com/a/b", "javascript:alert(1)", "git@github.com:a/b.git",
				"https://github.com/a/b\"onmouseover=x", "https://github.com/a b" }) {
			assertThat(CatalogController.improveUrl(repository, "a.md")).as(repository).isNull();
		}
	}
}
