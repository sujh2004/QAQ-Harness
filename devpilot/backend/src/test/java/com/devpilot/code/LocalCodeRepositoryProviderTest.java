package com.devpilot.code;

import com.devpilot.code.model.CodeMatch;
import com.devpilot.code.model.ListFilesRequest;
import com.devpilot.code.model.ListFilesResult;
import com.devpilot.code.model.ReadCodeFileRequest;
import com.devpilot.code.model.ReadCodeFileResult;
import com.devpilot.code.model.SearchCodeRequest;
import com.devpilot.code.model.SearchCodeResult;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.service.ProjectService;
import org.junit.jupiter.api.Assumptions;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract: repository reads stay inside the configured root, refuse sensitive files and are
 * bounded, whatever path a caller supplies.
 */
@SpringBootTest
@ActiveProfiles("test")
class LocalCodeRepositoryProviderTest {

    @Autowired
    private CodeRepositoryService repository;

    @Autowired
    private ProjectService projectService;

    @TempDir
    private Path repositoryRoot;

    @TempDir
    private Path outside;

    private long projectId;

    @BeforeEach
    void seedRepository() throws IOException {
        Files.createDirectories(repositoryRoot.resolve("src/main/java/com/demo/order"));
        Files.writeString(repositoryRoot.resolve("src/main/java/com/demo/order/OrderService.java"), """
                package com.demo.order;

                public class OrderService {
                    public Order createOrder(CreateOrderRequest request) {
                        return null;
                    }
                }
                """);
        Files.writeString(repositoryRoot.resolve("src/main/java/com/demo/order/OrderMapper.java"), """
                package com.demo.order;

                public interface OrderMapper {
                    Order selectById(Long id);
                }
                """);
        Files.writeString(repositoryRoot.resolve("README.md"), "# demo\ncreateOrder is documented here\n");
        Files.writeString(repositoryRoot.resolve(".env"), "SECRET=super-secret-value\n");
        Files.writeString(repositoryRoot.resolve("application-prod.yml"), "password: super-secret-value\n");
        Files.write(repositoryRoot.resolve("logo.png"), new byte[] {1, 2, 0, 3, 4});
        Files.writeString(outside.resolve("passwd.txt"), "root:x:0:0\n");

        projectId = projectService.create(new CreateProjectRequest(
                        "code-test", "code-" + UUID.randomUUID().toString().substring(0, 8),
                        null, repositoryRoot.toAbsolutePath().toString(), null))
                .id();
    }

    @Test
    void findsAKeywordWithItsFileLineAndContext() {
        SearchCodeResult result = repository.searchCode(
                new SearchCodeRequest(projectId, "createOrder", "*.java", 30));

        assertThat(result.matches()).isNotEmpty();
        CodeMatch match = result.matches().getFirst();
        assertThat(match.filePath()).isEqualTo("src/main/java/com/demo/order/OrderService.java");
        assertThat(match.lineNumber()).isEqualTo(4);
        assertThat(match.lineText()).contains("createOrder");
        assertThat(match.contextBefore()).isNotEmpty();
        assertThat(match.contextAfter()).isNotEmpty();
    }

    @Test
    void restrictsTheSearchToTheRequestedFilePattern() {
        SearchCodeResult javaOnly = repository.searchCode(
                new SearchCodeRequest(projectId, "createOrder", "*.java", 30));
        SearchCodeResult markdownOnly = repository.searchCode(
                new SearchCodeRequest(projectId, "createOrder", "*.md", 30));

        assertThat(javaOnly.matches()).allSatisfy(match ->
                assertThat(match.filePath()).endsWith(".java"));
        assertThat(markdownOnly.matches()).singleElement()
                .satisfies(match -> assertThat(match.filePath()).isEqualTo("README.md"));
    }

