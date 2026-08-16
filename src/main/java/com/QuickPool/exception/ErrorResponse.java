package com.QuickPool.exception;

import lombok.Data;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timestamp;
}