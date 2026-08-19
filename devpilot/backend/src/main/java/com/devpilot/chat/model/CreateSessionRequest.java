package com.devpilot.chat.model;

import jakarta.validation.constraints.Size;

/**
 * Request body for creating a session.
 *
 * @param title optional title; a default is used when omitted
 */
public record CreateSessionRequest(@Size(max = 255) String title) {
}
