package com.devpilot.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void createsStableSuccessEnvelope() {
        Result<String> result = Result.success("ready");

        assertThat(result.code()).isZero();
        assertThat(result.message()).isEqualTo("success");
        assertThat(result.data()).isEqualTo("ready");
    }

    @Test
    void createsStableFailureEnvelope() {
        Result<Void> result = Result.failure(ErrorCode.INVALID_ARGUMENT, "name is required");

        assertThat(result.code()).isEqualTo(40000);
        assertThat(result.message()).isEqualTo("name is required");
        assertThat(result.data()).isNull();
    }
}

