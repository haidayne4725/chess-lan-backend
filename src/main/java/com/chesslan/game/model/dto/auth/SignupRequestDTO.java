package com.chesslan.game.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequestDTO(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username may contain letters, numbers and underscore only")
        String username,
        @NotBlank
        @Size(min = 6, max = 72)
        String password
) {
}
