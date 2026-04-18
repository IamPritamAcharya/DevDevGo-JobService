package com.devdevgo.jobs.model;

public record SyncState(
        String id,
        String lastSyncTime,
        long rotationIndex,
        String lastRunStatus,
        String lastRunAt,
        String lastRunError
) {
    public static SyncState initial() {
        return new SyncState(
                "job_sync_state",
                null,
                0L,
                "NEVER",
                null,
                null
        );
    }
}
