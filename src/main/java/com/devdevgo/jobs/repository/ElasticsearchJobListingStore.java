package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.JobListing;
import com.devdevgo.jobs.model.JobListingDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ReactiveSearchHits;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
@Repository
@Primary
@ConditionalOnProperty(prefix = "jobs.elasticsearch", name = "enabled", havingValue = "true")
@ConditionalOnBean(ReactiveElasticsearchOperations.class)
public class ElasticsearchJobListingStore implements JobListingStore {

    private final ReactiveElasticsearchOperations operations;
    private final JobListingDocumentRepository repository;
    private final ObjectProvider<FirestoreJobListingStore> firestoreStoreProvider;

    @Override
    public Mono<Integer> upsertAll(List<JobListing> listings) {
        if (listings == null || listings.isEmpty()) {
            return Mono.just(0);
        }

        List<JobListingDocument> documents = listings.stream()
                .map(this::toDocument)
                .toList();

        Mono<Void> elasticWrite = repository.saveAll(documents)
                .doOnNext(doc -> log.debug("Indexed job in ES: {}", doc.getId()))
                .then();

        FirestoreJobListingStore firestoreStore = firestoreStoreProvider.getIfAvailable();
        Mono<Integer> firestoreWrite = firestoreStore == null
                ? Mono.just(listings.size())
                : firestoreStore.upsertAll(listings);

        return Mono.whenDelayError(elasticWrite, firestoreWrite)
                .doOnSuccess(v -> log.info("Upserted {} jobs into Elasticsearch (and Firestore if enabled)", listings.size()))
                .thenReturn(listings.size());
    }

    @Override
    public Flux<JobListing> findRecent(int limit) {
        int safeLimit = Math.max(limit, 1);
        Query query = new StringQuery("""
                {
                  "query": { "match_all": {} },
                  "sort": [{ "fetchedAtEpochSeconds": { "order": "desc" } }],
                  "size": %d,
                  "track_total_hits": true
                }
                """.formatted(safeLimit));

        return operations.searchForHits(query, JobListingDocument.class)
                .flatMapMany(this::toListings)
                .take(safeLimit);
    }

    @Override
    public Flux<JobListing> search(String query, String location, List<String> tags, int limit) {
        int safeLimit = Math.max(limit, 1);
        Query esQuery = buildQuery(query, location, tags, safeLimit);

        return operations.searchForHits(esQuery, JobListingDocument.class)
                .flatMapMany(this::toListings)
                .take(safeLimit);
    }

    /**
     * Deletes jobs older than the given threshold using a range search + deleteAllById.
     * Uses Adzuna's {@code createdAtEpochSeconds} (job post date) — NOT fetchedAt.
     * Runs against both Elasticsearch and Firestore (when available).
     */
    @Override
    public Mono<Void> deleteOlderThanCreatedAtEpochSeconds(long epochSecondsThreshold) {
        // range query: find all docs where createdAtEpochSeconds < threshold
        String rangeJson = """
                {
                  "query": { "range": { "createdAtEpochSeconds": { "lt": %d } } },
                  "size": 10000,
                  "_source": false
                }
                """.formatted(epochSecondsThreshold);
        Query rangeQuery = new StringQuery(rangeJson);

        Mono<Void> elasticDelete = operations.searchForHits(rangeQuery, JobListingDocument.class)
                .flatMapMany(hits -> hits.getSearchHits().map(SearchHit::getId))
                .collectList()
                .flatMap(ids -> {
                    if (ids.isEmpty()) {
                        log.debug("ES purge: no jobs older than epoch {} found", epochSecondsThreshold);
                        return Mono.empty();
                    }
                    log.info("ES purge: deleting {} jobs older than epoch {}", ids.size(), epochSecondsThreshold);
                    return repository.deleteAllById(ids).then();
                })
                .onErrorResume(error -> {
                    log.warn("ES purge failed (threshold={}), skipping", epochSecondsThreshold, error);
                    return Mono.empty();
                });

        FirestoreJobListingStore firestoreStore = firestoreStoreProvider.getIfAvailable();
        Mono<Void> firestoreDelete = firestoreStore == null
                ? Mono.empty()
                : firestoreStore.deleteOlderThanCreatedAtEpochSeconds(epochSecondsThreshold)
                        .onErrorResume(error -> {
                            log.warn("Firestore purge failed (threshold={}), skipping", epochSecondsThreshold, error);
                            return Mono.empty();
                        });

        return Mono.whenDelayError(elasticDelete, firestoreDelete).then();
    }

