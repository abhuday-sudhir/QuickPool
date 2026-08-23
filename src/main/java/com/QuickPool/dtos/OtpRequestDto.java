package com.QuickPool.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpRequestDto {

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format, e.g. +919000000001")
    private String phone;
}