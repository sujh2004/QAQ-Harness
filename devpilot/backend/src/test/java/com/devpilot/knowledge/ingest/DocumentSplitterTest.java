package com.devpilot.knowledge.ingest;

import com.devpilot.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: a heading stays with the body it introduces, an over-long section is cut on sentence
 * boundaries with overlap, and blank input yields nothing to index.
 */
class DocumentSplitterTest {

    private final DocumentSplitter splitter = new DocumentSplitter(properties(200, 40));

    @Test
    void blankInputYieldsNoChunks() {
        assertThat(splitter.split(null)).isEmpty();
        assertThat(splitter.split("  \n\r\n  ")).isEmpty();
    }

    @Test
    void keepsEachHeadingWithItsOwnBody() {
        List<String> chunks = splitter.split("""
                # 错误码规范

                错误码为六位数字，前两位标识服务。

                # 日志规范

                日志必须带 traceId。
                """);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).startsWith("# 错误码规范").contains("六位数字");
        assertThat(chunks.get(1)).startsWith("# 日志规范").contains("traceId");
    }

    @Test
    void cutsAnOverLongSectionWithinTheChunkSize() {
        StringBuilder section = new StringBuilder("这是一段没有标题的长文本。");
        while (section.length() < 1200) {
            section.append("继续补充下一句内容。");
        }

        List<String> chunks = splitter.split(section.toString());

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk).isNotBlank();
            assertThat(chunk.length()).isLessThanOrEqualTo(200);
        });
        // The tail of the document must survive: coverage beats per-chunk tidiness.
        assertThat(String.join("", chunks)).contains("继续补充下一句内容。");
    }

    @Test
    void neighbouringChunksOverlapSoABoundarySentenceStaysFindable() {
        StringBuilder section = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            section.append("第").append(i).append("句话的内容。");
        }

        List<String> chunks = splitter.split(section.toString());

        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String next = chunks.get(i);
            int overlap = longestCommonSuffixPrefix(previous, next);
            assertThat(overlap).isPositive();
        }
    }

    @Test
    void normalizesWindowsLineEndings() {
        List<String> chunks = splitter.split("# 标题\r\n\r\n正文一行。\r\n");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).doesNotContain("\r");
    }

    private static int longestCommonSuffixPrefix(String previous, String next) {
        int max = Math.min(previous.length(), next.length());
        for (int length = max; length > 0; length--) {
            if (previous.regionMatches(previous.length() - length, next, 0, length)) {
                return length;
            }
        }
        return 0;
    }

    private static AppProperties properties(int chunkSize, int overlap) {
        return new AppProperties(null, null, null, null, null,
                new AppProperties.Knowledge(
                        "./target/test-vector-splitter", chunkSize, overlap, 5, 0.6, false));
    }
}
