package com.devdevgo.jobs.service;

import com.devdevgo.jobs.client.AdzunaClient;
import com.devdevgo.jobs.dto.AdzunaJobAd;
import com.devdevgo.jobs.dto.AdzunaSearchResponse;
import com.devdevgo.jobs.model.JobListing;
import com.devdevgo.jobs.model.SearchProfile;
import com.devdevgo.jobs.model.SyncReport;
import com.devdevgo.jobs.model.SyncState;
import com.devdevgo.jobs.repository.JobListingStore;
import com.devdevgo.jobs.repository.SyncStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Service
public class JobSyncService {

    private final AdzunaClient adzunaClient;
    private final JobListingStore store;
    private final SearchCatalog catalog;
    private final SyncStateStore syncStateStore;
    private final int resultsPerPage;
    private final int maxBatchSize;
    private final int purgeAfterDays;
    private final Duration minSyncInterval;
    private final boolean firebaseEnabled;
    private final boolean elasticsearchEnabled;
    private final boolean syncEnabled;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    private static final String WB = "(?<![a-z0-9\\-])";
    private static final String WB_END = "(?![a-z0-9\\-])";

    public JobSyncService(
            AdzunaClient adzunaClient,
            JobListingStore store,
            SearchCatalog catalog,
            SyncStateStore syncStateStore,
            @Value("${adzuna.default-results-per-page:8}") int resultsPerPage,
            @Value("${jobs.sync.batch-size:8}") int maxBatchSize,
            @Value("${jobs.sync.purge-after-days:7}") int purgeAfterDays,
            @Value("${jobs.sync.min-interval:PT1H}") Duration minSyncInterval,
            @Value("${firebase.enabled:false}") boolean firebaseEnabled,
            @Value("${jobs.elasticsearch.enabled:false}") boolean elasticsearchEnabled,
            @Value("${jobs.sync.enabled:true}") boolean syncEnabled) {
        this.adzunaClient = adzunaClient;
        this.store = store;
        this.catalog = catalog;
        this.syncStateStore = syncStateStore;
        this.resultsPerPage = resultsPerPage;
        this.maxBatchSize = Math.max(maxBatchSize, 1);
        this.purgeAfterDays = Math.max(purgeAfterDays, 1);
        this.minSyncInterval = minSyncInterval == null ? Duration.ofHours(1) : minSyncInterval;
        this.firebaseEnabled = firebaseEnabled;
        this.elasticsearchEnabled = elasticsearchEnabled;
        this.syncEnabled = syncEnabled;
    }

    public Mono<SyncReport> syncNextBatch(int batchSize) {
        return sync(batchSize, false);
    }

    public Mono<SyncReport> forceSync(int batchSize) {
        return sync(batchSize, true);
    }

    public Mono<SyncReport> syncIfDue(int batchSize) {
        return sync(batchSize, false);
    }

    private Mono<SyncReport> sync(int requestedBatchSize, boolean force) {
        if (!syncEnabled) {
            return Mono.just(disabledReport("Sync is disabled"));
        }

        if (!syncRunning.compareAndSet(false, true)) {
            return Mono.just(disabledReport("Another sync is already running"));
        }

        Instant started = Instant.now();
        int effectiveBatchSize = clampBatchSize(requestedBatchSize);
        AtomicInteger failedProfiles = new AtomicInteger(0);

        Mono<SyncReport> pipeline = syncStateStore.load()
                .defaultIfEmpty(SyncState.initial())
                .flatMap(state -> {
                    if (!force && !isSyncDue(state)) {
                        return Mono.just(skippedReport(state, started));
                    }

                    if (catalog.size() == 0) {
                        return Mono.just(new SyncReport(
                                firebaseEnabled,
                                0,
                                0,
                                0,
                                List.of(),
                                started.toString(),
                                Instant.now().toString(),
                                "No search profiles configured"));
                    }

                    int startIndex = (int) Math.floorMod(state.rotationIndex(), catalog.size());
                    List<SearchProfile> selected = catalog.selectBatch(startIndex, effectiveBatchSize);
                    List<String> names = selected.stream().map(SearchProfile::name).toList();

                    if (selected.isEmpty()) {
                        return Mono.just(new SyncReport(
                                firebaseEnabled,
                                0,
                                0,
                                0,
                                names,
                                started.toString(),
                                Instant.now().toString(),
                                "No search profiles selected"));
                    }

                    return Flux.fromIterable(selected)
                            .concatMap(profile -> adzunaClient.search(profile, 1, resultsPerPage)
                                    .timeout(Duration.ofSeconds(30))
                                    .flatMapMany(response -> Flux.fromIterable(toListings(profile, response)))
                                    .onErrorResume(error -> {
                                        failedProfiles.incrementAndGet();
                                        log.warn("Skipping search profile {} because Adzuna call failed",
                                                profile.name(), error);
                                        return Flux.empty();
                                    }))
                            .collectList()
                            .map(this::dedupeByStableKey)
                            .flatMap(deduped -> store.upsertAll(deduped)
                                    .flatMap(saved -> {
                                        SyncOutcome outcome = new SyncOutcome(
                                                state,
                                                selected,
                                                names,
                                                deduped,
                                                saved,
                                                started,
                                                failedProfiles.get());

                                        return store
                                                .deleteOlderThanCreatedAtEpochSeconds(createdAtThresholdEpochSeconds())
                                                .then(persistSuccess(outcome))
                                                .thenReturn(buildSuccessReport(outcome));
                                    }));
                })
                .onErrorResume(error -> syncStateStore.load()
                        .defaultIfEmpty(SyncState.initial())
                        .flatMap(state -> persistFailure(state, error)
                                .then(Mono.<SyncReport>error(error))));

        return pipeline.doFinally(signal -> syncRunning.set(false));
    }

