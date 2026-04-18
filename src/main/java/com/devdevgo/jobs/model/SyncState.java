package com.devdevgo.jobs.model;


public record SyncState(int cursor, String lastSyncAt, long totalSynced) {}
