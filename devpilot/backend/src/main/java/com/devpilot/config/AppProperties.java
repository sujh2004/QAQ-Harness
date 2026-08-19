package com.devpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Type-safe application configuration.
 *
 * @param cors browser access configuration
 * @param runtime agent runtime configuration
 * @param repository local source repository configuration
 * @param ai model selection
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        @DefaultValue RuntimeSettings runtime,
        @DefaultValue Repository repository,
        @DefaultValue Ai ai) {

    /**
     * Browser access configuration.
     *
     * @param allowedOrigins origins allowed to call the backend
     */
    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Model selection. Model names live here rather than in business code, so changing a model is
     * configuration rather than a code change.
     *
     * @param chatModel model used for the default chat route
     * @param embeddingModel model used for embeddings, from the knowledge phase onwards
     */
    public record Ai(
            @DefaultValue("qwen-plus") String chatModel,
            @DefaultValue("text-embedding-v3") String embeddingModel) {
    }

    /**
     * Local source repository configuration.
     *
     * @param baseDir directory that relative project repository paths resolve against; blank falls
     *     back to the process working directory so no user home is hard-coded
     * @param maxFileBytes largest file the code tools will read
     * @param maxReadLines largest line range a single read may return
     * @param deniedFilePatterns glob patterns of files the code tools must never open
     * @param searchableExtensions file extensions the code tools may open, without the leading dot
     */
    public record Repository(
            @DefaultValue("") String baseDir,
            @DefaultValue("262144") int maxFileBytes,
            @DefaultValue("2000") int maxReadLines,
            List<String> deniedFilePatterns,
            List<String> searchableExtensions) {
    }

    /**
     * Agent runtime configuration.
     *
     * @param recovery startup recovery of dangling lifecycle state
     * @param tool tool execution limits
     * @param profile agent profile selection
     */
    public record RuntimeSettings(
            @DefaultValue Recovery recovery,
            @DefaultValue Tool tool,
            @DefaultValue Profile profile) {

        /**
         * Startup recovery configuration.
         *
         * @param enabled whether dangling turns are closed when the application starts
         */
        public record Recovery(@DefaultValue("true") boolean enabled) {
        }

        /**
         * Agent profile selection. Phase 5 replaces this with a versioned profile loader; until
         * then the value is still recorded on every session so replays stay honest.
         *
         * @param version profile version pinned when a session is created
         */
        public record Profile(@DefaultValue("standard@1") String version) {
        }

        /**
         * Default tool execution limits. A tool definition may narrow them but never widen them.
         *
         * @param defaultTimeout timeout applied when a tool declares none
         * @param maxResultItems upper bound on items kept in a tool result
         * @param maxResultBytes upper bound on serialized tool result size
         * @param maxConcurrentExecutions size of the tool execution thread pool
         * @param mutatingAllowList tools allowed to change state; empty until Phase 5 adds
         *     {@code saveTestCases} and the knowledge index writer
         */
        public record Tool(
                @DefaultValue("30s") Duration defaultTimeout,
                @DefaultValue("100") int maxResultItems,
                @DefaultValue("65536") int maxResultBytes,
                @DefaultValue("8") int maxConcurrentExecutions,
                List<String> mutatingAllowList) {
        }
    }
}
