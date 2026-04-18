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

@Service
public class JobSearchService {

    private final JobListingStore store;
    private final int recentLimit;

    public JobSearchService(JobListingStore store, @Value("${jobs.search.recent-limit:500}") int recentLimit) {
        this.store = store;
        this.recentLimit = recentLimit;
    }

    public Mono<JobSearchResponse> search(String q, String location, String tag, int page, int size) {
        final String query = normalize(q);
        final String loc = normalize(location);
        final String tg = normalize(tag);

        return store.findRecent(recentLimit)
                .filter(job -> matches(job, query, loc, tg))
                .collectList()
                .map(all -> {
                    all.sort(Comparator.comparing(JobListing::fetchedAt, Comparator.nullsLast(String::compareTo)).reversed());
                    return paginate(all, page, size);
                });
    }

    private JobSearchResponse paginate(List<JobListing> all, int page, int size) {
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new JobSearchResponse(all.size(), page, size, all.subList(from, to));
    }

    private boolean matches(JobListing job, String query, String location, String tag) {
        if (query != null && !query.isBlank() && !containsAllTokens(job.normalizedText(), query)) {
            return false;
        }
        if (location != null && !location.isBlank()) {
            String haystack = (safe(job.location()) + " " + safe(job.searchedWhere())).toLowerCase(Locale.ROOT);
            if (!haystack.contains(location)) return false;
        }
        if (tag != null && !tag.isBlank()) {
            List<String> tags = job.tags() == null ? List.of() : job.tags();
            if (tags.stream().noneMatch(t -> t.toLowerCase(Locale.ROOT).contains(tag))) return false;
        }
        return true;
    }

    private boolean containsAllTokens(String text, String query) {
        if (text == null || text.isBlank()) return false;
        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (!token.isBlank() && !normalizedText.contains(token)) return false;
        }
        return true;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
