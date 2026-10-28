package dev.codeacademy.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/** Strict request bodies: an object with exactly the named string fields, no extras, nulls or coercion. */
public final class StrictJson {

	private StrictJson() {
	}

	public static List<String> strings(JsonNode body, String... fields) {
		if (body == null || !body.isObject()) throw ApiException.invalidRequest();
		Set<String> present = new HashSet<>(body.propertyNames());
		if (!present.equals(Set.of(fields))) throw ApiException.invalidRequest();
		return java.util.Arrays.stream(fields).map(field -> {
			JsonNode value = body.get(field);
			if (!value.isString()) throw ApiException.invalidRequest();
			return value.asString();
		}).toList();
	}
}
