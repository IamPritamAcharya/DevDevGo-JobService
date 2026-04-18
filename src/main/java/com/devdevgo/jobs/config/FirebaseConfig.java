package com.devdevgo.jobs.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    private FirebaseApp app;

    @Bean
    public FirebaseApp firebaseApp(FirebaseProperties properties, ResourceLoader resourceLoader) throws IOException {

        if (!FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseApp.getInstance().delete();
                log.info("Deleted stale FirebaseApp instance (hot-reload)");
            } catch (Exception e) {
                log.warn("Could not delete existing FirebaseApp: {}", e.getMessage());
            }
        }

        // Prefer the env-var JSON (used on Render)
        String credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON");
        InputStream in;
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            log.info("Loading Firebase credentials from FIREBASE_CREDENTIALS_JSON env var");
            in = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        } else {
            // Fall back to file path (used locally)
            String credentialsPath = properties.getCredentialsPath();
            if (credentialsPath == null || credentialsPath.isBlank()) {
                throw new IllegalStateException(
                    "Either FIREBASE_CREDENTIALS_JSON env var or firebase.credentials-path must be set");
            }
            log.info("Loading Firebase credentials from path: {}", credentialsPath);
            Resource resource = resourceLoader.getResource(credentialsPath);
            in = resource.getInputStream();
        }

        try (in) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(credentials);
            if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
                builder.setProjectId(properties.getProjectId());
            }
            app = FirebaseApp.initializeApp(builder.build());
            log.info("FirebaseApp initialized (project={})", properties.getProjectId());
            return app;
        }
    }

    @Bean
    @DependsOn("firebaseApp")
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    @PreDestroy
    public void cleanup() {
        if (app != null) {
            try {
                app.delete();
                log.info("FirebaseApp deleted on context shutdown");
            } catch (Exception e) {
                log.warn("Error deleting FirebaseApp on shutdown: {}", e.getMessage());
            }
        }
    }
}