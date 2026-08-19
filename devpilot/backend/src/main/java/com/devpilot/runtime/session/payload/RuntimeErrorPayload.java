package com.devpilot.runtime.session.payload;

/**
 * Records a runtime failure so a reloaded page can still explain what went wrong.
 *
 * <p>This is informational: lifecycle state is closed by the matching terminal events, not by this
 * one, so a replay that cannot understand it may skip it.
 *
 * @param errorCode stable error identifier
 * @param message safe failure message without stack traces
 * @param scope where the failure happened, for example {@code TURN} or {@code TOOL}
 */
public record RuntimeErrorPayload(String errorCode, String message, String scope)
        implements SessionEventPayload {
}
