package com.QuickPool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        // DEV ONLY. Replace with real delivery before shipping.
        log.info("========================================");
        log.info("[EMAIL] to={} subject='{}'", to, subject);
        log.info("[EMAIL] {}", body);
        log.info("========================================");
    }
}
