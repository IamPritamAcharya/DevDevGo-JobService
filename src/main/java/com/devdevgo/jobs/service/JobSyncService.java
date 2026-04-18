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

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class JobSyncService {

    private final AdzunaClient adzunaClient;
    private final JobListingStore store;
    private final SearchCatalog catalog;
    private final SyncStateStore stateStore;
    private final int resultsPerPage;
    private final boolean firebaseEnabled;

    private static final String WB     = "(?<![a-z0-9\\-])";
    private static final String WB_END = "(?![a-z0-9\\-])";

    public JobSyncService(
            AdzunaClient adzunaClient,
            JobListingStore store,
            SearchCatalog catalog,
            SyncStateStore stateStore,
            @Value("${adzuna.default-results-per-page:10}") int resultsPerPage,
            @Value("${firebase.enabled:false}") boolean firebaseEnabled
    ) {
        this.adzunaClient    = adzunaClient;
        this.store           = store;
        this.catalog         = catalog;
        this.stateStore      = stateStore;
        this.resultsPerPage  = resultsPerPage;
        this.firebaseEnabled = firebaseEnabled;
    }

    
    public Mono<Void> restoreState() {
        return stateStore.load()
                .doOnNext(state -> {
                    if (state != null && state.cursor() > 0) {
                        catalog.setCursor(state.cursor());
                        log.info("Restored sync cursor to {} (last sync: {})", state.cursor(), state.lastSyncAt());
                    } else {
                        log.info("No prior sync state found — starting from cursor 0");
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to restore sync state (starting fresh): {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    public Mono<SyncReport> syncNextBatch(int batchSize) {
        Instant started = Instant.now();
        List<SearchProfile> selected = catalog.nextBatch(batchSize);
        List<String> names = selected.stream().map(SearchProfile::name).toList();

        if (selected.isEmpty()) {
            return Mono.just(new SyncReport(firebaseEnabled, 0, 0, 0, names,
                    started.toString(), Instant.now().toString(), "No search profiles configured"));
        }

        return Flux.fromIterable(selected)
                .concatMap(profile -> adzunaClient.search(profile, 1, resultsPerPage)
                        .flatMapMany(response -> Flux.fromIterable(toListings(profile, response))))
                .collectList()
                .flatMap(listings -> store.upsertAll(listings)
                        .flatMap(saved -> {
                            int newCursor = catalog.getCursor();
                            return stateStore.load()
                                    .defaultIfEmpty(new SyncState(0, "", 0L))
                                    .flatMap(prev -> {
                                        long newTotal = prev.totalSynced() + selected.size();
                                        SyncState next = new SyncState(newCursor, Instant.now().toString(), newTotal);
                                        return stateStore.save(next).thenReturn(saved);
                                    });
                        })
                        .map(saved -> new SyncReport(
                                firebaseEnabled,
                                selected.size(),
                                listings.size(),
                                saved,
                                names,
                                started.toString(),
                                Instant.now().toString(),
                                firebaseEnabled ? "Synced jobs into Firestore" : "Synced jobs into in-memory store")));
    }

    public Mono<Void> purgeOlderThanDays(int days) {
        Instant threshold = Instant.now().minusSeconds(days * 24L * 60L * 60L);
        return store.deleteOlderThan(threshold.toString());
    }

  
    private List<JobListing> toListings(SearchProfile profile, AdzunaSearchResponse response) {
        List<AdzunaJobAd> results = response.getResults() == null ? List.of() : response.getResults();
        return results.stream().map(ad -> toListing(profile, ad)).toList();
    }

    private JobListing toListing(SearchProfile profile, AdzunaJobAd ad) {
        String title       = safe(ad.getTitle());
        String company     = ad.getCompany()  != null ? safe(ad.getCompany().getDisplayName())  : null;
        String location    = ad.getLocation() != null ? safe(ad.getLocation().getDisplayName()) : null;
        String description = safe(ad.getDescription());
        String category    = ad.getCategory() != null ? safe(ad.getCategory().getLabel()) : null;
        String categoryTag = ad.getCategory() != null ? safe(ad.getCategory().getTag())   : null;
        String fetchedAt   = Instant.now().toString();

        String normalizedText = buildNormalizedText(title, company, location, description, category, categoryTag);

        Set<String> tags = new LinkedHashSet<>();
        if (profile.tag() != null && !profile.tag().isBlank()) tags.add(profile.tag());
        extractTags(tags, normalizedText);

        return new JobListing(
                ad.getId() != null ? ad.getId() : title + "|" + company + "|" + location,
                title, company, location, description,
                safe(ad.getRedirectUrl()), safe(ad.getCreated()),
                safe(ad.getContractType()), safe(ad.getContractTime()),
                ad.getSalaryMin(), ad.getSalaryMax(),
                ad.getSalaryIsPredicted() != null && ad.getSalaryIsPredicted() == 1,
                ad.getLatitude(), ad.getLongitude(),
                category, categoryTag,
                "adzuna", profile.name(), profile.what(), profile.where(),
                fetchedAt, normalizedText, List.copyOf(tags)
        );
    }

    private String buildNormalizedText(String title, String company,
                                       String location, String description,
                                       String category, String categoryTag) {
        String t  = title    != null ? title.toLowerCase(Locale.ROOT)    : "";
        String co = company  != null ? company.toLowerCase(Locale.ROOT)  : "";
        String lo = location != null ? location.toLowerCase(Locale.ROOT) : "";
        String d  = description != null ? description.toLowerCase(Locale.ROOT) : "";
        String c  = category    != null ? category.toLowerCase(Locale.ROOT)    : "";
        String ct = categoryTag != null ? categoryTag.toLowerCase(Locale.ROOT) : "";
     
        return (t + " " + co + " " + lo + " " + d + " " + c + " " + ct).trim();
    }

    private void extractTags(Set<String> tags, String text) {
        addTagIfMatch(tags, text, "intern",           "internship");
        addTagIfMatch(tags, text, "fresher",          "entry level", "graduate trainee", "0-1 years", "0-1 year");
        addTagIfMatch(tags, text, "remote",           "work from home", "wfh");
        addTagIfMatch(tags, text, "flutter");
        addTagIfMatch(tags, text, "react");
        addTagIfMatch(tags, text, "java");
        addTagIfMatch(tags, text, "python");
        addTagIfMatch(tags, text, "data analyst",     "data science");
        addTagIfMatch(tags, text, "machine learning", "ml", "ai");
        addTagIfMatch(tags, text, "qa",               "tester", "testing");
        addTagIfMatch(tags, text, "ui ux",            "ui/ux", "design");
        addTagIfMatch(tags, text, "android");
        addTagIfMatch(tags, text, "devops",           "docker", "kubernetes");
        addTagIfMatch(tags, text, "security",         "cybersecurity");
        addTagIfMatch(tags, text, "sales");
        addTagIfMatch(tags, text, "marketing");
        addTagIfMatch(tags, text, "content writer",   "copywriter");
        addTagIfMatch(tags, text, "business analyst");
        addTagIfMatch(tags, text, "support",          "customer support");
        addTagIfMatch(tags, text, "campus");
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

    private String safe(String value) {
        return value == null ? null : value.trim();
    }
}
