package com.devpilot.code.provider;

import com.devpilot.code.CodeRepositoryService;
import com.devpilot.code.RepositoryAccessException;
import com.devpilot.code.model.CodeMatch;
import com.devpilot.code.model.ListFilesRequest;
import com.devpilot.code.model.ListFilesResult;
import com.devpilot.code.model.ReadCodeFileRequest;
import com.devpilot.code.model.ReadCodeFileResult;
import com.devpilot.code.model.SearchCodeRequest;
import com.devpilot.code.model.SearchCodeResult;
import com.devpilot.config.AppProperties;
import com.devpilot.project.model.ProjectRow;
import com.devpilot.project.service.ProjectService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the local directory a project is bound to.
 *
 * <p>Everything this provider returns is confined to the repository root: a requested path is
 * normalised, resolved through symbolic links and re-checked against the root, so neither
 * {@code ../..} nor a link planted inside the repository can reach outside it. Sensitive files are
 * refused by name whatever a model asks for, and only text files of allowed types are opened.
 * Searching uses Java NIO rather than shelling out, so no model input reaches a command line.
 */
@Service
public class LocalCodeRepositoryProvider implements CodeRepositoryService {

    private static final int CONTEXT_LINES = 2;
    private static final Set<String> PRUNED_DIRECTORIES =
            Set.of(".git", ".idea", "node_modules", "target", "build", "dist", ".gradle", ".mvn");

    private final ProjectService projectService;
    private final int maxFileBytes;
    private final int maxReadLines;
    private final List<PathMatcher> deniedNameMatchers;
    private final Set<String> allowedExtensions;

    /**
     * Creates the provider.
     *
     * @param projectService project lookup and repository path resolution
     * @param appProperties application configuration supplying the limits and the blacklist
     */
    public LocalCodeRepositoryProvider(ProjectService projectService, AppProperties appProperties) {
        AppProperties.Repository settings = appProperties.repository();
        this.projectService = projectService;
        this.maxFileBytes = settings.maxFileBytes();
        this.maxReadLines = settings.maxReadLines();
        this.deniedNameMatchers = settings.deniedFilePatterns() == null
                ? List.of()
                : settings.deniedFilePatterns().stream()
                        .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                        .toList();
        this.allowedExtensions = settings.searchableExtensions() == null
                ? Set.of()
                : settings.searchableExtensions().stream()
                        .map(extension -> extension.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ListFilesResult listFiles(ListFilesRequest request) {
        Path root = repositoryRoot(request.projectId());
        Path directory = resolveInside(root, request.relativePath());
        if (!Files.isDirectory(directory)) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.UNSUPPORTED_FILE,
                    "Path is not a directory: " + relativeOf(root, directory));
        }

        List<String> files = new ArrayList<>();
        boolean[] truncated = {false};
        walk(directory, request.maxDepth(), (path, attributes) -> {
            if (isDeniedName(path)) {
                return true;
            }
            if (files.size() >= request.limit()) {
                truncated[0] = true;
                return false;
            }
            files.add(relativeOf(root, path));
            return true;
        });
        return new ListFilesResult(files, truncated[0]);
    }

    @Override
    public SearchCodeResult searchCode(SearchCodeRequest request) {
        Path root = repositoryRoot(request.projectId());
        PathMatcher fileMatcher = request.filePattern() == null || request.filePattern().isBlank()
                ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + request.filePattern().trim());
        String needle = request.keyword().toLowerCase(Locale.ROOT);

        List<CodeMatch> matches = new ArrayList<>();
        int[] scanned = {0};
        boolean[] truncated = {false};

        walk(root, Integer.MAX_VALUE, (path, attributes) -> {
            if (matches.size() >= request.limit()) {
                truncated[0] = true;
                return false;
            }
            if (!isSearchable(path) || attributes.size() > maxFileBytes) {
                return true;
            }
            if (fileMatcher != null && !fileMatcher.matches(path.getFileName())) {
                return true;
            }

            List<String> lines = readLinesQuietly(path);
            if (lines == null) {
                return true;
            }
            scanned[0]++;
            String relative = relativeOf(root, path);
            for (int index = 0; index < lines.size(); index++) {
                if (!lines.get(index).toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                if (matches.size() >= request.limit()) {
                    truncated[0] = true;
                    return false;
                }
                matches.add(new CodeMatch(
                        relative,
                        index + 1,
                        lines.get(index),
                        lines.subList(Math.max(0, index - CONTEXT_LINES), index),
                        lines.subList(Math.min(lines.size(), index + 1),
                                Math.min(lines.size(), index + 1 + CONTEXT_LINES))));
            }
            return true;
        });

        return new SearchCodeResult(matches, scanned[0], truncated[0]);
    }

