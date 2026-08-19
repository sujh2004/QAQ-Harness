package com.devpilot.runtime.projection;

import java.time.Instant;

/**
 * One message in the projected chat timeline.
 *
 * @param seq sequence number of the source event
 * @param role who produced the message
 * @param agentName producing agent, null for user messages
 * @param content message text
 * @param createdAt when the message was recorded
 */
public record MessageView(long seq, MessageRole role, String agentName, String content, Instant createdAt) {
}
