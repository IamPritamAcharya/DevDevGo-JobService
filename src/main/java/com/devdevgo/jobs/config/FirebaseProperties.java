package com.devdevgo.jobs.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "firebase")
public class FirebaseProperties {

    /** Set true only when a Firebase service account is configured. */
    private boolean enabled = false;
    private String credentialsPath = "";
    private String projectId = "";
    private String collectionName = "job_listings";

    /** Stores system metadata such as sync_state (cursor persistence for Render restarts). */
    private String metadataCollectionName = "system_metadata";
}