    @Override
    public ReadCodeFileResult readFile(ReadCodeFileRequest request) {
        Path root = repositoryRoot(request.projectId());
        Path file = resolveInside(root, request.relativePath());

        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.UNSUPPORTED_FILE,
                    "Path is not a regular file: " + relativeOf(root, file));
        }
        if (isDeniedName(file)) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.PATH_FORBIDDEN,
                    "File " + relativeOf(root, file) + " is on the sensitive-file blacklist");
        }
        if (!hasAllowedExtension(file)) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.UNSUPPORTED_FILE,
                    "File type of " + relativeOf(root, file) + " is not readable by the code tools");
        }
        long size = sizeOf(file);
        if (size > maxFileBytes) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.UNSUPPORTED_FILE,
                    "File " + relativeOf(root, file) + " is larger than the " + maxFileBytes
                            + " byte read limit");
        }

        List<String> lines = readLinesQuietly(file);
        if (lines == null) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.UNSUPPORTED_FILE,
                    "File " + relativeOf(root, file) + " is not readable text");
        }

        int start = Math.min(request.startLine(), Math.max(lines.size(), 1));
        int requestedEnd = Math.max(request.endLine(), start);
        int end = Math.min(requestedEnd, lines.size());
        boolean truncated = false;
        if (end - start + 1 > maxReadLines) {
            end = start + maxReadLines - 1;
            truncated = true;
        }
        if (requestedEnd > lines.size()) {
            truncated = true;
        }

        List<String> selected = start > lines.size()
                ? List.of()
                : List.copyOf(lines.subList(start - 1, end));
        return new ReadCodeFileResult(
                relativeOf(root, file), start, Math.max(end, start - 1), lines.size(), selected, truncated);
    }

    private Path repositoryRoot(long projectId) {
        ProjectRow project = projectService.require(projectId);
        Path configured = projectService.resolveRepositoryPath(project.getRepositoryPath());
        try {
            Path real = configured.toRealPath();
            if (!Files.isDirectory(real)) {
                throw new RepositoryAccessException(
                        RepositoryAccessException.Reason.REPOSITORY_UNAVAILABLE,
                        "Configured repository path of project " + projectId + " is not a directory");
            }
            return real;
        } catch (IOException exception) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.REPOSITORY_UNAVAILABLE,
                    "Configured repository path of project " + projectId + " cannot be accessed");
        }
    }

    /**
     * Resolves a caller-supplied path and proves it stays inside the repository.
     *
     * <p>The check runs twice on purpose: once on the normalised path to reject {@code ../}
     * traversal, and once on the real path so a symbolic link cannot smuggle the read outside.
     *
     * @param root real path of the repository root
     * @param relativePath caller-supplied path
     * @return real path inside the repository
     */
    private Path resolveInside(Path root, String relativePath) {
        String cleaned = relativePath == null ? "" : relativePath.trim().replace('\\', '/');
        Path requested;
        try {
            requested = Path.of(cleaned);
        } catch (InvalidPathException exception) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.PATH_FORBIDDEN, "Path is not usable: " + cleaned);
        }
        if (requested.isAbsolute()) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.PATH_ESCAPES_REPOSITORY,
                    "Only paths relative to the repository root are allowed");
        }

        Path candidate = root.resolve(requested).normalize();
        if (!candidate.startsWith(root)) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.PATH_ESCAPES_REPOSITORY,
                    "Path escapes the repository root: " + cleaned);
        }
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) {
                throw new RepositoryAccessException(
                        RepositoryAccessException.Reason.PATH_ESCAPES_REPOSITORY,
                        "Path links outside the repository root: " + cleaned);
            }
            return real;
        } catch (IOException exception) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.PATH_NOT_FOUND, "Path does not exist: " + cleaned);
        }
    }

    private void walk(Path start, int maxDepth, FileHandler handler) {
        try {
            Files.walkFileTree(start, Set.of(), maxDepth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    String name = directory.getFileName() == null ? "" : directory.getFileName().toString();
                    return PRUNED_DIRECTORIES.contains(name) && !directory.equals(start)
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (!attributes.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    return handler.handle(file, attributes)
                            ? FileVisitResult.CONTINUE
                            : FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    // An unreadable entry is skipped rather than failing the whole listing.
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.REPOSITORY_UNAVAILABLE,
                    "Repository could not be scanned");
        }
    }

    private boolean isSearchable(Path file) {
        return !isDeniedName(file) && hasAllowedExtension(file);
    }

    private boolean isDeniedName(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return true;
        }
        Path lowered = Path.of(fileName.toString().toLowerCase(Locale.ROOT));
        return deniedNameMatchers.stream().anyMatch(matcher -> matcher.matches(lowered));
    }

    private boolean hasAllowedExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return false;
        }
        return allowedExtensions.contains(name.substring(dot + 1));
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new RepositoryAccessException(
                    RepositoryAccessException.Reason.PATH_NOT_FOUND, "File size cannot be read");
        }
    }

    /**
     * Reads a file as UTF-8 text, or reports it as unusable.
     *
     * @param file file to read
     * @return lines without terminators, or null when the file is binary or unreadable
     */
    private List<String> readLinesQuietly(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            for (byte value : bytes) {
                if (value == 0) {
                    return null;
                }
            }
            // Decoding with the replacement strategy keeps a mis-encoded file searchable instead of
            // failing the whole scan.
            return new String(bytes, StandardCharsets.UTF_8).lines().toList();
        } catch (IOException | OutOfMemoryError exception) {
            return null;
        }
    }

    private static String relativeOf(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    /** Callback invoked for each regular file during a walk. */
    @FunctionalInterface
    private interface FileHandler {

        /**
         * Handles one file.
         *
         * @param file visited file
         * @param attributes file attributes
         * @return whether the walk should continue
         */
        boolean handle(Path file, BasicFileAttributes attributes);
    }
}
