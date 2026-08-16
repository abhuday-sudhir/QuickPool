package com.QuickPool.dtos;

import lombok.Data;

@Data
public class OtpVerifyDto {
    private String phone;
    private String otp;
}
