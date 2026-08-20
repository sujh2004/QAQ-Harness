package com.devpilot.knowledge.rag;

/**
 * Raised when the knowledge base cannot be used because no embedding model is configured.
 *
 * <p>Reported rather than silently returning no matches: "the knowledge base has nothing on this"
 * and "the knowledge base is not switched on" mean very different things to whoever is asking.
 */
public final class KnowledgeUnavailableException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message safe explanation
     */
    public KnowledgeUnavailableException(String message) {
        super(message);
    }
}
