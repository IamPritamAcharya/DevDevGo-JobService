package com.devdevgo.jobs.service;

import com.devdevgo.jobs.model.JobListing;

import java.util.List;
import java.util.Locale;

public final class JobListingMatcher {

    private JobListingMatcher() {
    }

    public static boolean matches(JobListing listing, String query, String location, List<String> tags) {
        String haystack = (safe(listing.title()) + " " + safe(listing.company()) + " " + safe(listing.location())
                + " " + safe(listing.description()) + " " + safe(listing.category()) + " " + safe(listing.categoryTag())
                + " " + safe(listing.searchProfile()) + " " + safe(listing.searchedWhat()) + " " + safe(listing.searchedWhere())
                + " " + safe(listing.normalizedText()) + " " + safeTags(listing.tags()))
                .toLowerCase(Locale.ROOT);

        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            boolean match = haystack.contains(q) || java.util.Arrays.stream(q.split("\\s+"))
                    .filter(token -> !token.isBlank())
                    .allMatch(haystack::contains);
            if (!match) {
                return false;
            }
        }

        if (location != null && !location.isBlank()) {
            String locationHaystack = (safe(listing.location()) + " " + safe(listing.searchedWhere())).toLowerCase(Locale.ROOT);
            if (!locationHaystack.contains(location.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        if (tags != null && !tags.isEmpty()) {
            List<String> jobTags = listing.tags() == null ? List.of() : listing.tags();
            boolean matchesAll = tags.stream().allMatch(filterTag ->
                    jobTags.stream().anyMatch(jobTag -> jobTag.equalsIgnoreCase(filterTag)));
            if (!matchesAll) {
                return false;
            }
        }

        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeTags(List<String> tags) {
        return tags == null ? "" : String.join(" ", tags);
    }
}