    @Test
    void returnsNothingForAKeywordThatDoesNotOccur() {
        SearchCodeResult result = repository.searchCode(
                new SearchCodeRequest(projectId, "thisKeywordDoesNotExist", null, 30));

        assertThat(result.matches()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void neverSearchesSensitiveOrBinaryFiles() {
        SearchCodeResult secrets = repository.searchCode(
                new SearchCodeRequest(projectId, "super-secret-value", null, 30));

        assertThat(secrets.matches()).isEmpty();
    }

    @Test
    void stopsAtTheRequestedMatchLimit() {
        SearchCodeResult result = repository.searchCode(
                new SearchCodeRequest(projectId, "order", null, 1));

        assertThat(result.matches()).hasSize(1);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void listsFilesAndHidesTheSensitiveOnes() {
        ListFilesResult result = repository.listFiles(new ListFilesRequest(projectId, "", 10, 100));

        assertThat(result.files()).contains("README.md", "src/main/java/com/demo/order/OrderService.java");
        assertThat(result.files()).doesNotContain(".env", "application-prod.yml");
    }

    @Test
    void stopsListingAtTheRequestedLimit() {
        ListFilesResult result = repository.listFiles(new ListFilesRequest(projectId, "", 10, 1));

        assertThat(result.files()).hasSize(1);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void readsTheRequestedLineRange() {
        ReadCodeFileResult result = repository.readFile(new ReadCodeFileRequest(
                projectId, "src/main/java/com/demo/order/OrderService.java", 3, 5));

        assertThat(result.startLine()).isEqualTo(3);
        assertThat(result.endLine()).isEqualTo(5);
        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines().getFirst()).contains("public class OrderService");
        assertThat(result.totalLines()).isGreaterThan(5);
    }

    @Test
    void reportsTruncationWhenTheRangeRunsPastTheEndOfTheFile() {
        ReadCodeFileResult result = repository.readFile(new ReadCodeFileRequest(
                projectId, "README.md", 1, 5_000));

        assertThat(result.truncated()).isTrue();
        assertThat(result.endLine()).isEqualTo(result.totalLines());
    }

    @Test
    void refusesToEscapeTheRepositoryWithParentSegments() {
        assertThatThrownBy(() -> repository.readFile(new ReadCodeFileRequest(
                projectId, "../../../../etc/passwd", 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .satisfies(thrown -> assertThat(((RepositoryAccessException) thrown).reason())
                        .isEqualTo(RepositoryAccessException.Reason.PATH_ESCAPES_REPOSITORY));
    }

    @Test
    void refusesAnAbsolutePath() {
        assertThatThrownBy(() -> repository.readFile(new ReadCodeFileRequest(
                projectId, outside.resolve("passwd.txt").toAbsolutePath().toString(), 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .satisfies(thrown -> assertThat(((RepositoryAccessException) thrown).reason())
                        .isEqualTo(RepositoryAccessException.Reason.PATH_ESCAPES_REPOSITORY));
    }

    @Test
    void refusesASymbolicLinkThatPointsOutsideTheRepository() throws IOException {
        Path link = repositoryRoot.resolve("escape.md");
        try {
            Files.createSymbolicLink(link, outside.resolve("passwd.txt"));
        } catch (IOException | UnsupportedOperationException notPermitted) {
            Assumptions.abort("This platform does not allow creating symbolic links");
            return;
        }

        assertThatThrownBy(() -> repository.readFile(new ReadCodeFileRequest(projectId, "escape.md", 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .satisfies(thrown -> assertThat(((RepositoryAccessException) thrown).reason())
                        .isEqualTo(RepositoryAccessException.Reason.PATH_ESCAPES_REPOSITORY));
    }

    @Test
    void refusesBlacklistedFilesByName() {
        assertThatThrownBy(() -> repository.readFile(new ReadCodeFileRequest(projectId, ".env", 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .hasMessageContaining("blacklist");

        assertThatThrownBy(() -> repository.readFile(
                new ReadCodeFileRequest(projectId, "application-prod.yml", 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .hasMessageContaining("blacklist");
    }

    @Test
    void refusesFileTypesTheCodeToolsDoNotUnderstand() {
        assertThatThrownBy(() -> repository.readFile(new ReadCodeFileRequest(projectId, "logo.png", 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .satisfies(thrown -> assertThat(((RepositoryAccessException) thrown).reason())
                        .isEqualTo(RepositoryAccessException.Reason.UNSUPPORTED_FILE));
    }

    @Test
    void refusesAFileLargerThanTheReadLimit() throws IOException {
        Files.writeString(repositoryRoot.resolve("huge.txt"), "x".repeat(300_000));

        assertThatThrownBy(() -> repository.readFile(new ReadCodeFileRequest(projectId, "huge.txt", 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .hasMessageContaining("read limit");
    }

    @Test
    void reportsAMissingFileClearly() {
        assertThatThrownBy(() -> repository.readFile(
                new ReadCodeFileRequest(projectId, "src/main/java/Nope.java", 1, 10)))
                .isInstanceOf(RepositoryAccessException.class)
                .satisfies(thrown -> assertThat(((RepositoryAccessException) thrown).reason())
                        .isEqualTo(RepositoryAccessException.Reason.PATH_NOT_FOUND));
    }

    @Test
    void handlesNonAsciiPathsAndContent() throws IOException {
        Path directory = repositoryRoot.resolve("src/main/java/com/demo/订单");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("订单说明.md"), "订单服务的 createOrder 说明\n",
                StandardCharsets.UTF_8);

        SearchCodeResult result = repository.searchCode(
                new SearchCodeRequest(projectId, "订单服务", null, 30));

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.filePath()).isEqualTo("src/main/java/com/demo/订单/订单说明.md");
            assertThat(match.lineText()).contains("createOrder");
        });
    }
}
