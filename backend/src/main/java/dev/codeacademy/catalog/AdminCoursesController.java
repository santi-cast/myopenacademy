package dev.codeacademy.catalog;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The installed courses in the administration (H9): only to see them, with their version, tag and commit. They are
 * installed from the console (content-import, content-update), never from the web.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AdminCoursesController {

	record InstalledCourse(String id, String title, String version, String tag, String commit, String repository) {
	}

	record InstalledCourses(List<InstalledCourse> courses) {
	}

	private final Catalog catalog;

	AdminCoursesController(Catalog catalog) {
		this.catalog = catalog;
	}

	@GetMapping(path = "/api/admin/courses", produces = MediaType.APPLICATION_JSON_VALUE)
	InstalledCourses courses() {
		return new InstalledCourses(catalog.courses().stream().map(course -> {
			var manifest = course.manifest();
			var source = manifest.source();
			return new InstalledCourse(manifest.course().id(), manifest.course().title(), source.courseVersion(),
				source.versionTag(), source.versionRef(), source.repository());
		}).toList());
	}
}
