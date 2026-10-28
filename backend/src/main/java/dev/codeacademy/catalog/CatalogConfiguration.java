package dev.codeacademy.catalog;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the content and synchronizes the catalog when the web server starts; any invalid content or conflict stops
 * the startup. Console reset mode has no web server, so a broken content directory never blocks password recovery.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CatalogConfiguration {

	private static final Logger log = LoggerFactory.getLogger(CatalogConfiguration.class);

	@Bean
	Catalog catalog(@Value("${code-academy.content-dir}") String contentDir, CatalogSynchronizer synchronizer) {
		Catalog catalog = Catalog.load(Path.of(contentDir));
		synchronizer.synchronize(catalog.manifests());
		log.info("Catalog synchronized from {}: {} course(s)", contentDir, catalog.manifests().size());
		return catalog;
	}
}
