package dev.codeacademy;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * One origin and one port (H10): with {@code FRONTEND_DIR} set, the backend serves the compiled frontend itself. A file
 * that exists is served as it is; any other path of the application (/courses/inicial, /lessons/inicial.markdown) gets
 * index.html, so an identifier with a dot is never taken as a file extension. A missing file under /assets/ is a 404,
 * never the page. Without FRONTEND_DIR (development with Vite), nothing is served here.
 */
@Configuration
@ConditionalOnExpression("!'${code-academy.frontend-dir:}'.isBlank()")
class FrontendConfiguration implements WebMvcConfigurer {

	private final Path root;

	FrontendConfiguration(@Value("${code-academy.frontend-dir}") String frontendDir) {
		this.root = Path.of(frontendDir).toAbsolutePath().normalize();
	}

	/** The root has no resource path of its own: it is the application's entry, index.html. */
	@Override
	public void addViewControllers(org.springframework.web.servlet.config.annotation.ViewControllerRegistry registry) {
		registry.addViewController("/").setViewName("forward:/index.html");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations(new FileSystemResource(root.toString() + "/"))
			.resourceChain(false)
			.addResolver(new PathResourceResolver() {
				@Override
				protected Resource getResource(String resourcePath, Resource location) throws IOException {
					Resource found = super.getResource(resourcePath, location);
					if (found != null) return found;
					if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")
							|| resourcePath.startsWith("assets/")) return null;
					Resource index = location.createRelative("index.html");
					return index.exists() && index.isReadable() ? index : null;
				}
			});
	}
}
