package dev.codeacademy.content;

import java.util.List;

/** Available project content, deliberately separate from counted lessons and assessed exercises. */
public record StageContent(String stageId, String bodyHtml, List<String> resourceIds) {
}
