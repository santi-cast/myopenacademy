package dev.codeacademy.content;

import java.nio.file.Path;

/** A registered resource: its checked file inside the content root and the original file name to show and serve. */
public record ResourceFile(String resourceId, Path localPath, String mediaType, String originalFileName) {
}
