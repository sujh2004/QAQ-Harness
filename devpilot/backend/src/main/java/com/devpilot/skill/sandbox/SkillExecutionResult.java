package com.devpilot.skill.sandbox;

/**
 * What a skill script produced.
 *
 * @param exitCode process exit status
 * @param stdout captured standard output, already capped
 * @param stderr captured standard error, already capped
 * @param truncated whether output was cut to respect the byte budget
 * @param durationMs wall-clock execution time
 */
public record SkillExecutionResult(
        int exitCode, String stdout, String stderr, boolean truncated, long durationMs) {

    /** @return whether the script exited successfully */
    public boolean successful() {
        return exitCode == 0;
    }
}
