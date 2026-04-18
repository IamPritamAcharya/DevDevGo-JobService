package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.config.FirebaseProperties;
import com.devdevgo.jobs.model.SyncState;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreSyncStateStore implements SyncStateStore {

    private static final String DOC_ID = "sync_state";

    private final Firestore firestore;
    private final FirebaseProperties properties;

    @Override
    public Mono<SyncState> load() {
        return Mono.fromCallable(() -> {
                    DocumentReference ref = firestore
                            .collection(properties.getMetadataCollectionName())
                            .document(DOC_ID);
                    DocumentSnapshot snap = ref.get().get();
                    if (!snap.exists()) return null;

                    Long cursor      = snap.getLong("cursor");
                    String lastSync  = snap.getString("lastSyncAt");
                    Long total       = snap.getLong("totalSynced");

                    return new SyncState(
                            cursor != null ? cursor.intValue() : 0,
                            lastSync != null ? lastSync : "",
                            total   != null ? total : 0L
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(s  -> log.info("Loaded sync state from Firestore: cursor={} lastSyncAt={} totalSynced={}", s.cursor(), s.lastSyncAt(), s.totalSynced()))
                .doOnError(e -> log.warn("Could not load sync state from Firestore: {}", e.getMessage()));
    }

    @Override
    public Mono<Void> save(SyncState state) {
        return Mono.fromCallable(() -> {
                    firestore.collection(properties.getMetadataCollectionName())
                            .document(DOC_ID)
                            .set(Map.of(
                                    "cursor",      state.cursor(),
                                    "lastSyncAt",  state.lastSyncAt(),
                                    "totalSynced", state.totalSynced()
                            ))
                            .get();
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.debug("Saved sync state: cursor={}", state.cursor()))
                .doOnError(e  -> log.warn("Could not save sync state: {}", e.getMessage()))
                .then();
    }
}
