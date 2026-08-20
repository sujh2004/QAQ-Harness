package com.devpilot.knowledge.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Model-facing arguments of the knowledge search tool.
 *
 * @param query what to look for, in natural language
 * @param topK how many chunks to return, defaults to the configured value
 * @param similarityThreshold lowest acceptable score, defaults to the configured value
 */
public record SearchKnowledgeArguments(
        @NotBlank @Size(max = 500) String query,
        @Min(1) @Max(20) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold) {
}
