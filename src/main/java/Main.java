import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class Main {

    // grepdir dir pattern [encoding]
    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            usage();
            return;
        }
        Charset charset = args.length == 2 ? Charset.defaultCharset() : extractCharset(args[2]);
        Pattern pattern = extractPattern(args[1]);
        String dirName = args[0];
        final PatternSearcher searcher = new PatternSearcher(charset, pattern);
        searcher.search(dirName);
    }

    private static class PatternSearcher {
        private final Charset charset;
        private final Pattern pattern;

        private PatternSearcher(Charset charset, Pattern pattern) {
            this.charset = charset;
            this.pattern = pattern;
        }

        private void search(String dirName) {
            Path dir;
            try {
                dir = Paths.get(dirName);
            } catch (InvalidPathException e) {
                throw new GrepDirException("Path is invalid: " + dirName, e);
            }
            try (Stream<Path> filePaths = Files.list(dir)) {
                filePaths.forEach(filePath -> search(filePath));
            } catch (IOException e) {
                throw new GrepDirException("Failed to access directory: " + dirName, e);
            }
        }

        private void search(Path filePath) {
            try (final Stream<String> lines = Files.lines(filePath, charset)) {
                LineCounter lineCounter = new LineCounter();
                lines.forEach(line -> {
                    if (pattern.matcher(line).find()) {
                        System.out.printf("%s %d: %s%n", filePath.normalize().toAbsolutePath(), lineCounter.cnt, line);
                    }
                    lineCounter.inc();
                });
            } catch (SecurityException e) {
                throw new GrepDirException(String.format("Access to file %s was denied", filePath.normalize().toAbsolutePath()), e);
            } catch (IOException e) {
                throw new GrepDirException(String.format("Failed to read from file %s", filePath.normalize().toAbsolutePath()), e);
            }
        }
    }

    private static class LineCounter {
        private int cnt = 1;

        private void inc() {
            cnt++;
        }
    }

    private static Pattern extractPattern(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new GrepDirException("Invalid pattern: " + regex, e);
        }
    }

    private static Charset extractCharset(String encodingName) {
        try {
            return Charset.forName(encodingName);
        } catch (IllegalArgumentException e) {
            throw new GrepDirException("Unknown encoding: " + encodingName, e);
        }
    }

    private static void usage() {
        System.err.println("""
    Usage: grepdir dir pattern [encoding]
     Recursively traverse given directory and find specified pattern in files
        dir - directory to traverse
        pattern - what to search in files
        encoding - files encoding in directory, will use system encoding by default 
    """);
    }
}
