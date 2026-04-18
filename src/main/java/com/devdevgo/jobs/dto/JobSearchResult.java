package com.devdevgo.jobs.dto;

import java.util.List;

public record JobSearchResult(
        String what,
        String where,
        String country,
        int page,
        int resultsPerPage,
        long totalResults,
        List<JobCard> jobs) {
}