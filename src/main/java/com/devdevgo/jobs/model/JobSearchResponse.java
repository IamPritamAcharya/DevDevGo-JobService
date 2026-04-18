package com.devdevgo.jobs.model;

import java.util.List;

public record JobSearchResponse(
        long total,
        int page,
        int size,
        List<JobListing> items
) {
}
