package com.QuickPool.service;

/**
 * Outbound email. The only implementation logs, exactly like OTP does, so the flow
 * is complete and testable without paying for a mail provider. Swapping in SMTP or
 * a transactional-email API later is one class, no callers change.
 */
public interface EmailSender {
    void send(String to, String subject, String body);
}
