package com.QuickPool.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyEmailDto {

    @NotBlank(message = "code is required")
    @Pattern(regexp = "^\\d{6}$", message = "The code is 6 digits")
    private String code;
}
