package com.devdevgo.jobs.service;

import com.devdevgo.jobs.client.AdzunaClient;
import com.devdevgo.jobs.dto.AdzunaJobAd;
import com.devdevgo.jobs.dto.AdzunaSearchResponse;
import com.devdevgo.jobs.model.JobListing;
import com.devdevgo.jobs.model.SearchProfile;
import com.devdevgo.jobs.model.SyncReport;
import com.devdevgo.jobs.repository.JobListingStore;
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
    private final int resultsPerPage;
    private final boolean firebaseEnabled;

   
    private static final String WB = "(?<![a-z0-9\\-])";
    private static final String WB_END = "(?![a-z0-9\\-])";

    public JobSyncService(
            AdzunaClient adzunaClient,
            JobListingStore store,
            SearchCatalog catalog,
            @Value("${adzuna.default-results-per-page:10}") int resultsPerPage,
            @Value("${firebase.enabled:false}") boolean firebaseEnabled
    ) {
        this.adzunaClient = adzunaClient;
        this.store = store;
        this.catalog = catalog;
        this.resultsPerPage = resultsPerPage;
        this.firebaseEnabled = firebaseEnabled;
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
        String normalizedText = (title + " " + company + " " + location + " " + description + " " + category + " " + categoryTag)
                .toLowerCase(Locale.ROOT);

        Set<String> tags = new LinkedHashSet<>();
        if (profile.tag() != null && !profile.tag().isBlank()) tags.add(profile.tag());

        addTagIfMatch(tags, normalizedText, "intern",           "internship");
        addTagIfMatch(tags, normalizedText, "fresher",          "entry level", "graduate trainee", "0-1 years", "0-1 year");
        addTagIfMatch(tags, normalizedText, "remote",           "work from home", "wfh");
        addTagIfMatch(tags, normalizedText, "flutter");
        addTagIfMatch(tags, normalizedText, "react");
        addTagIfMatch(tags, normalizedText, "java");
        addTagIfMatch(tags, normalizedText, "python");
        addTagIfMatch(tags, normalizedText, "data analyst",     "data science");
        addTagIfMatch(tags, normalizedText, "machine learning", "ml", "ai");
        addTagIfMatch(tags, normalizedText, "qa",               "tester", "testing");
        addTagIfMatch(tags, normalizedText, "ui ux",            "ui/ux", "design");
        addTagIfMatch(tags, normalizedText, "android");
        addTagIfMatch(tags, normalizedText, "devops",           "docker", "kubernetes");
        addTagIfMatch(tags, normalizedText, "security",         "cybersecurity");
        addTagIfMatch(tags, normalizedText, "sales");
        addTagIfMatch(tags, normalizedText, "marketing");
        addTagIfMatch(tags, normalizedText, "content writer",   "copywriter");
        addTagIfMatch(tags, normalizedText, "business analyst");
        addTagIfMatch(tags, normalizedText, "support",          "customer support");
        addTagIfMatch(tags, normalizedText, "campus");

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
