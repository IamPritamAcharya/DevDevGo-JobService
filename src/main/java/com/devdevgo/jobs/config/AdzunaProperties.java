package com.devdevgo.jobs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "adzuna")
public class AdzunaProperties {

    /**
     * Official API root from Adzuna docs.
     */
    private String baseUrl = "https://api.adzuna.com/v1/api";

    private String appId;
    private String appKey;

    /**
     * Default to India.
     */
    private String country = "in";

    private int defaultResultsPerPage = 10;
}