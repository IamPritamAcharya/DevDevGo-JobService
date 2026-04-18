package com.devdevgo.jobs.service;

import com.devdevgo.jobs.model.JobListing;
import com.devdevgo.jobs.model.JobSearchResponse;
import com.devdevgo.jobs.repository.JobListingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Search service with relevance scoring.
 *
 * Scoring breakdown (higher = better match):
 *   +10  every query token found in the job title
 *   +5   every query token found in the company name
 *   +2   every query token found in the full normalizedText (description/location/etc.)
 *
 * This means a listing whose title directly contains the search term ranks above
 * one that only mentions it deep in the description — addressing the issue of
 * irrelevant results bubbling up from the normalizedText blob.
 */
@Service
public class JobSearchService {

    private static final int SCORE_TITLE    = 10;
    private static final int SCORE_COMPANY  = 5;
    private static final int SCORE_TEXT     = 2;

    private final JobListingStore store;
    private final int recentLimit;

    public JobSearchService(JobListingStore store,
            @Value("${jobs.search.recent-limit:500}") int recentLimit) {
        this.store = store;
        this.recentLimit = recentLimit;
    }

    public Mono<JobSearchResponse> search(String q,
            String location,
            List<String> tags,
            int page,
            int size) {

        final String query = normalize(q);
        final String loc   = normalize(location);

        final List<String> normalizedTags = tags == null
                ? List.of()
                : tags.stream().map(this::normalize).toList();

        return store.findRecent(recentLimit)
                .filter(job -> matchesHard(job, query, loc, normalizedTags))
                .collectList()
                .map(all -> {
                    all.sort(Comparator
                            .comparingInt((JobListing job) -> score(job, query))
                            .reversed()
                            .thenComparing(
                                    Comparator.comparing(
                                            JobListing::fetchedAt,
                                            Comparator.nullsLast(String::compareTo)).reversed()));

                    return paginate(all, page, size);
                });
    }

    private boolean matchesHard(JobListing job, String query, String location, List<String> tags) {

        if (query != null && !query.isBlank()) {
            for (String token : query.split("\\s+")) {
                if (token.isBlank()) continue;
                boolean inTitle   = containsToken(safe(job.title()),   token);
                boolean inCompany = containsToken(safe(job.company()), token);
                boolean inText    = containsToken(safe(job.normalizedText()), token);
                if (!inTitle && !inCompany && !inText) return false;
            }
        }

        if (location != null && !location.isBlank()) {
            String haystack = (safe(job.location()) + " " + safe(job.searchedWhere()))
                    .toLowerCase(Locale.ROOT);
            if (!haystack.contains(location)) return false;
        }

        if (!tags.isEmpty()) {
            List<String> jobTags = job.tags() == null ? List.of() : job.tags();
            boolean matchesAll = tags.stream().allMatch(filterTag ->
                    jobTags.stream().anyMatch(jobTag -> jobTag.equalsIgnoreCase(filterTag)));
            if (!matchesAll) return false;
        }

        return true;
    }

    private int score(JobListing job, String query) {
        if (query == null || query.isBlank()) return 0;

        int total = 0;
        for (String token : query.split("\\s+")) {
            if (token.isBlank()) continue;
            if (containsToken(safe(job.title()),   token)) total += SCORE_TITLE;
            if (containsToken(safe(job.company()), token)) total += SCORE_COMPANY;
            if (containsToken(safe(job.normalizedText()), token)) total += SCORE_TEXT;
        }
        return total;
    }

    private boolean containsToken(String haystack, String token) {
        if (haystack == null || haystack.isBlank()) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private JobSearchResponse paginate(List<JobListing> all, int page, int size) {
        int from = Math.min((page - 1) * size, all.size());
        int to   = Math.min(from + size, all.size());
        return new JobSearchResponse(all.size(), page, size, all.subList(from, to));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
