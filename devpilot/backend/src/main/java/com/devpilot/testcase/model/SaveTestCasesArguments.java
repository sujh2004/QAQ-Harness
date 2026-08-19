package com.devpilot.testcase.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Model-facing arguments of the test case writer.
 *
 * <p>This is the only tool in the MVP that changes state, so its bounds are deliberately tight: a
 * single call may write at most twenty cases, and every field is validated before the provider is
 * reached.
 *
 * @param projectId owning project; must match the project of the calling session
 * @param sourceSessionId session the cases were designed in
 * @param cases the cases to store
 */
public record SaveTestCasesArguments(
        @NotNull Long projectId,
        @Size(max = 64) String sourceSessionId,
        @NotEmpty @Size(max = 20, message = "at most 20 cases per call") @Valid List<TestCaseInput> cases) {

    /**
     * One test case to store.
     *
     * @param title what the case verifies
     * @param priority P0 to P3
     * @param precondition state the case assumes
     * @param steps ordered steps
     * @param expectedResult what should happen
     */
    public record TestCaseInput(
            @NotBlank @Size(max = 255) String title,
            @Pattern(regexp = "P0|P1|P2|P3", message = "must be one of P0, P1, P2, P3") String priority,
            @Size(max = 2000) String precondition,
            @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 500) String> steps,
            @NotBlank @Size(max = 2000) String expectedResult) {
    }
}
