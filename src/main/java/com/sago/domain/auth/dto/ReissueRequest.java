package com.sago.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(@NotBlank(message = "refreshToken은 필수입니다.") String refreshToken) {
}
