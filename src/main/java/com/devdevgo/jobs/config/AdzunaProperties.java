package com.devdevgo.jobs.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "adzuna")
public class AdzunaProperties {

    /** Official Adzuna API base URL. */
    private String baseUrl = "https://api.adzuna.com/v1/api";
    private String appId = "";
    private String appKey = "";
    private String country = "in";
    private int defaultResultsPerPage = 10;
}
