package com.devpilot.agent.tool.delegate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Model-facing arguments of a delegation tool.
 *
 * <p>Only the task travels. The project and session come from the runtime, so a supervisor cannot
 * point a specialist at data the session does not own.
 *
 * @param task what the specialist should find out
 */
public record DelegationArguments(@NotBlank @Size(max = 2000) String task) {
}
