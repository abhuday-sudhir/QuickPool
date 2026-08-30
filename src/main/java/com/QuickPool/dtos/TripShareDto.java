package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TripShareDto {
    private String token;
    /** Ready to paste into a message. */
    private String url;
    private LocalDateTime expiresAt;
}
