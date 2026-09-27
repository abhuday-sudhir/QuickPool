package com.QuickPool.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterDeviceDto {

    @NotBlank
    private String token;

    private String platform = "android";
}
