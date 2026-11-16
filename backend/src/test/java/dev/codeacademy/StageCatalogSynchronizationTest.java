package dev.codeacademy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import dev.codeacademy.catalog.Catalog;
import dev.codeacademy.catalog.CatalogConflictException;
import dev.codeacademy.catalog.CatalogSynchronizer;
import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Stage;

import static dev.codeacademy.ProgressTestSupport.*;
import static dev.codeacademy.StageFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synchronization uses the existing transaction; public two-user progress is the preservation oracle. */
class StageCatalogSynchronizationTest extends StageTestSupport {

	@Test
	void stageCatalogPersistsAddsAndReordersIdempotentlyButRejectsIdentityConflictsAndWriteFailures() throws Exception {
		var synchronizer = context.getBean(CatalogSynchronizer.class);
		var first = learner();
		var second = learner();
		for (ApiClient browser : List.of(first, second)) {
			ok(lessonState(browser, INTRO, "completed"));
			ok(exerciseState(browser, E01, "solved"));
			ok(continuation(browser, "demo", NEXT));
		}
		ok(mark(first, FIRST, "completed"));
		ok(mark(first, LAST, "not_completed"));
		ok(open(first, "demo", LAST));
		ok(mark(second, FIRST, "not_completed"));
		ok(mark(second, LAST, "completed"));
		ok(open(second, "demo", FIRST));
		var manifests = Catalog.load(fixture.content.root).manifests();
		var before = TestDatabase.snapshot(jdbc);
		var firstBefore = personal(first);
		var secondBefore = personal(second);
		synchronizer.synchronize(manifests);
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		assertThat(personal(first)).isEqualTo(firstBefore);
		assertThat(personal(second)).isEqualTo(secondBefore);

		Manifest demo = manifests.stream().filter(m -> m.course().id().equals("demo")).findFirst().orElseThrow();
		Manifest other = manifests.stream().filter(m -> m.course().id().equals("other")).findFirst().orElseThrow();
		var stages = new ArrayList<>(demo.projectStages());
		stages.add(new Stage("demo.stage-added", "Added", 4, "project/added.md", false, false, null));
		Manifest added = withStages(demo, stages);
		synchronizer.synchronize(List.of(added, other));
		for (ApiClient browser : List.of(first, second)) {
			var body = ok(stageProgress(browser, "demo"));
			stageCounts(body.path("progress"), 1, 2, 4);
			assertThat(body.path("stageStates")).isEqualTo((browser == first ? firstBefore : secondBefore).get(0).path("stageStates"));
			assertThat(body.path("continuation")).isEqualTo((browser == first ? firstBefore : secondBefore).get(0).path("continuation"));
		}
		assertThat(ok(progress(first, "demo"))).isEqualTo(firstBefore.get(2));
		assertThat(ok(progress(second, "demo"))).isEqualTo(secondBefore.get(2));
		before = TestDatabase.snapshot(jdbc);
		synchronizer.synchronize(List.of(added, other));
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);

