package com.devpilot.skill.sandbox;

import com.devpilot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs a skill script in a confined child process.
 *
 * <p>Skills are the one place in DevPilot where downloaded code executes, so the confinement is
 * built from several independent controls rather than one:
 *
 * <ul>
 *   <li><b>Interpreter allow list</b> — only the runtimes named in configuration can be launched,
 *       so a package cannot bring its own binary.
 *   <li><b>Path confinement</b> — the entrypoint is normalised, resolved through symbolic links and
 *       re-checked against the package root, the same two-stage check the code tools use.
 *   <li><b>No inherited environment</b> — the child starts from an empty environment and receives
 *       only the variables on the allow list. Model credentials and database passwords are never
 *       among them.
 *   <li><b>No shell, no argv interpolation</b> — arguments are written to the child's stdin as
 *       JSON. Nothing a model produced ever reaches a command line.
 *   <li><b>Fresh working directory</b> — each execution gets an empty temporary directory that is
 *       deleted afterwards, so a skill cannot see or leave state between runs.
 *   <li><b>Time and output budgets</b> — the process tree is destroyed on timeout and captured
 *       output is capped.
 * </ul>
 *
 * <p>What this deliberately does not claim: it is not an OS-level jail. A skill can still reach the
 * network and read files the backend user can read. Running untrusted packages therefore also
 * depends on installation being a human decision and on execution being approved.
 */
