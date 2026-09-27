package com.QuickPool.service;

import com.QuickPool.entity.DeviceToken;
import com.QuickPool.repository.DeviceTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class DeviceTokenService {

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    /**
     * Idempotent: the app re-registers on every cold start, so this is called far more often
     * than the token actually changes. An existing row is re-pointed at the current user
     * rather than duplicated — FCM hands the same token to whoever installs next on a device,
     * and leaving it on the old owner would push their bookings to someone else's phone.
     */
    @Transactional
    public void register(UUID userId, String token, String platform) {
        DeviceToken row = deviceTokenRepository.findByToken(token).orElseGet(DeviceToken::new);
        row.setUserId(userId);
        row.setToken(token);
        row.setPlatform(platform == null || platform.isBlank() ? "android" : platform);
        row.setLastSeenAt(LocalDateTime.now());
        deviceTokenRepository.save(row);
    }

    /** Called on logout. Silent when the token is already gone — logging out twice is not an error. */
    @Transactional
    public void unregister(String token) {
        deviceTokenRepository.deleteByToken(token);
    }

    public List<String> tokensFor(UUID userId) {
        return deviceTokenRepository.findByUserId(userId).stream()
                .map(DeviceToken::getToken)
                .toList();
    }

    /** Drops tokens FCM has told us are dead, so they stop being retried on every notification. */
    @Transactional
    public void prune(List<String> tokens) {
        tokens.forEach(deviceTokenRepository::deleteByToken);
        if (!tokens.isEmpty()) {
            log.info("Pruned {} unregistered device token(s)", tokens.size());
        }
    }
}
