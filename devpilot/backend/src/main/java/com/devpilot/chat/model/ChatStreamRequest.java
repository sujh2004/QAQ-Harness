package com.devpilot.chat.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body of the streaming chat endpoint.
 *
 * <p>The agent that answers is not a parameter: routing is the supervisor's job, and letting a
 * browser name the agent would move that decision out of the platform. A blank {@code message} is
 * meaningful — it means "attach to this session and follow it", which is how a client resumes after
 * a dropped connection without starting a second turn.
 *
 * @param projectId project the turn may read
 * @param sessionId session to continue, omit to open a new one
 * @param message the question, omit to attach to an existing session without starting a turn
 */
public record ChatStreamRequest(
        @NotNull Long projectId,
        @Size(max = 64) String sessionId,
        @Size(max = 4000) String message) {

    /** @return whether this request asks for a new turn rather than only attaching to the stream */
    public boolean startsTurn() {
        return message != null && !message.isBlank();
    }
}
