package com.devpilot.skill.service;

import com.devpilot.config.AppProperties;
import com.devpilot.skill.SkillSourceException;
import com.devpilot.skill.model.SkillPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes a downloaded skill package to disk.
 *
 * <p>Installation is the moment untrusted content becomes files, so it is validated rather than
 * trusted: the declared runtime must be on the allow list, every file path must stay inside the
 * package directory, and the entrypoint must be one of the files actually shipped. A package that
 * fails any check leaves nothing behind.
 *
 * <p>The recorded checksum covers the content that was written, so a later execution can prove it
 * is running what was reviewed.
 */
@Component
public class SkillInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillInstaller.class);
    private static final int MAX_FILES = 50;
    private static final int MAX_TOTAL_BYTES = 1024 * 1024;

    private final Path installRoot;
    private final java.util.Set<String> allowedRuntimes;

    /**
     * Creates the installer.
     *
     * @param appProperties application configuration supplying the install directory and allow list
     */
    public SkillInstaller(AppProperties appProperties) {
        AppProperties.Skill settings = appProperties.skill();
        this.installRoot = Path.of(settings.installDir()).toAbsolutePath().normalize();
        this.allowedRuntimes = settings.allowedRuntimes() == null
                ? java.util.Set.of()
                : settings.allowedRuntimes().keySet().stream()
                        .map(name -> name.toUpperCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Installs a package, replacing any previous installation of the same key.
     *
     * @param skillPackage package to install
     * @return where it was installed and the checksum of what was written
     */
    public Installed install(SkillPackage skillPackage) {
        validate(skillPackage);

        Path packageRoot = installRoot.resolve(skillPackage.key()).normalize();
        if (!packageRoot.startsWith(installRoot)) {
            throw new SkillSourceException("Skill key would place the package outside the install root");
        }

        try {
            deleteRecursively(packageRoot);
            Files.createDirectories(packageRoot);

            for (Map.Entry<String, String> file : skillPackage.files().entrySet()) {
                Path target = resolveInside(packageRoot, file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Skill package could not be written", exception);
        }

        String checksum = checksumOf(skillPackage);
        LOGGER.info("Installed skill {} version {} at {} (sha256 {})",
                skillPackage.key(), skillPackage.version(), packageRoot, checksum);
        return new Installed(packageRoot, checksum);
    }

    /**
     * Removes an installed package from disk.
     *
     * @param packageRoot directory to remove
     */
    public void uninstall(Path packageRoot) {
        Path normalized = packageRoot.toAbsolutePath().normalize();
        if (!normalized.startsWith(installRoot)) {
            throw new SkillSourceException("Refusing to remove a directory outside the install root");
        }
        deleteRecursively(normalized);
    }

    private void validate(SkillPackage skillPackage) {
        if (skillPackage.key() == null || !skillPackage.key().matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new SkillSourceException(
                    "Skill key must be lower-case letters, digits or dash, so it can be used verbatim as a tool name");
        }
        String runtime = skillPackage.runtime() == null
                ? ""
                : skillPackage.runtime().toUpperCase(Locale.ROOT);
        if (!allowedRuntimes.contains(runtime)) {
            throw new SkillSourceException(
                    "Runtime " + skillPackage.runtime() + " is not on the allow list " + allowedRuntimes);
        }
        if (skillPackage.files().isEmpty()) {
            throw new SkillSourceException("Skill package ships no files");
        }
        if (skillPackage.files().size() > MAX_FILES) {
            throw new SkillSourceException("Skill package ships more than " + MAX_FILES + " files");
        }
        int total = skillPackage.files().values().stream()
                .mapToInt(content -> content.getBytes(StandardCharsets.UTF_8).length)
                .sum();
        if (total > MAX_TOTAL_BYTES) {
            throw new SkillSourceException("Skill package is larger than the 1 MiB limit");
        }
        if (!skillPackage.files().containsKey(skillPackage.entrypoint())) {
            throw new SkillSourceException(
                    "Entrypoint " + skillPackage.entrypoint() + " is not among the shipped files");
        }
    }

    /**
     * Resolves a package-relative file path and proves it stays inside the package.
     *
     * <p>This is the archive-extraction check without an archive: a path such as
     * {@code ../../etc/cron.d/evil} must never become a write outside the package directory.
     *
     * @param packageRoot package directory
     * @param filePath path declared by the package
     * @return absolute path to write to
     */
    private static Path resolveInside(Path packageRoot, String filePath) {
        String cleaned = filePath == null ? "" : filePath.trim().replace('\\', '/');
        Path requested;
        try {
            requested = Path.of(cleaned);
        } catch (InvalidPathException exception) {
            throw new SkillSourceException("Skill package declares an unusable file path");
        }
        if (cleaned.isEmpty() || requested.isAbsolute()) {
            throw new SkillSourceException("Skill package file paths must be relative");
        }
        Path target = packageRoot.resolve(requested).normalize();
        if (!target.startsWith(packageRoot)) {
            throw new SkillSourceException(
                    "Skill package tries to write outside its own directory: " + cleaned);
        }
        return target;
    }

    /**
     * Computes a stable digest of what the package contains.
     *
     * @param skillPackage package to digest
     * @return lower-case hexadecimal SHA-256
     */
    public static String checksumOf(SkillPackage skillPackage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(skillPackage.key()).getBytes(StandardCharsets.UTF_8));
            digest.update(String.valueOf(skillPackage.version()).getBytes(StandardCharsets.UTF_8));
            digest.update(String.valueOf(skillPackage.runtime()).getBytes(StandardCharsets.UTF_8));
            digest.update(String.valueOf(skillPackage.entrypoint()).getBytes(StandardCharsets.UTF_8));
            // Sorted so the digest does not depend on map iteration order.
            new TreeMap<>(skillPackage.files()).forEach((path, content) -> {
                digest.update(path.getBytes(StandardCharsets.UTF_8));
                digest.update(content.getBytes(StandardCharsets.UTF_8));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Previous skill installation could not be removed", exception);
        }
    }

    /**
     * Where a package landed and what it hashed to.
     *
     * @param packageRoot directory the package was written to
     * @param checksum SHA-256 of the installed content
     */
    public record Installed(Path packageRoot, String checksum) {
    }
}