    private SyncReport buildSuccessReport(SyncOutcome outcome) {
        String note;
        if (firebaseEnabled && elasticsearchEnabled) {
            note = "Synced jobs into Firestore and Elasticsearch";
        } else if (firebaseEnabled) {
            note = "Synced jobs into Firestore";
        } else if (elasticsearchEnabled) {
            note = "Synced jobs into Elasticsearch";
        } else {
            note = "Synced jobs into local store";
        }

        if (outcome.failedProfiles() > 0) {
            note = note + " (partial: " + outcome.failedProfiles() + " profile(s) skipped)";
        }

        return new SyncReport(
                firebaseEnabled,
                outcome.selectedProfiles().size(),
                outcome.dedupedListings().size(),
                outcome.savedCount(),
                outcome.selectedProfileNames(),
                outcome.startedAt().toString(),
                Instant.now().toString(),
                note);
    }

    private SyncReport disabledReport(String note) {
        Instant now = Instant.now();
        return new SyncReport(
                firebaseEnabled,
                0,
                0,
                0,
                List.of(),
                now.toString(),
                now.toString(),
                note);
    }

    private Mono<Void> persistSuccess(SyncOutcome outcome) {
        Instant now = Instant.now();
        SyncState nextState = new SyncState(
                outcome.previousState.id(),
                now.toString(),
                nextRotationIndex(outcome.previousState.rotationIndex(), outcome.selectedProfiles().size()),
                "SUCCESS",
                now.toString(),
                null);
        return syncStateStore.save(nextState);
    }

    private Mono<Void> persistFailure(SyncState previousState, Throwable error) {
        SyncState failedState = new SyncState(
                previousState.id(),
                previousState.lastSyncTime(),
                previousState.rotationIndex(),
                "FAILED",
                Instant.now().toString(),
                truncate(error.getMessage()));
        return syncStateStore.save(failedState)
                .onErrorResume(saveError -> {
                    log.warn("Failed to persist sync failure state", saveError);
                    return Mono.empty();
                });
    }

    private SyncReport skippedReport(SyncState state, Instant started) {
        String lastSync = state.lastSyncTime() == null ? "unknown" : state.lastSyncTime();
        String note = "Skipped because last sync was within the minimum interval (last sync: " + lastSync + ")";
        return new SyncReport(
                firebaseEnabled,
                0,
                0,
                0,
                List.of(),
                started.toString(),
                Instant.now().toString(),
                note);
    }

    private boolean isSyncDue(SyncState state) {
        if (state == null || state.lastSyncTime() == null || state.lastSyncTime().isBlank()) {
            return true;
        }

        try {
            Instant lastSync = Instant.parse(state.lastSyncTime());
            return !lastSync.plus(minSyncInterval).isAfter(Instant.now());
        } catch (DateTimeParseException ex) {
            return true;
        }
    }

    private int clampBatchSize(int requestedBatchSize) {
        int requested = Math.max(requestedBatchSize, 1);
        if (catalog.size() == 0) {
            return 1;
        }
        return Math.min(Math.min(requested, maxBatchSize), catalog.size());
    }

    private long nextRotationIndex(long currentRotationIndex, int batchSize) {
        if (catalog.size() == 0) {
            return 0L;
        }
        return Math.floorMod(currentRotationIndex + batchSize, catalog.size());
    }

    private long createdAtThresholdEpochSeconds() {
        return Instant.now().minus(Duration.ofDays(purgeAfterDays)).getEpochSecond();
    }