@Component
public class SkillSandbox {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillSandbox.class);

    private final Map<String, String> allowedRuntimes;
    private final Map<String, String> environmentAllowList;
    private final Duration defaultTimeout;
    private final int maxOutputBytes;
    private final ExecutorService streamReaders =
            Executors.newCachedThreadPool(Thread.ofPlatform().name("skill-io-", 0).daemon().factory());

    /**
     * Creates the sandbox.
     *
     * @param appProperties application configuration supplying the limits and allow lists
     */
    public SkillSandbox(AppProperties appProperties) {
        AppProperties.Skill settings = appProperties.skill();
        this.defaultTimeout = settings.defaultTimeout();
        this.maxOutputBytes = settings.maxOutputBytes();

        Map<String, String> runtimes = new LinkedHashMap<>();
        if (settings.allowedRuntimes() != null) {
            settings.allowedRuntimes().forEach((name, command) ->
                    runtimes.put(name.toUpperCase(Locale.ROOT), command));
        }
        this.allowedRuntimes = Map.copyOf(runtimes);

        Map<String, String> environment = new LinkedHashMap<>();
        if (settings.environmentAllowList() != null) {
            settings.environmentAllowList().forEach(name -> {
                String value = System.getenv(name);
                if (value != null) {
                    environment.put(name, value);
                }
            });
        }
        this.environmentAllowList = Map.copyOf(environment);
    }

    /**
     * Runs one skill script.
     *
     * @param packageRoot directory the installed skill lives in
     * @param runtime runtime name declared by the skill, for example {@code NODE}
     * @param entrypoint script path relative to the package root
     * @param argumentsJson arguments handed to the script on stdin
     * @param timeout wall-clock limit, null to use the configured default
     * @return what the script produced
     * @throws SkillExecutionException when the skill cannot be launched or exceeds its budget
     */
    public SkillExecutionResult run(
            Path packageRoot, String runtime, String entrypoint, String argumentsJson, Duration timeout) {

        String interpreter = resolveInterpreter(runtime);
        Path script = resolveEntrypoint(packageRoot, entrypoint);
        Duration limit = timeout == null ? defaultTimeout : timeout;

        Path workingDirectory = createWorkingDirectory();
        long startedAt = System.nanoTime();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(interpreter, script.toString());
            builder.directory(workingDirectory.toFile());
            // Start from nothing and add back only what the allow list names.
            builder.environment().clear();
            builder.environment().putAll(environmentAllowList);

            Process started;
            try {
                started = builder.start();
            } catch (IOException exception) {
                throw new SkillExecutionException(
                        SkillExecutionException.Reason.LAUNCH_FAILED,
                        "Runtime " + runtime + " could not be started");
            }
            process = started;

            writeArguments(started, argumentsJson);
            Future<String> stdout = streamReaders.submit(() -> read(started.getInputStream()));
            Future<String> stderr = streamReaders.submit(() -> read(started.getErrorStream()));

            if (!started.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) {
                destroyTree(started);
                throw new SkillExecutionException(
                        SkillExecutionException.Reason.TIMEOUT,
                        "Skill exceeded its " + limit.toMillis() + " ms budget and was stopped");
            }

            String out = stdout.get(5, TimeUnit.SECONDS);
            String err = stderr.get(5, TimeUnit.SECONDS);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            boolean truncated = out.length() >= maxOutputBytes || err.length() >= maxOutputBytes;

            return new SkillExecutionResult(started.exitValue(), out, err, truncated, durationMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                destroyTree(process);
            }
            throw new SkillExecutionException(
                    SkillExecutionException.Reason.TIMEOUT, "Skill execution was interrupted");
        } catch (SkillExecutionException exception) {
            throw exception;
        } catch (Exception exception) {
            if (process != null) {
                destroyTree(process);
            }
            LOGGER.error("Skill execution failed unexpectedly", exception);
            throw new SkillExecutionException(
                    SkillExecutionException.Reason.LAUNCH_FAILED,
                    "Skill could not be executed: " + exception.getClass().getSimpleName());
        } finally {
            deleteRecursively(workingDirectory);
        }
    }

    private String resolveInterpreter(String runtime) {
        String key = runtime == null ? "" : runtime.toUpperCase(Locale.ROOT);
        String interpreter = allowedRuntimes.get(key);
        if (interpreter == null) {
            throw new SkillExecutionException(
                    SkillExecutionException.Reason.RUNTIME_NOT_ALLOWED,
                    "Runtime " + runtime + " is not on the allow list " + allowedRuntimes.keySet());
        }
        return interpreter;
    }

    /**
     * Resolves the entrypoint and proves it stays inside the skill package.
     *
     * <p>Checked twice on purpose: once on the normalised path to reject {@code ../} traversal, and
     * once on the real path so a symbolic link inside the package cannot point at something else.
     *
     * @param packageRoot installed package directory
     * @param entrypoint declared script path
     * @return real path of the script
     */
    private static Path resolveEntrypoint(Path packageRoot, String entrypoint) {
        String cleaned = entrypoint == null ? "" : entrypoint.trim().replace('\\', '/');
        Path requested;
        try {
            requested = Path.of(cleaned);
        } catch (InvalidPathException exception) {
            throw new SkillExecutionException(
                    SkillExecutionException.Reason.ENTRYPOINT_ESCAPES_PACKAGE,
                    "Entrypoint is not a usable path");
        }
        if (cleaned.isEmpty() || requested.isAbsolute()) {
            throw new SkillExecutionException(
                    SkillExecutionException.Reason.ENTRYPOINT_ESCAPES_PACKAGE,
                    "Entrypoint must be a path relative to the skill package");
        }

        try {
            Path root = packageRoot.toRealPath();
            Path candidate = root.resolve(requested).normalize();
            if (!candidate.startsWith(root)) {
                throw new SkillExecutionException(
                        SkillExecutionException.Reason.ENTRYPOINT_ESCAPES_PACKAGE,
                        "Entrypoint escapes the skill package");
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) {
                throw new SkillExecutionException(
                        SkillExecutionException.Reason.ENTRYPOINT_ESCAPES_PACKAGE,
                        "Entrypoint links outside the skill package");
            }
            if (!Files.isRegularFile(real)) {
                throw new SkillExecutionException(
                        SkillExecutionException.Reason.ENTRYPOINT_NOT_FOUND,
                        "Entrypoint is not a readable file");
            }
            return real;
        } catch (IOException exception) {
            throw new SkillExecutionException(
                    SkillExecutionException.Reason.ENTRYPOINT_NOT_FOUND,
                    "Entrypoint does not exist inside the skill package");
        }
    }

    /**
     * Hands the arguments to the script on stdin.
     *
     * <p>Arguments never appear on a command line, so nothing a model produced can be interpreted
     * as an option, a redirect or a second command.
     *
     * @param process running script
     * @param argumentsJson arguments as JSON
     */
    private static void writeArguments(Process process, String argumentsJson) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write((argumentsJson == null ? "{}" : argumentsJson).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException exception) {
            // A script that never reads stdin closes it early; that is not a failure of ours.
            LOGGER.debug("Skill closed stdin before arguments were fully written", exception);
        }
    }

    /**
     * Captures up to the output budget, then keeps draining.
     *
     * <p>Draining matters: a child whose stdout pipe fills up blocks forever, so a skill that is
     * merely chatty would look like a hang and die on the timeout. The captured prefix is bounded,
     * the rest is discarded, and the timeout still bounds a skill that never stops talking.
     *
     * @param stream child output stream
     * @return captured prefix
     * @throws IOException when the stream cannot be read
     */
    private String read(InputStream stream) throws IOException {
        byte[] head = stream.readNBytes(maxOutputBytes);
        stream.transferTo(OutputStream.nullOutputStream());
        return new String(head, StandardCharsets.UTF_8);
    }

    private static void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static Path createWorkingDirectory() {
        try {
            return Files.createTempDirectory("devpilot-skill-");
        } catch (IOException exception) {
            throw new SkillExecutionException(
                    SkillExecutionException.Reason.LAUNCH_FAILED,
                    "A working directory for the skill could not be created");
        }
    }

    private static void deleteRecursively(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A file the skill left locked is cleaned up by the OS temp sweeper.
                }
            });
        } catch (IOException exception) {
            LOGGER.warn("Skill working directory could not be removed: {}", exception.getMessage());
        }
    }
}
