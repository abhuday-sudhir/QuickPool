package com.QuickPool.service;

import com.QuickPool.enums.NotificationType;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FcmSender {

    // ObjectProvider, not a plain injection: FirebaseConfig returns null when no service
    // account is configured, and a required injection would refuse to start the context.
    @Autowired
    private ObjectProvider<FirebaseMessaging> messaging;

    @Autowired
    private DeviceTokenService deviceTokenService;

    /**
     * Best-effort, and async so a slow FCM round trip never holds up the booking transaction
     * that triggered it. The inbox row is already committed by the time this runs, so a failure
     * here costs a buzz, never the notification itself.
     */
    @Async
    public void send(UUID userId, String title, String body, NotificationType type, UUID entityId) {
        FirebaseMessaging fcm = messaging.getIfAvailable();
        if (fcm == null) {
            return; // push disabled — inbox-only, already logged at startup
        }

        List<String> tokens = deviceTokenService.tokensFor(userId);
        if (tokens.isEmpty()) {
            return; // user has never opened the app on a device, or denied the permission
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", type.name());
        if (entityId != null) {
            data.put("entityId", entityId.toString());
        }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("quickpool_rides")
                                .build())
                        .build())
                .build();

        try {
            BatchResponse response = fcm.sendEachForMulticast(message);
            prunePermanentFailures(response, tokens);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM send failed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * UNREGISTERED / INVALID_ARGUMENT mean the token is dead for good — the app was uninstalled
     * or the token rotated. Left in the table they would be retried on every future notification,
     * so they are deleted. Transient failures are left alone and simply retried next time.
     */
    private void prunePermanentFailures(BatchResponse response, List<String> tokens) {
        List<String> dead = new ArrayList<>();
        List<SendResponse> results = response.getResponses();
        for (int i = 0; i < results.size(); i++) {
            SendResponse r = results.get(i);
            if (r.isSuccessful()) {
                continue;
            }
            MessagingErrorCode code = r.getException() == null
                    ? null : r.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                dead.add(tokens.get(i));
            }
        }
        deviceTokenService.prune(dead);
    }
}
