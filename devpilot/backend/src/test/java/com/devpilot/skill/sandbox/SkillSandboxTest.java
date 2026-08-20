package com.devpilot.skill.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract: a skill script runs confined. These are the controls a downloaded package must not be
 * able to talk its way past, so each one is asserted against a real child process.
 */
@SpringBootTest
@ActiveProfiles("test")
class SkillSandboxTest {

    @Autowired
    private SkillSandbox sandbox;

    @TempDir
    private Path packageRoot;

    @TempDir
    private Path outside;

    @BeforeEach
    void seedPackage() throws IOException {
        // Echoes the arguments it was given back as JSON.
        Files.writeString(packageRoot.resolve("echo.js"), """
                let raw = '';
                process.stdin.on('data', (chunk) => { raw += chunk; });
                process.stdin.on('end', () => {
                  const args = raw.trim() ? JSON.parse(raw) : {};
                  process.stdout.write(JSON.stringify({ received: args }));
                });
                """);
        Files.writeString(outside.resolve("intruder.js"), "process.stdout.write('escaped');");
    }

    @Test
    void runsAScriptAndReturnsItsOutput() {
        SkillExecutionResult result = sandbox.run(
                packageRoot, "NODE", "echo.js", "{\"keyword\":\"createOrder\"}", null);

        assertThat(result.successful()).isTrue();
        assertThat(result.stdout()).contains("createOrder");
        assertThat(result.durationMs()).isNotNegative();
    }

    @Test
    void passesArgumentsOnStdinSoNothingReachesACommandLine() throws IOException {
        // A value that would be catastrophic if it were ever interpolated into a shell command.
        Files.writeString(packageRoot.resolve("echo.js"), """
                let raw = '';
                process.stdin.on('data', (chunk) => { raw += chunk; });
                process.stdin.on('end', () => {
                  process.stdout.write(JSON.parse(raw).value);
                });
                """);

        SkillExecutionResult result = sandbox.run(
                packageRoot, "NODE", "echo.js", "{\"value\":\"; rm -rf / #\"}", null);

        assertThat(result.successful()).isTrue();
        assertThat(result.stdout()).isEqualTo("; rm -rf / #");
    }

    @Test
    void withholdsCredentialsFromTheSkillProcess() throws IOException {
        Files.writeString(packageRoot.resolve("env.js"),
                "process.stdout.write(JSON.stringify(Object.keys(process.env)));");

        SkillExecutionResult result = sandbox.run(packageRoot, "NODE", "env.js", "{}", null);

        assertThat(result.successful()).isTrue();
        assertThat(result.stdout())
                .doesNotContain("DASHSCOPE_API_KEY")
                .doesNotContain("DB_PASSWORD")
                .doesNotContain("SKILL_MARKETPLACE_URL");
    }

    @Test
    void returnsNonAsciiOutputIntact() throws IOException {
        // The sandbox reads UTF-8, so it must make the child write UTF-8: an interpreter following
        // the host code page would hand the model mojibake and call it evidence.
        Files.writeString(packageRoot.resolve("chinese.js"),
                "process.stdout.write('异常链：空指针 → OrderService.java:86');", StandardCharsets.UTF_8);

        SkillExecutionResult result = sandbox.run(packageRoot, "NODE", "chinese.js", "{}", null);

        assertThat(result.successful()).isTrue();
        assertThat(result.stdout()).isEqualTo("异常链：空指针 → OrderService.java:86");
    }

    @Test
    void givesEachRunAnEmptyWorkingDirectory() throws IOException {
        Files.writeString(packageRoot.resolve("cwd.js"), """
                const fs = require('fs');
                fs.writeFileSync('left-behind.txt', 'x');
                process.stdout.write(JSON.stringify(fs.readdirSync('.')));
                """);

        SkillExecutionResult first = sandbox.run(packageRoot, "NODE", "cwd.js", "{}", null);
        SkillExecutionResult second = sandbox.run(packageRoot, "NODE", "cwd.js", "{}", null);

        // Each run starts from an empty directory, so the file written by the first is not there.
        assertThat(first.stdout()).isEqualTo("[\"left-behind.txt\"]");
        assertThat(second.stdout()).isEqualTo("[\"left-behind.txt\"]");
        assertThat(packageRoot.resolve("left-behind.txt")).doesNotExist();
    }

    @Test
    void stopsAScriptThatOverrunsItsBudget() throws IOException {
        Files.writeString(packageRoot.resolve("slow.js"), "setTimeout(() => {}, 60000);");

        assertThatThrownBy(() ->
                sandbox.run(packageRoot, "NODE", "slow.js", "{}", Duration.ofMillis(700)))
                .isInstanceOf(SkillExecutionException.class)
                .satisfies(thrown -> assertThat(((SkillExecutionException) thrown).reason())
                        .isEqualTo(SkillExecutionException.Reason.TIMEOUT));
    }

    @Test
    void capsHowMuchOutputASkillCanReturn() throws IOException {
        Files.writeString(packageRoot.resolve("flood.js"),
                "process.stdout.write('x'.repeat(500000));");

        SkillExecutionResult result = sandbox.run(packageRoot, "NODE", "flood.js", "{}", null);

        assertThat(result.truncated()).isTrue();
        assertThat(result.stdout().length()).isLessThanOrEqualTo(65_536);
    }

    @Test
    void refusesARuntimeThatIsNotOnTheAllowList() {
        assertThatThrownBy(() -> sandbox.run(packageRoot, "BASH", "echo.js", "{}", null))
                .isInstanceOf(SkillExecutionException.class)
                .satisfies(thrown -> assertThat(((SkillExecutionException) thrown).reason())
                        .isEqualTo(SkillExecutionException.Reason.RUNTIME_NOT_ALLOWED));
    }

    @Test
    void refusesAnEntrypointOutsideThePackage() {
        assertThatThrownBy(() -> sandbox.run(packageRoot, "NODE", "../intruder.js", "{}", null))
                .isInstanceOf(SkillExecutionException.class)
                .satisfies(thrown -> assertThat(((SkillExecutionException) thrown).reason())
                        .isEqualTo(SkillExecutionException.Reason.ENTRYPOINT_ESCAPES_PACKAGE));

        assertThatThrownBy(() -> sandbox.run(
                packageRoot, "NODE", outside.resolve("intruder.js").toString(), "{}", null))
                .isInstanceOf(SkillExecutionException.class)
                .satisfies(thrown -> assertThat(((SkillExecutionException) thrown).reason())
                        .isEqualTo(SkillExecutionException.Reason.ENTRYPOINT_ESCAPES_PACKAGE));
    }

    @Test
    void reportsAMissingEntrypointClearly() {
        assertThatThrownBy(() -> sandbox.run(packageRoot, "NODE", "nope.js", "{}", null))
                .isInstanceOf(SkillExecutionException.class)
                .satisfies(thrown -> assertThat(((SkillExecutionException) thrown).reason())
                        .isEqualTo(SkillExecutionException.Reason.ENTRYPOINT_NOT_FOUND));
    }

    @Test
    void reportsAFailingScriptWithoutThrowing() throws IOException {
        Files.writeString(packageRoot.resolve("boom.js"), """
                process.stderr.write('bad input');
                process.exit(3);
                """);

        SkillExecutionResult result = sandbox.run(packageRoot, "NODE", "boom.js", "{}", null);

        assertThat(result.successful()).isFalse();
        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.stderr()).contains("bad input");
    }
}
