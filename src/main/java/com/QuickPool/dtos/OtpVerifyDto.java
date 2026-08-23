package com.QuickPool.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpVerifyDto {

    @NotBlank(message = "phone is required")
    private String phone;

    @NotBlank(message = "otp is required")
    @Pattern(regexp = "^\\d{6}$", message = "otp must be exactly 6 digits")
    private String otp;
}