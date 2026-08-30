package com.QuickPool.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileDto {

    @NotBlank(message = "Please enter your name")
    @Size(min = 2, max = 80, message = "Name must be 2-80 characters")
    private String name;

    // Required: ride updates and offers are emailed until push notifications exist.
    @NotBlank(message = "Please enter your email")
    @Email(message = "Enter a valid email address")
    @Size(max = 160)
    private String email;
}
