package com.devpilot.runtime.session;

import java.util.List;

/**
 * Runtime metadata frozen when a session event stream is opened.
 *
 * <p>Pinning the profile version and capability set at creation time keeps a later replay honest:
 * the stream is always interpreted with the configuration it actually ran under.
 *
 * @param sessionId session identifier
 * @param projectId owning project, null until the project module exists
 * @param profileVersion agent profile version used for the whole session
 * @param capabilities capability identifiers available to the session
 * @param title human-readable session title
 */
public record SessionStreamDescriptor(
        String sessionId,
        Long projectId,
        String profileVersion,
        List<String> capabilities,
        String title) {
}
