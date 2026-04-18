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
        final String loc = normalize(location);

        final List<String> normalizedTags = tags == null
                ? List.of()
                : tags.stream()
                        .map(this::normalize)
                        .filter(tag -> tag != null && !tag.isBlank())
                        .toList();

        return store.search(query, loc, normalizedTags, recentLimit)
                .collectList()
                .map(all -> {
                    all.sort(Comparator.comparing(
                            JobListing::fetchedAtEpochSeconds,
                            Comparator.nullsLast(Long::compareTo)).reversed());

                    return paginate(all, page, size);
                });
    }

    private JobSearchResponse paginate(List<JobListing> all, int page, int size) {
        int from = Math.min(Math.max((page - 1) * size, 0), all.size());
        int to = Math.min(from + size, all.size());
        return new JobSearchResponse(all.size(), page, size, all.subList(from, to));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
