package com.example.aikb.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应体 Result 的单元测试。
 */
class ResultTest {

    @Test
    void success_setsCode200AndData() {
        Result<String> r = Result.success("hello");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    void error_setsCodeAndNullData() {
        Result<Object> r = Result.error("boom");
        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getData()).isNull();
        assertThat(r.getMessage()).isEqualTo("boom");
    }
}
