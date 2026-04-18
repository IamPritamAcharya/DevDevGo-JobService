package com.devdevgo.jobs.model;

import java.util.List;

public record JobListing(
        String id,
        String title,
        String company,
        String location,
        String description,
        String redirectUrl,
        String createdAt,
        Long createdAtEpochSeconds,
        String contractType,
        String contractTime,
        Double salaryMin,
        Double salaryMax,
        boolean salaryPredicted,
        Double latitude,
        Double longitude,
        String category,
        String categoryTag,
        String source,
        String searchProfile,
        String searchedWhat,
        String searchedWhere,
        String fetchedAt,
        Long fetchedAtEpochSeconds,
        String normalizedText,
        List<String> tags
) {
}
