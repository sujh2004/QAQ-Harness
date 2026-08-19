package com.devpilot.runtime.session.payload;

import java.util.List;

/**
 * Opens a session event stream and freezes the runtime configuration it will replay under.
 *
 * @param projectId owning project, null until the project module exists
 * @param profileVersion agent profile version pinned for the whole session
 * @param capabilities capability identifiers available to this session
 * @param title human-readable session title
 */
public record SessionCreatedPayload(
        Long projectId,
        String profileVersion,
        List<String> capabilities,
        String title) implements SessionEventPayload {
}
