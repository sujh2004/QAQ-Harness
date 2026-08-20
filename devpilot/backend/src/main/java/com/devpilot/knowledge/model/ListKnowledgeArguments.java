package com.devpilot.knowledge.model;

/**
 * Model-facing arguments of the document listing tool.
 *
 * <p>Deliberately empty: the project comes from the session, and listing takes no other input.
 *
 * @param unused placeholder so the schema is a well-formed object
 */
public record ListKnowledgeArguments(String unused) {
}
