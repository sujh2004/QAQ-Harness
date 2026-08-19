package com.devpilot.runtime.tool;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Everything the runtime must know about a tool before a model is allowed to call it.
 *
 * <p>A tool that does not declare its limits, permission and side-effect level cannot be
 * registered, so the execution pipeline never has to guess.
 *
 * @param name globally unique, version-stable name the model sees
 * @param version implementation version recorded on every call
 * @param description model-facing description
 * @param inputSchema JSON Schema of the arguments, published to the model
 * @param argumentType record the arguments are bound to and bean-validated against
 * @param sideEffect whether the tool only reads
 * @param concurrency whether concurrent calls are safe
 * @param timeout maximum provider execution time
 * @param maxResultItems maximum number of items kept in the result
 * @param maxResultBytes maximum serialized result size
 * @param requiredPermission capability the calling scope must hold
 * @param requiresApproval whether a human must approve the exact arguments first
 * @param displayIntent how the UI should render the call
 */
public record ToolDefinition(
        String name,
        String version,
        String description,
        Map<String, Object> inputSchema,
        Class<?> argumentType,
        SideEffectLevel sideEffect,
        ConcurrencyMode concurrency,
        Duration timeout,
        int maxResultItems,
        int maxResultBytes,
        ToolPermission requiredPermission,
        boolean requiresApproval,
        ToolDisplayIntent displayIntent) {

    /** Validates that the declaration is complete. */
    public ToolDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(argumentType, "argumentType");
        Objects.requireNonNull(sideEffect, "sideEffect");
        Objects.requireNonNull(concurrency, "concurrency");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(requiredPermission, "requiredPermission");
        Objects.requireNonNull(displayIntent, "displayIntent");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Tool " + name + " must declare a positive timeout");
        }
        if (maxResultItems <= 0) {
            throw new IllegalArgumentException("Tool " + name + " must declare a positive maxResultItems");
        }
        if (maxResultBytes <= 0) {
            throw new IllegalArgumentException("Tool " + name + " must declare a positive maxResultBytes");
        }
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }

    /**
     * Starts a declaration.
     *
     * @param name globally unique tool name
     * @param argumentType record the arguments are bound to
     * @return builder seeded with the required identity
     */
    public static Builder builder(String name, Class<?> argumentType) {
        return new Builder(name, argumentType);
    }

    /** Fluent builder for {@link ToolDefinition}. */
    public static final class Builder {

        private final String name;
        private final Class<?> argumentType;
        private String version = "1";
        private String description = "";
        private Map<String, Object> inputSchema = Map.of();
        private SideEffectLevel sideEffect = SideEffectLevel.READ_ONLY;
        private ConcurrencyMode concurrency = ConcurrencyMode.CONCURRENCY_SAFE;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxResultItems = 100;
        private int maxResultBytes = 65_536;
        private ToolPermission requiredPermission;
        private boolean requiresApproval;
        private ToolDisplayIntent displayIntent = ToolDisplayIntent.GENERIC;

        private Builder(String name, Class<?> argumentType) {
            this.name = name;
            this.argumentType = argumentType;
        }

        /**
         * @param value implementation version
         * @return this builder
         */
        public Builder version(String value) {
            this.version = value;
            return this;
        }

        /**
         * @param value model-facing description
         * @return this builder
         */
        public Builder description(String value) {
            this.description = value;
            return this;
        }

        /**
         * @param value JSON Schema of the arguments
         * @return this builder
         */
        public Builder inputSchema(Map<String, Object> value) {
            this.inputSchema = value;
            return this;
        }

        /**
         * @param value whether the tool only reads
         * @return this builder
         */
        public Builder sideEffect(SideEffectLevel value) {
            this.sideEffect = value;
            return this;
        }

        /**
         * @param value whether concurrent calls are safe
         * @return this builder
         */
        public Builder concurrency(ConcurrencyMode value) {
            this.concurrency = value;
            return this;
        }

        /**
         * @param value maximum provider execution time
         * @return this builder
         */
        public Builder timeout(Duration value) {
            this.timeout = value;
            return this;
        }

        /**
         * @param value maximum number of items kept in the result
         * @return this builder
         */
        public Builder maxResultItems(int value) {
            this.maxResultItems = value;
            return this;
        }

        /**
         * @param value maximum serialized result size
         * @return this builder
         */
        public Builder maxResultBytes(int value) {
            this.maxResultBytes = value;
            return this;
        }

        /**
         * @param value capability the calling scope must hold
         * @return this builder
         */
        public Builder requiredPermission(ToolPermission value) {
            this.requiredPermission = value;
            return this;
        }

        /**
         * @param value whether a human must approve the exact arguments first
         * @return this builder
         */
        public Builder requiresApproval(boolean value) {
            this.requiresApproval = value;
            return this;
        }

        /**
         * @param value how the UI should render the call
         * @return this builder
         */
        public Builder displayIntent(ToolDisplayIntent value) {
            this.displayIntent = value;
            return this;
        }

        /**
         * Builds the definition.
         *
         * @return validated tool definition
         */
        public ToolDefinition build() {
            return new ToolDefinition(
                    name, version, description, inputSchema, argumentType, sideEffect, concurrency,
                    timeout, maxResultItems, maxResultBytes, requiredPermission, requiresApproval,
                    displayIntent);
        }
    }
}
