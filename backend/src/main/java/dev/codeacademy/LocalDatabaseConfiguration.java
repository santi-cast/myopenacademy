package dev.codeacademy;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalDatabaseConfiguration {

	@Bean
	static BeanFactoryPostProcessor requireLocalDatabasePassword(Environment environment) {
		// Post-process the bean factory before any DataSource or migration singleton is created.
		return beanFactory -> {
			String password = environment.getProperty("DB_PASSWORD");
			if (password == null || password.isBlank()) {
				throw new IllegalStateException("DB_PASSWORD must be configured and nonblank.");
			}
		};
	}
}