    private List<JobListing> dedupeByStableKey(List<JobListing> listings) {
        if (listings == null || listings.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, JobListing> deduped = new LinkedHashMap<>();
        for (JobListing listing : listings) {
            if (listing == null) {
                continue;
            }
            deduped.putIfAbsent(stableKey(listing), listing);
        }
        return new ArrayList<>(deduped.values());
    }

    private String stableKey(JobListing listing) {
        String key = safe(listing.title()) + "|" + safe(listing.company()) + "|" + safe(listing.location());
        return key.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replace("/", "-").trim();
    }

    private List<JobListing> toListings(SearchProfile profile, AdzunaSearchResponse response) {
        List<AdzunaJobAd> results = response.getResults() == null ? List.of() : response.getResults();
        return results.stream().map(ad -> toListing(profile, ad)).toList();
    }

    private JobListing toListing(SearchProfile profile, AdzunaJobAd ad) {
        String title = safe(ad.getTitle());
        String company = ad.getCompany() != null ? safe(ad.getCompany().getDisplayName()) : null;
        String location = ad.getLocation() != null ? safe(ad.getLocation().getDisplayName()) : null;
        String description = safe(ad.getDescription());
        String category = ad.getCategory() != null ? safe(ad.getCategory().getLabel()) : null;
        String categoryTag = ad.getCategory() != null ? safe(ad.getCategory().getTag()) : null;
        String createdAt = safe(ad.getCreated());
        Long createdAtEpochSeconds = toEpochSeconds(createdAt);
        Instant fetchedInstant = Instant.now();
        String fetchedAt = fetchedInstant.toString();
        Long fetchedAtEpochSeconds = fetchedInstant.getEpochSecond();
        String normalizedText = (title + " " + company + " " + location + " " + description + " " + category + " "
                + categoryTag + " " + safe(profile.what()) + " " + safe(profile.tag())).toLowerCase(Locale.ROOT);

        Set<String> tags = new LinkedHashSet<>();
        if (profile.tag() != null && !profile.tag().isBlank()) {
            tags.add(profile.tag());
        }

        addTagIfMatch(tags, normalizedText, "intern", "internship");
        addTagIfMatch(tags, normalizedText, "fresher", "entry level", "graduate trainee", "0-1 years", "0-1 year");
        addTagIfMatch(tags, normalizedText, "remote", "work from home", "wfh");
        addTagIfMatch(tags, normalizedText, "flutter");
        addTagIfMatch(tags, normalizedText, "react");
        addTagIfMatch(tags, normalizedText, "frontend");
        addTagIfMatch(tags, normalizedText, "backend");
        addTagIfMatch(tags, normalizedText, "full stack", "fullstack");
        addTagIfMatch(tags, normalizedText, "web developer", "web");
        addTagIfMatch(tags, normalizedText, "java");
        addTagIfMatch(tags, normalizedText, "python");
        addTagIfMatch(tags, normalizedText, "sql", "database", "postgresql", "mysql");
        addTagIfMatch(tags, normalizedText, "data analyst", "data analysis");
        addTagIfMatch(tags, normalizedText, "data science", "datascience", "data-science");
        addTagIfMatch(tags, normalizedText, "data engineer", "data engineering");
        addTagIfMatch(tags, normalizedText, "machine learning", "machine-learning", "ml", "ai");
        addTagIfMatch(tags, normalizedText, "qa", "tester", "testing");
        addTagIfMatch(tags, normalizedText, "automation", "automation testing");
        addTagIfMatch(tags, normalizedText, "ui ux", "ui/ux", "ux");
        addTagIfMatch(tags, normalizedText, "android");
        addTagIfMatch(tags, normalizedText, "ios");
        addTagIfMatch(tags, normalizedText, "devops", "docker", "kubernetes", "cloud");
        addTagIfMatch(tags, normalizedText, "security", "cybersecurity");
        addTagIfMatch(tags, normalizedText, "embedded");
        addTagIfMatch(tags, normalizedText, "campus");

        return new JobListing(
                stableKey(title, company, location),
                title,
                company,
                location,
                description,
                safe(ad.getRedirectUrl()),
                createdAt,
                createdAtEpochSeconds,
                safe(ad.getContractType()),
                safe(ad.getContractTime()),
                ad.getSalaryMin(),
                ad.getSalaryMax(),
                ad.getSalaryIsPredicted() != null && ad.getSalaryIsPredicted() == 1,
                ad.getLatitude(),
                ad.getLongitude(),
                category,
                categoryTag,
                "adzuna",
                profile.name(),
                profile.what(),
                profile.where(),
                fetchedAt,
                fetchedAtEpochSeconds,
                normalizedText,
                List.copyOf(tags));
    }

    private void addTagIfMatch(Set<String> tags, String text, String... keywords) {
        for (String keyword : keywords) {
            String kw = keyword.toLowerCase(Locale.ROOT);
            String patternStr = WB + Pattern.quote(kw) + WB_END;
            if (Pattern.compile(patternStr).matcher(text).find()) {
                tags.add(kw.replace(' ', '-').replace('/', '-'));
                return;
            }
        }
    }

    private Long toEpochSeconds(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(createdAt).getEpochSecond();
        } catch (DateTimeParseException ignored) {
            try {
                return java.time.OffsetDateTime.parse(createdAt).toInstant().getEpochSecond();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private String stableKey(String title, String company, String location) {
        return (safe(title) + "|" + safe(company) + "|" + safe(location))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replace("/", "-")
                .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private record SyncOutcome(
            SyncState previousState,
            List<SearchProfile> selectedProfiles,
            List<String> selectedProfileNames,
            List<JobListing> dedupedListings,
            int savedCount,
            Instant startedAt,
            int failedProfiles) {
    }
}
