package com.QuickPool.service;

import com.QuickPool.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Separate bean on purpose. Revoking on reuse-detection has to survive the exception
 * thrown immediately afterwards, so it needs REQUIRES_NEW — and a self-call inside
 * TokenService would bypass the proxy and silently run in the caller's transaction,
 * getting rolled back with it.
 */
@Service
public class TokenRevoker {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllNow(UUID userId, String reason) {
        refreshTokenRepository.revokeAllForUser(userId, reason);
    }
}
