package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.SyncState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;


@Repository
@ConditionalOnMissingBean(FirestoreSyncStateStore.class)
public class InMemorySyncStateStore implements SyncStateStore {

    private final AtomicReference<SyncState> state = new AtomicReference<>(new SyncState(0, "", 0L));

    @Override
    public Mono<SyncState> load() {
        return Mono.justOrEmpty(state.get());
    }

    @Override
    public Mono<Void> save(SyncState newState) {
        state.set(newState);
        return Mono.empty();
    }
}
