package com.devpilot.runtime.session.payload;

/**
 * Body of an event this build cannot decode, kept verbatim so replay stays lossless.
 *
 * <p>The codec only produces this for non-critical event types. An unknown critical event aborts
 * recovery instead, because silently dropping it would change what the model sees.
 *
 * @param eventType wire name of the undecodable event
 * @param schemaVersion schema version of the undecodable event
 * @param rawJson original payload JSON
 */
public record UnknownPayload(String eventType, int schemaVersion, String rawJson)
        implements SessionEventPayload {
}
