package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.JobListing;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface JobListingStore {

    Mono<Integer> upsertAll(List<JobListing> listings);

    Flux<JobListing> findRecent(int limit);

    Mono<Void> deleteOlderThan(String isoInstantThreshold);
}
