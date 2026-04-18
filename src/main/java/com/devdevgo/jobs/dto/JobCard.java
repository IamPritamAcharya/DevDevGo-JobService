package com.devdevgo.jobs.dto;

public record JobCard(
        String id,
        String title,
        String company,
        String location,
        String description,
        String redirectUrl,
        String created,
        String contractType,
        String contractTime,
        Double salaryMin,
        Double salaryMax,
        boolean salaryPredicted,
        Double latitude,
        Double longitude,
        String category,
        String categoryTag) {
}