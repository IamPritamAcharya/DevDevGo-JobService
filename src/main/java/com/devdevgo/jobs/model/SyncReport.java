package com.devdevgo.jobs.model;

import java.util.List;

public record SyncReport(
        boolean firebaseEnabled,
        int queriesRun,
        int jobsFetched,
        int jobsSaved,
        List<String> profiles,
        String startedAt,
        String finishedAt,
        String note
) {
}
