package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseDto {
    private UUID userId;
    private String accessToken;
    private String refreshToken;
}
