package com.QuickPool.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${app.fcm.credentials:}")
    private String credentialsPath;

    /**
     * Returns null when no credentials are configured, and {@link com.QuickPool.service.FcmSender}
     * treats that as "push disabled". Deliberately not a hard failure: a developer without the
     * service account file must still be able to boot the backend and use every other feature.
     */
    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("app.fcm.credentials is not set — push notifications are disabled "
                    + "(set FIREBASE_CREDENTIALS to the service account JSON path)");
            return null;
        }
        try (InputStream in = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            // Guard against double-initialisation: Spring may build this context twice in tests.
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("Firebase initialised for project {}", app.getOptions().getProjectId());
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            log.error("Failed to initialise Firebase from {} — push disabled: {}",
                    credentialsPath, e.getMessage());
            return null;
        }
    }
}
