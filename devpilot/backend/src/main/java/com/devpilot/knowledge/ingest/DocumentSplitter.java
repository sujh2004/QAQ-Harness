package com.devpilot.knowledge.ingest;

import com.devpilot.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a document into overlapping chunks.
 *
 * <p>Chunking decides what retrieval can ever return, so it splits on structure before length: a
 * Markdown heading starts a new chunk, and paragraphs are kept whole where they fit. That keeps a
 * heading with the text it introduces, which is what makes a retrieved chunk readable on its own.
 *
 * <p>Neighbouring chunks overlap so a sentence that straddles a boundary is still findable from
 * either side.
 */
@Component
public class DocumentSplitter {

    private final int chunkSize;
    private final int overlap;

    /**
     * Creates the splitter.
     *
     * @param appProperties application configuration supplying the chunk size and overlap
     */
    public DocumentSplitter(AppProperties appProperties) {
        this.chunkSize = appProperties.knowledge().chunkSize();
        this.overlap = Math.min(appProperties.knowledge().chunkOverlap(), chunkSize / 2);
    }

    /**
     * Splits cleaned document text.
     *
     * @param text document content
     * @return chunks in document order, never empty for non-blank input
     */
    public List<String> split(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        for (String section : splitOnHeadings(cleaned)) {
            if (section.length() <= chunkSize) {
                chunks.add(section);
            } else {
                chunks.addAll(splitOnLength(section));
            }
        }
        return List.copyOf(chunks);
    }

    /**
     * Removes formatting noise that would otherwise dilute the embedding.
     *
     * @param text raw document content
     * @return cleaned text
     */
    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("[ \t]+\n", "\n")
                .strip();
    }

    /**
     * Cuts the text where a Markdown heading starts, so a heading stays with its own body.
     *
     * @param text cleaned text
     * @return sections in document order
     */
    private static List<String> splitOnHeadings(String text) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            boolean heading = line.startsWith("#");
            if (heading && !current.isEmpty()) {
                sections.add(current.toString().strip());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().strip());
        }
        sections.removeIf(String::isBlank);
        return sections.isEmpty() ? List.of(text) : sections;
    }

    /**
     * Splits an over-long section on paragraph boundaries, falling back to a hard cut.
     *
     * @param section section longer than one chunk
     * @return chunks with the configured overlap
     */
    private List<String> splitOnLength(String section) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < section.length()) {
            int end = Math.min(start + chunkSize, section.length());
            if (end < section.length()) {
                int paragraph = section.lastIndexOf("\n\n", end);
                int sentence = section.lastIndexOf('。', end);
                int period = section.lastIndexOf(". ", end);
                int boundary = Math.max(paragraph, Math.max(sentence, period));
                // Only honour a boundary that leaves a chunk worth having.
                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
            }
            chunks.add(section.substring(start, end).strip());
            if (end >= section.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        chunks.removeIf(String::isBlank);
        return chunks;
    }
}
