package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Standalone offline entry point. Deliberately never starts Spring or opens a database. */
public final class ContentUpdateCli {
    static final String HELP = "Usage: content-update compare|apply --active DIR --candidate DIR --source GIT --report FILE\n"
        + "Compare prepared copies offline; apply requires the server stopped. No database writes.\n";

    private ContentUpdateCli() {}

    public static void main(String[] args) {
        int result;
        try { result = execute(args); }
        catch (ContentValidationException ex) {
            System.err.println("content-update: invalid content: " + String.join("; ", ex.problems()));
            result = 2;
        }
        catch (IOException | IllegalArgumentException ex) {
            System.err.println("content-update: " + ex.getMessage());
            result = 2;
        }
        System.exit(result);
    }

    private static int execute(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.print(HELP);
            return 0;
        }
        if (args.length != 9 || !List.of("compare", "apply").contains(args[0])) throw invalid();
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!List.of("--active", "--candidate", "--source", "--report").contains(args[i])
                || args[i + 1].isBlank() || options.putIfAbsent(args[i], args[i + 1]) != null) throw invalid();
        }
        Paths paths = new Paths(checked(Path.of(options.get("--active"))), checked(Path.of(options.get("--candidate"))),
            checkedSource(Path.of(options.get("--source"))), checked(Path.of(options.get("--report"))));
        List<Path> distinct = List.of(paths.active(), paths.candidate(), paths.source(), paths.report());
        for (int i = 0; i < distinct.size(); i++) for (int j = i + 1; j < distinct.size(); j++) {
            if (overlaps(distinct.get(i), distinct.get(j))) throw new IllegalArgumentException("paths must not overlap");
        }
        if (!Files.isDirectory(paths.active()) || !Files.isDirectory(paths.candidate()) || !Files.isDirectory(paths.source())
            || !Files.isDirectory(paths.report().getParent())) throw new IllegalArgumentException("directories must exist");
        ContentUpdateComparison comparison = ContentUpdateComparison.compare(paths);
        String text = comparison.report();
        if (args[0].equals("compare")) {
            // CREATE_NEW avoids replacing an unrelated report, including a link introduced after path validation.
            Files.writeString(paths.report(), text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("Comparison written; conflicts: " + comparison.conflicts().size());
            return comparison.conflicts().isEmpty() ? 0 : 3;
        }
        requireReport(paths, text);
        if (!comparison.conflicts().isEmpty()) throw new IllegalArgumentException("comparison contains conflicts");
        Path backup = ContentUpdateInstall.install(paths, comparison);
        System.out.println("Applied; previous root retained at " + backup);
        return 0;
    }

    static void requireReport(Paths paths, String expected) throws IOException {
        checked(paths.report());
        if (!Files.isRegularFile(paths.report(), LinkOption.NOFOLLOW_LINKS)
            || !Files.readString(paths.report()).equals(expected)) {
            throw new IllegalArgumentException("report does not match current comparison");
        }
    }

    static boolean overlaps(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }

    /**
     * The source repository may be given relative and with {@code ..} ({@code ../Cursos/curso-inicial}, the layout of the
     * common plan): it is only read, through Git objects, so the path is normalized first and then gets every other check
     * (no protected names, no symbolic links, no overlap with the other paths). The paths that are written stay strict.
     */
    static Path checkedSource(Path input) throws IOException {
        return checked(input.toAbsolutePath().normalize());
    }

    /** Reject lexical escapes and symlink ancestors even when the final file does not yet exist. */
    static Path checked(Path input) throws IOException {
        for (Path part : input) {
            String name = part.toString();
            if (name.equals("..")) throw new IllegalArgumentException("path escapes are not allowed");
            if (name.equals(".env") || name.startsWith(".env.")) throw new IllegalArgumentException("protected paths are not allowed");
        }
        Path path = input.toAbsolutePath().normalize();
        Path current = path.getRoot();
        for (Path part : path) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("symbolic links are not allowed");
        }
        return path;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("invalid arguments; use --help"); }

    record Paths(Path active, Path candidate, Path source, Path report) {}
}
