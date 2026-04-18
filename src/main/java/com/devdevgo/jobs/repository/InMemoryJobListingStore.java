package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.JobListing;
import com.devdevgo.jobs.service.JobListingMatcher;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnMissingBean(value = {Firestore.class, ReactiveElasticsearchOperations.class})
public class InMemoryJobListingStore implements JobListingStore {

    private final Map<String, JobListing> storage = new ConcurrentHashMap<>();

    @Override
    public Mono<Integer> upsertAll(List<JobListing> listings) {
        if (listings == null || listings.isEmpty()) {
            return Mono.just(0);
        }
        listings.forEach(listing -> storage.put(listing.id(), listing));
        return Mono.just(listings.size());
    }

    @Override
    public Flux<JobListing> findRecent(int limit) {
        return Flux.fromStream(storage.values().stream()
                .sorted(Comparator.comparing(JobListing::fetchedAtEpochSeconds, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(limit));
    }

    @Override
    public Flux<JobListing> search(String query, String location, List<String> tags, int limit) {
        return findRecent(Math.max(limit, 500))
                .filter(listing -> JobListingMatcher.matches(listing, query, location, tags))
                .take(limit);
    }

    @Override
    public Mono<Void> deleteOlderThanCreatedAtEpochSeconds(long epochSecondsThreshold) {
        storage.values().removeIf(listing ->
                listing.createdAtEpochSeconds() != null && listing.createdAtEpochSeconds() < epochSecondsThreshold);
        return Mono.empty();
    }
}
