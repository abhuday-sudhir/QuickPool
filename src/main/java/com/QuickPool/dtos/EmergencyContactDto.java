package com.QuickPool.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmergencyContactDto {

    @NotBlank(message = "Please enter a name")
    @Size(max = 80)
    private String name;

    @NotBlank(message = "Please enter a phone number")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Use international format, e.g. +919000000001")
    private String phone;
}
