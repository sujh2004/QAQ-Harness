package com.devpilot.runtime.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: result limits and redaction are applied around the provider, not requested from it.
 */
@SpringBootTest
@ActiveProfiles("test")
class ToolResultLimitTest {

    @Autowired
    private ToolResultProcessor resultProcessor;

    @Autowired
    private SensitiveDataRedactor redactor;

    @Test
    void keepsResultsThatFitWithinTheDeclaredLimits() {
        ToolDefinition definition = definition(10, 65_536);
        ToolResult raw = ToolResult.of(List.of("a", "b"), 2, "Found 2 rows");

        ProcessedToolResult processed = resultProcessor.process(raw, definition);

        assertThat(processed.truncated()).isFalse();
        assertThat(processed.data()).isEqualTo(List.of("a", "b"));
        assertThat(processed.modelSummary()).isEqualTo("Found 2 rows");
    }

    @Test
    void dropsItemsBeyondTheDeclaredItemLimit() {
        ToolDefinition definition = definition(3, 65_536);
        List<String> rows = IntStream.range(0, 40).mapToObj(index -> "row-" + index).toList();

        ProcessedToolResult processed = resultProcessor.process(ToolResult.of(rows, 40, "Found 40 rows"), definition);

        assertThat(processed.truncated()).isTrue();
        assertThat(processed.data()).isEqualTo(List.of("row-0", "row-1", "row-2"));
    }

    @Test
    void replacesAnOversizedResultWithAPreview() {
        ToolDefinition definition = definition(10, 128);

        ProcessedToolResult processed =
                resultProcessor.process(ToolResult.of("x".repeat(4_000), 1, "Large document"), definition);

        assertThat(processed.truncated()).isTrue();
        assertThat(processed.data()).isInstanceOf(Map.class);
        Map<?, ?> placeholder = (Map<?, ?>) processed.data();
        assertThat(placeholder.get("truncated")).isEqualTo(true);
        assertThat(placeholder.get("sizeBytes")).isEqualTo(4_002);
        assertThat(placeholder.get("preview").toString()).hasSizeLessThan(600);
    }

    @Test
    void masksCredentialShapedArgumentValues() {
        Map<String, Object> redacted = redactor.redactArguments(Map.of(
                "apiKey", "sk-0123456789abcdefghij",
                "keyword", "createOrder",
                "nested", Map.of("accessToken", "0123456789abcdefghij")));

        assertThat(redacted.get("apiKey")).isEqualTo(SensitiveDataRedactor.MASK);
        assertThat(redacted.get("keyword")).isEqualTo("createOrder");
        assertThat(((Map<?, ?>) redacted.get("nested")).get("accessToken"))
                .isEqualTo(SensitiveDataRedactor.MASK);
    }

    @Test
    void masksCredentialShapedTextInsideResults() {
        ToolDefinition definition = definition(10, 65_536);
        ToolResult raw = ToolResult.of(
                List.of("Authorization: Bearer 0123456789abcdefghij"),
                1,
                "header was Authorization: Bearer 0123456789abcdefghij");

        ProcessedToolResult processed = resultProcessor.process(raw, definition);

        assertThat(processed.data().toString()).doesNotContain("0123456789abcdefghij");
        assertThat(processed.modelSummary()).doesNotContain("0123456789abcdefghij");
        assertThat(processed.persistSummary()).contains(SensitiveDataRedactor.MASK);
    }

    private static ToolDefinition definition(int maxItems, int maxBytes) {
        return ToolDefinition.builder("limitProbe", FakeTools.NoteArguments.class)
                .description("Limit probe")
                .requiredPermission(ToolPermission.LOG_READ)
                .maxResultItems(maxItems)
                .maxResultBytes(maxBytes)
                .build();
    }
}
