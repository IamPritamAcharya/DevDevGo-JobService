package com.devdevgo.jobs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdzunaJobAd {

    private String id;
    private String title;
    private String description;

    @JsonProperty("redirect_url")
    private String redirectUrl;

    private String created;

    @JsonProperty("salary_min")
    private Double salaryMin;

    @JsonProperty("salary_max")
    private Double salaryMax;

    @JsonProperty("salary_is_predicted")
    private Integer salaryIsPredicted;

    @JsonProperty("contract_type")
    private String contractType;

    @JsonProperty("contract_time")
    private String contractTime;

    private Double latitude;
    private Double longitude;

    private Company company;
    private Location location;
    private Category category;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Company {
        @JsonProperty("display_name")
        private String displayName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        @JsonProperty("display_name")
        private String displayName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Category {
        private String label;
        private String tag;
    }
}