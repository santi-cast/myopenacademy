package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** A stopped-server directory swap, not a filesystem/database transaction or a recovery framework. */
final class ContentUpdateInstall {
    private ContentUpdateInstall() {}

    static Path install(ContentUpdateCli.Paths paths, ContentUpdateComparison comparison) throws IOException {
        Path active = paths.active();
        Path backup = active.resolveSibling(active.getFileName() + ".previous");
        Path stage = active.resolveSibling(active.getFileName() + ".staged");
        for (Path reserved : new Path[] {backup, stage}) {
            ContentUpdateCli.checked(reserved);
            for (Path input : new Path[] {paths.candidate(), paths.source(), paths.report()}) {
                if (ContentUpdateCli.overlaps(reserved, input)) throw new IllegalArgumentException("paths must not overlap");
            }
            if (Files.exists(reserved, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("installation paths already exist");
        }
        // Never remove an arbitrary directory. Even an incomplete stage is retained for explicit operator inspection.
        Files.createDirectory(stage);
        try {
            Map<String, String> expected = new TreeMap<>(comparison.candidate().files());
            comparison.base().files().forEach((local, hash) -> {
                if (!comparison.base().managed().containsKey(local) && !comparison.base().manifests().contains(local)) {
                    expected.putIfAbsent(local, hash);
                }
            });
            for (var entry : expected.entrySet()) {
                Path target = stage.resolve(entry.getKey());
                if (entry.getValue().equals("directory")) {
                    Files.createDirectories(target);
                    continue;
                }
                Path from = comparison.candidate().files().containsKey(entry.getKey()) ? paths.candidate() : active;
                ArrayList<String> problems = new ArrayList<>();
                Path checked = ContentPaths.file(from, entry.getKey(), "installation copy", problems);
                if (checked == null) throw new ContentValidationException(problems);
                Files.createDirectories(target.getParent());
                Files.copy(checked, target);
            }
            ContentUpdateComparison.Copy installed = ContentUpdateComparison.read(stage);
            if (!installed.files().equals(expected)) throw new IOException("staged copy differs from verified inputs");
            // Copying is not a trust boundary: re-read both inputs, Git source and exact report immediately before the swap.
            ContentUpdateComparison actual = ContentUpdateComparison.compare(paths);
            ContentUpdateCli.requireReport(paths, actual.report());
            if (!actual.report().equals(comparison.report()) || !actual.conflicts().isEmpty()) throw new IOException("inputs changed during staging");
            ContentUpdateCli.checked(active);
            ContentUpdateCli.checked(backup);
            ContentUpdateCli.checked(stage);
            if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) throw new IOException("backup appeared during staging");
            Files.move(active, backup, StandardCopyOption.ATOMIC_MOVE);
            try {
                Files.move(stage, active, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException failure) {
                try {
                    if (!Files.exists(active, LinkOption.NOFOLLOW_LINKS)) Files.move(backup, active, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
            return backup;
        }
        catch (IOException | RuntimeException failure) {
            throw new IOException("installation failed; inspect retained paths " + stage + " and " + backup
                + "; " + failure.getMessage(), failure);
        }
    }
}