    private Query buildQuery(String query, String location, List<String> tags, int limit) {
        boolean hasQuery = query != null && !query.isBlank();
        boolean hasLocation = location != null && !location.isBlank();
        boolean hasTags = tags != null && !tags.isEmpty();

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"query\":{");

        if (!hasQuery && !hasLocation && !hasTags) {
            json.append("\"match_all\":{}");
        } else {
            json.append("\"bool\":{");
            boolean firstClause = true;

            if (hasQuery) {
                json.append("\"must\":[")
                        .append(queryStringClause(query, "searchText", "normalizedText", "title", "company", "location",
                                "description", "category", "categoryTag", "searchProfile", "searchedWhat", "searchedWhere", "tags"))
                        .append("]");
                firstClause = false;
            }

            if (hasLocation || hasTags) {
                if (!firstClause) {
                    json.append(",");
                }
                json.append("\"filter\":[");
                boolean firstFilter = true;

                if (hasLocation) {
                    json.append(queryStringClause(location, "location", "searchedWhere"));
                    firstFilter = false;
                }

                if (hasTags) {
                    for (String tag : tags) {
                        if (!firstFilter) {
                            json.append(",");
                        }
                        json.append(termClause("tags", tag));
                        firstFilter = false;
                    }
                }

                json.append("]");
            }

            json.append("}");
        }

        json.append("},");
        json.append("\"sort\":[{\"fetchedAtEpochSeconds\":{\"order\":\"desc\"}}],");
        json.append("\"size\":").append(limit);
        json.append(",\"track_total_hits\":true");
        json.append("}");

        return new StringQuery(json.toString());
    }

    private String queryStringClause(String value, String... fields) {
        String escaped = escapeJson(value);
        String fieldsJson = Arrays.stream(fields)
                .map(field -> "\"" + field + "^3\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("\"searchText\"");
        return """
                {"query_string":{"query":"%s","fields":[%s],"default_operator":"AND","analyze_wildcard":true}}
                """.formatted(escaped, fieldsJson).trim();
    }

    private String termClause(String field, String value) {
        return """
                {"term":{"%s":"%s"}}
                """.formatted(field, escapeJson(value)).trim();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Flux<JobListing> toListings(ReactiveSearchHits<JobListingDocument> hits) {
        return hits.getSearchHits()
                .map(SearchHit::getContent)
                .map(this::toListing);
    }

    private JobListing toListing(JobListingDocument doc) {
        return new JobListing(
                doc.getId(),
                doc.getTitle(),
                doc.getCompany(),
                doc.getLocation(),
                doc.getDescription(),
                doc.getRedirectUrl(),
                doc.getCreatedAt(),
                doc.getCreatedAtEpochSeconds(),
                doc.getContractType(),
                doc.getContractTime(),
                doc.getSalaryMin(),
                doc.getSalaryMax(),
                doc.isSalaryPredicted(),
                doc.getLatitude(),
                doc.getLongitude(),
                doc.getCategory(),
                doc.getCategoryTag(),
                doc.getSource(),
                doc.getSearchProfile(),
                doc.getSearchedWhat(),
                doc.getSearchedWhere(),
                doc.getFetchedAt(),
                doc.getFetchedAtEpochSeconds(),
                doc.getNormalizedText(),
                doc.getTags()
        );
    }

    private JobListingDocument toDocument(JobListing listing) {
        String normalizedText = safeJoin(
                listing.title(),
                listing.company(),
                listing.location(),
                listing.description(),
                listing.category(),
                listing.categoryTag(),
                listing.searchProfile(),
                listing.searchedWhat(),
                listing.searchedWhere(),
                listing.tags() == null ? null : String.join(" ", listing.tags())
        );

        String searchText = normalizedText.toLowerCase(Locale.ROOT);

        return new JobListingDocument(
                listing.id(),
                listing.title(),
                listing.company(),
                listing.location(),
                listing.description(),
                listing.redirectUrl(),
                listing.createdAt(),
                listing.createdAtEpochSeconds(),
                listing.contractType(),
                listing.contractTime(),
                listing.salaryMin(),
                listing.salaryMax(),
                listing.salaryPredicted(),
                listing.latitude(),
                listing.longitude(),
                listing.category(),
                listing.categoryTag(),
                listing.source(),
                listing.searchProfile(),
                listing.searchedWhat(),
                listing.searchedWhere(),
                listing.fetchedAt(),
                listing.fetchedAtEpochSeconds(),
                listing.normalizedText(),
                searchText,
                listing.tags()
        );
    }

    private String safeJoin(String... values) {
        return Arrays.stream(values)
                .filter(v -> v != null && !v.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}

