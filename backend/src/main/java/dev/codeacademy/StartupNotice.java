package dev.codeacademy;

import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Once started (H10): where the academy is, and a warning when people reach it over the network without HTTPS,
 * because then passwords travel unencrypted.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class StartupNotice {

	private static final Logger log = LoggerFactory.getLogger(StartupNotice.class);

	static final String PLAIN_HTTP = "La academia se abre en la red sin HTTPS: las contraseñas viajan sin cifrar. "
		+ "Úsala solo en una red de confianza, como la de casa, y nunca así en internet (ver la guía de instalación).";

	private final String publicUrl;
	private final String name;

	StartupNotice(@Value("${code-academy.public-url:}") String publicUrl, @Value("${spring.application.name}") String name) {
		this.publicUrl = publicUrl;
		this.name = name;
	}

	@EventListener(ApplicationReadyEvent.class)
	void started() {
		if (!publicUrl.isBlank()) log.info("{}, en {}", name, publicUrl);
		if (plainHttpOnTheNetwork(publicUrl)) log.warn(PLAIN_HTTP);
	}

	/** An http:// address whose host is not this machine. */
	static boolean plainHttpOnTheNetwork(String publicUrl) {
		try {
			URI uri = URI.create(publicUrl.strip());
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			return "http".equalsIgnoreCase(uri.getScheme())
				&& !(host.equals("localhost") || host.equals("::1") || host.equals("[::1]") || host.startsWith("127."));
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}
}
