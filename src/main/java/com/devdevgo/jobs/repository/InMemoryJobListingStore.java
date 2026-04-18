package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.JobListing;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnMissingBean(JobListingStore.class)
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
                .sorted(Comparator.comparing(JobListing::fetchedAt, Comparator.nullsLast(String::compareTo)).reversed())
                .limit(limit));
    }

    @Override
    public Mono<Void> deleteOlderThan(String isoInstantThreshold) {
        storage.values().removeIf(listing ->
                listing.fetchedAt() != null && listing.fetchedAt().compareTo(isoInstantThreshold) < 0);
        return Mono.empty();
    }
}
