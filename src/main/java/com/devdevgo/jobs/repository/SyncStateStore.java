package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.SyncState;
import reactor.core.publisher.Mono;

public interface SyncStateStore {

    Mono<SyncState> load();

    Mono<Void> save(SyncState state);
}
