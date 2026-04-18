package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.config.FirebaseProperties;
import com.devdevgo.jobs.model.JobListing;
import com.devdevgo.jobs.service.JobListingMatcher;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreJobListingStore implements JobListingStore {

    private final Firestore firestore;
    private final FirebaseProperties properties;

    @Override
    public Mono<Integer> upsertAll(List<JobListing> listings) {
        if (listings == null || listings.isEmpty()) {
            return Mono.just(0);
        }

        return Mono.fromCallable(() -> {
                    WriteBatch batch = firestore.batch();
                    String collection = properties.getCollectionName();
                    for (JobListing listing : listings) {
                        batch.set(
                                firestore.collection(collection).document(listing.id()),
                                toMap(listing),
                                SetOptions.merge()
                        );
                    }
                    batch.commit().get();
                    return listings.size();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<JobListing> findRecent(int limit) {
        return Mono.fromCallable(() -> {
                    List<QueryDocumentSnapshot> docs = firestore.collection(properties.getCollectionName())
                            .orderBy("fetchedAtEpochSeconds", Query.Direction.DESCENDING)
                            .limit(limit)
                            .get()
                            .get()
                            .getDocuments();

                    List<JobListing> listings = new ArrayList<>(docs.size());
                    for (QueryDocumentSnapshot doc : docs) {
                        JobListing listing = fromMap(doc.getData());
                        if (listing != null) {
                            listings.add(listing);
                        }
                    }
                    return listings;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<JobListing> search(String query, String location, List<String> tags, int limit) {
        return findRecent(Math.max(limit, 500))
                .filter(listing -> JobListingMatcher.matches(listing, query, location, tags))
                .take(limit);
    }

    @Override
    public Mono<Void> deleteOlderThanCreatedAtEpochSeconds(long epochSecondsThreshold) {
        return Mono.fromCallable(() -> {
                    List<QueryDocumentSnapshot> docs = firestore.collection(properties.getCollectionName())
                            .whereLessThan("createdAtEpochSeconds", epochSecondsThreshold)
                            .get()
                            .get()
                            .getDocuments();

                    if (docs.isEmpty()) {
                        return null;
                    }

                    WriteBatch batch = firestore.batch();
                    docs.forEach(doc -> batch.delete(doc.getReference()));
                    batch.commit().get();
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Map<String, Object> toMap(JobListing listing) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", listing.id());
        map.put("title", listing.title());
        map.put("company", listing.company());
        map.put("location", listing.location());
        map.put("description", listing.description());
        map.put("redirectUrl", listing.redirectUrl());
        map.put("createdAt", listing.createdAt());
        map.put("createdAtEpochSeconds", listing.createdAtEpochSeconds());
        map.put("contractType", listing.contractType());
        map.put("contractTime", listing.contractTime());
        map.put("salaryMin", listing.salaryMin());
        map.put("salaryMax", listing.salaryMax());
        map.put("salaryPredicted", listing.salaryPredicted());
        map.put("latitude", listing.latitude());
        map.put("longitude", listing.longitude());
        map.put("category", listing.category());
        map.put("categoryTag", listing.categoryTag());
        map.put("source", listing.source());
        map.put("searchProfile", listing.searchProfile());
        map.put("searchedWhat", listing.searchedWhat());
        map.put("searchedWhere", listing.searchedWhere());
        map.put("fetchedAt", listing.fetchedAt());
        map.put("fetchedAtEpochSeconds", listing.fetchedAtEpochSeconds());
        map.put("normalizedText", listing.normalizedText());
        map.put("tags", listing.tags());
        return map;
    }

    private JobListing fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return new JobListing(
                asString(map.get("id")),
                asString(map.get("title")),
                asString(map.get("company")),
                asString(map.get("location")),
                asString(map.get("description")),
                asString(map.get("redirectUrl")),
                asString(map.get("createdAt")),
                asLong(map.get("createdAtEpochSeconds")),
                asString(map.get("contractType")),
                asString(map.get("contractTime")),
                asDouble(map.get("salaryMin")),
                asDouble(map.get("salaryMax")),
                asBoolean(map.get("salaryPredicted")),
                asDouble(map.get("latitude")),
                asDouble(map.get("longitude")),
                asString(map.get("category")),
                asString(map.get("categoryTag")),
                asString(map.get("source")),
                asString(map.get("searchProfile")),
                asString(map.get("searchedWhat")),
                asString(map.get("searchedWhere")),
                asString(map.get("fetchedAt")),
                asLong(map.get("fetchedAtEpochSeconds")),
                asString(map.get("normalizedText")),
                asStringList(map.get("tags"))
        );
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean asBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private List<String> asStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .toList();
        }
        return List.of(value.toString());
    }
}
