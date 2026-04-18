package com.devdevgo.jobs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdzunaSearchResponse {

    private Long count;
    private List<AdzunaJobAd> results;
}