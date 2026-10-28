package dev.codeacademy.catalog;

import java.util.List;

/** The manifests contradict the stored catalog: a permanent ID disappeared or moved. Nothing was changed. */
public class CatalogConflictException extends RuntimeException {

	public CatalogConflictException(List<String> problems) {
		super("Catalog cannot be synchronized with the content, nothing was changed:\n- " + String.join("\n- ", problems));
	}
}
