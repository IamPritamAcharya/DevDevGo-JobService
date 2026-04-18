package com.devdevgo.jobs.service;

import com.devdevgo.jobs.model.SearchProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Catalog of all search profiles.
 *
 * Rate-limit maths (Adzuna free tier):
 *   250 req/day  →  250 / 24h ≈ 10 req/h  (hard ceiling)
 *   Batch size 8 → 8 req/h → 192 req/day  (safe buffer, ~77% of daily quota)
 *
 * With 10 results per profile that's 80 new listings written to Firestore every hour.
 * The cursor advances by batchSize each run and wraps around, so all 24 profiles
 * are covered in a 3-hour window.
 */
@Component
public class SearchCatalog {

    private final List<SearchProfile> profiles = List.of(
            new SearchProfile("flutter-intern",       "flutter intern",                  "in", "flutter"),
            new SearchProfile("react-intern",         "react intern",                    "in", "frontend"),
            new SearchProfile("backend-fresher",      "backend developer fresher",       "in", "backend"),
            new SearchProfile("fullstack-intern",     "full stack intern",               "in", "fullstack"),
            new SearchProfile("java-trainee",         "java trainee",                    "in", "java"),
            new SearchProfile("python-fresher",       "python developer fresher",        "in", "python"),
            new SearchProfile("data-analyst",         "data analyst internship",         "in", "data"),
            new SearchProfile("ml-intern",            "machine learning intern",         "in", "ml"),
            new SearchProfile("qa-fresher",           "qa tester fresher",               "in", "testing"),
            new SearchProfile("uiux-intern",          "ui ux internship",                "in", "design"),
            new SearchProfile("android-intern",       "android developer intern",        "in", "mobile"),
            new SearchProfile("devops-intern",        "devops intern",                   "in", "devops"),
            new SearchProfile("cybersecurity-intern", "cybersecurity internship",        "in", "security"),
            new SearchProfile("business-analyst",     "business analyst fresher",        "in", "business"),
            new SearchProfile("sales-intern",         "sales internship",                "in", "sales"),
            new SearchProfile("marketing-intern",     "marketing internship",            "in", "marketing"),
            new SearchProfile("content-writer",       "content writer internship",       "in", "content"),
            new SearchProfile("support-fresher",      "customer support fresher",        "in", "support"),
            new SearchProfile("remote-intern",        "remote internship",               "in", "remote"),
            new SearchProfile("work-from-home",       "work from home fresher",          "in", "remote"),
            new SearchProfile("campus-hiring",        "campus hiring software engineer", "in", "campus"),
            new SearchProfile("graduate-trainee",     "graduate trainee",                "in", "graduate"),
            new SearchProfile("apprentice",           "apprentice software",             "in", "apprentice"),
            new SearchProfile("product-intern",       "product intern",                  "in", "product")
    );

    private final AtomicInteger cursor = new AtomicInteger(0);

    public List<SearchProfile> all() {
        return profiles;
    }

    public int size() {
        return profiles.size();
    }

    /** Advance the in-memory cursor and return the next {@code batchSize} profiles (wraps). */
    public List<SearchProfile> nextBatch(int batchSize) {
        if (profiles.isEmpty() || batchSize <= 0) return List.of();
        int start = Math.floorMod(cursor.getAndAdd(batchSize), profiles.size());
        return IntStream.range(0, batchSize)
                .mapToObj(i -> profiles.get((start + i) % profiles.size()))
                .toList();
    }

    /** Restore the cursor to a specific position (called on startup from persisted state). */
    public void setCursor(int position) {
        cursor.set(Math.floorMod(position, Math.max(profiles.size(), 1)));
    }

    public int getCursor() {
        return cursor.get();
    }
}
