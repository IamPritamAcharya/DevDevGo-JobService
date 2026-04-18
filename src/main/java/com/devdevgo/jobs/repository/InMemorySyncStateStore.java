package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.SyncState;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

@Repository
@ConditionalOnMissingBean(Firestore.class)
public class InMemorySyncStateStore implements SyncStateStore {

    private final AtomicReference<SyncState> state = new AtomicReference<>(SyncState.initial());

    @Override
    public Mono<SyncState> load() {
        return Mono.just(state.get());
    }

    @Override
    public Mono<Void> save(SyncState newState) {
        state.set(newState == null ? SyncState.initial() : newState);
        return Mono.empty();
    }
}