		// Position swaps must be deferred until commit, without moving saved state to another identity.
		Manifest reordered = mapStages(added, s -> new Stage(s.id(), s.title(), s.id().equals(FIRST) ? 3
			: s.id().equals(LAST) ? 1 : s.position(), s.sourcePath(), s.sourceAvailable(), s.available(), s.bodyPath()));
		synchronizer.synchronize(List.of(reordered, other));
		for (ApiClient browser : List.of(first, second)) {
			var saved = ok(stageProgress(browser, "demo"));
			var original = (browser == first ? firstBefore : secondBefore).get(0);
			assertThat(saved.path("stageStates").get(0)).isEqualTo(original.path("stageStates").get(1));
			assertThat(saved.path("stageStates").get(1)).isEqualTo(original.path("stageStates").get(0));
			assertThat(saved.path("continuation")).isEqualTo(original.path("continuation"));
			stageCounts(saved.path("progress"), 1, 2, 4);
		}
		firstBefore = personal(first);
		secondBefore = personal(second);
		before = TestDatabase.snapshot(jdbc);
		Manifest removed = withStages(reordered, reordered.projectStages().stream().filter(s -> !s.id().equals(FIRST)).toList());
		Manifest renamed = mapStages(reordered, s -> s.id().equals(FIRST)
			? new Stage("demo.stage-renamed", s.title(), s.position(), s.sourcePath(), s.sourceAvailable(), s.available(), s.bodyPath()) : s);
		Manifest moved = withStages(other, List.of(reordered.projectStages().stream().filter(s -> s.id().equals(FIRST)).findFirst().orElseThrow()));
		Manifest duplicatePosition = mapStages(reordered, s -> new Stage(s.id(), s.title(), 1, s.sourcePath(),
			s.sourceAvailable(), s.available(), s.bodyPath()));
		Manifest zeroPosition = mapStages(reordered, s -> new Stage(s.id(), s.title(), 0, s.sourcePath(),
			s.sourceAvailable(), s.available(), s.bodyPath()));
		Manifest invalidAvailability = mapStages(reordered, s -> new Stage(s.id(), s.title(), s.position(), s.sourcePath(),
			false, true, s.bodyPath()));
		Manifest crossedId = mapStages(reordered, s -> s.id().equals(FIRST)
			? new Stage(INTRO, s.title(), s.position(), s.sourcePath(), s.sourceAvailable(), s.available(), s.bodyPath()) : s);
		for (List<Manifest> conflict : List.of(List.of(removed, other), List.of(renamed, other),
			List.of(removed, moved), List.of(duplicatePosition, other),
			List.of(zeroPosition, other), List.of(invalidAvailability, other), List.of(crossedId, other))) {
			assertThatThrownBy(() -> synchronizer.synchronize(conflict)).isInstanceOf(CatalogConflictException.class);
			assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
			assertThat(personal(first)).isEqualTo(firstBefore);
			assertThat(personal(second)).isEqualTo(secondBefore);
		}
		// Invalid positions are rejected before writes; inject a real database failure to prove rollback after writes.
		Manifest changed = mapStages(reordered, s -> new Stage(s.id(), s.title(), s.position(), s.sourcePath(),
			s.sourceAvailable(), s.id().equals(FIRST) ? false : s.available(), s.bodyPath()));
		Manifest failedUpdate = new Manifest(changed.schemaVersion(), changed.source(),
			new Manifest.Course("demo", "demo", "Must roll back"), changed.phases(), changed.lessons(), changed.resources(),
			changed.sourceToId(), changed.projectStages(), changed.provenance());
		try {
			jdbc.execute("""
					create function code_academy.stage_sync_fail() returns trigger language plpgsql
					as $$ begin raise exception 'injected catalog failure'; end $$
					""");
			jdbc.execute("create trigger stage_sync_failure before update on code_academy.project_stages "
				+ "for each row execute function code_academy.stage_sync_fail()");
			assertThatThrownBy(() -> synchronizer.synchronize(List.of(failedUpdate, other))).isInstanceOf(RuntimeException.class);
		}
		finally {
			jdbc.execute("drop trigger if exists stage_sync_failure on code_academy.project_stages");
			jdbc.execute("drop function if exists code_academy.stage_sync_fail()");
		}
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		assertThat(personal(first)).isEqualTo(firstBefore);
		assertThat(personal(second)).isEqualTo(secondBefore);
	}

	private static Manifest mapStages(Manifest manifest, UnaryOperator<Stage> change) {
		return withStages(manifest, manifest.projectStages().stream().map(change).toList());
	}

	private static Manifest withStages(Manifest m, List<Stage> stages) {
		return new Manifest(m.schemaVersion(), m.source(), m.course(), m.phases(), m.lessons(), m.resources(),
			m.sourceToId(), stages, m.provenance());
	}
}
