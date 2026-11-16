package dev.codeacademy.content;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/** Real, tiny image bytes, registered through the same temporary manifest helper as other synthetic resources. */
public final class ImageFixture {

	private ImageFixture() {
	}

	public static byte[] bytes(String type) {
		if (type.equals("svg")) {
			return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"><rect width=\"1\" height=\"1\"/></svg>"
				.getBytes(StandardCharsets.UTF_8);
		}
		try (var output = new ByteArrayOutputStream()) {
			if (!ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), type, output)) {
				throw new IllegalArgumentException("No image writer for " + type);
			}
			return output.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static void register(ContentFixture fixture, String type) {
		fixture.addResource("demo.image-" + type, "fase-01/images/diagram." + type,
				type.equals("svg") ? "image/svg+xml" : "image/" + type, bytes(type));
	}
}
