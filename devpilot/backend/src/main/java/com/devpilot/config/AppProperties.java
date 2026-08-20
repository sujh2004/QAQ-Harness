package com.devpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Type-safe application configuration.
 *
 * @param cors browser access configuration
 * @param runtime agent runtime configuration
 * @param repository local source repository configuration
 * @param ai model selection
 * @param skill executable skill configuration
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        @DefaultValue RuntimeSettings runtime,
        @DefaultValue Repository repository,
        @DefaultValue Ai ai,
        @DefaultValue Skill skill,
        @DefaultValue Knowledge knowledge) {

    /**
     * Knowledge base configuration.
     *
     * <p>Retrieval settings are configuration rather than constants because the right chunk size and
     * similarity threshold depend on the corpus, and a threshold that is wrong in either direction
     * is a correctness problem: too low invents relevance, too high hides the answer.
     *
     * @param vectorDir directory the per-project vector stores are persisted to
     * @param chunkSize target chunk length in characters
     * @param chunkOverlap characters repeated between neighbouring chunks
     * @param topK how many chunks a search returns at most
     * @param similarityThreshold lowest score a chunk may have and still be returned
     * @param seedDemoDocuments whether the bundled demo corpus is imported at startup when the demo
     *     project has no documents yet
     */
    public record Knowledge(
            @DefaultValue("./data/vector") String vectorDir,
            @DefaultValue("800") int chunkSize,
            @DefaultValue("150") int chunkOverlap,
            @DefaultValue("5") int topK,
            @DefaultValue("0.6") double similarityThreshold,
            @DefaultValue("false") boolean seedDemoDocuments) {
    }

    /**
     * Executable skill configuration.
     *
     * <p>Skills run real scripts, so every limit here is a security control rather than a tuning
     * knob. {@code allowedRuntimes} is the list of interpreters a skill may be launched with;
     * anything not named here cannot be executed at all.
     *
     * @param installDir directory installed skill packages live in
     * @param defaultTimeout wall-clock limit of one skill execution
     * @param maxOutputBytes largest amount of output captured from a skill
     * @param allowedRuntimes runtime name to interpreter command, for example {@code NODE: node}
     * @param environmentAllowList environment variable names a skill process may inherit; every
     *     other variable, including model credentials, is withheld
     * @param marketplaceUrl HTTPS manifest the marketplace reads its catalogue from
     */
    public record Skill(
            @DefaultValue("./data/skills") String installDir,
            @DefaultValue("20s") Duration defaultTimeout,
            @DefaultValue("65536") int maxOutputBytes,
            Map<String, String> allowedRuntimes,
            List<String> environmentAllowList,
            String marketplaceUrl) {
    }

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
     * @param chat streaming chat limits
     */
    public record RuntimeSettings(
            @DefaultValue Recovery recovery,
            @DefaultValue Tool tool,
            @DefaultValue Profile profile,
            @DefaultValue Chat chat) {

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
         * Streaming chat limits.
         *
         * <p>The agent a chat turn runs is configuration rather than a request parameter: letting a
         * caller name the agent would move routing out of the supervisor and into the browser.
         *
         * @param agent agent a chat turn is dispatched to
         * @param streamTimeout how long one SSE stream may stay open before the client must
         *     reconnect with {@code Last-Event-ID}
         * @param maxConcurrentTurns size of the pool running chat turns
         * @param queueCapacity events one slow client may fall behind before it is told to reconnect
         * @param replayLimit largest number of events replayed in one reconnect
         */
        public record Chat(
                @DefaultValue("supervisor") String agent,
                @DefaultValue("10m") Duration streamTimeout,
                @DefaultValue("4") int maxConcurrentTurns,
                @DefaultValue("256") int queueCapacity,
                @DefaultValue("2000") int replayLimit) {
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
