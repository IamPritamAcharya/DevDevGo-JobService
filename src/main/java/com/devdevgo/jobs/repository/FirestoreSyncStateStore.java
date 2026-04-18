package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.config.FirebaseProperties;
import com.devdevgo.jobs.model.SyncState;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
@Repository
@ConditionalOnBean(Firestore.class)
public class FirestoreSyncStateStore implements SyncStateStore {

    private static final String DOC_ID = "job_sync_state";

    private final Firestore firestore;
    private final FirebaseProperties properties;

    @Override
    public Mono<SyncState> load() {
        return Mono.fromCallable(() -> {
                    Map<String, Object> data = firestore.collection(properties.getMetadataCollectionName())
                            .document(DOC_ID)
                            .get()
                            .get()
                            .getData();

                    if (data == null || data.isEmpty()) {
                        return SyncState.initial();
                    }
                    return fromMap(data);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> save(SyncState state) {
        return Mono.fromCallable(() -> {
                    firestore.collection(properties.getMetadataCollectionName())
                            .document(DOC_ID)
                            .set(toMap(state), SetOptions.merge())
                            .get();
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Map<String, Object> toMap(SyncState state) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", state == null ? DOC_ID : state.id());
        map.put("lastSyncTime", state == null ? null : state.lastSyncTime());
        map.put("rotationIndex", state == null ? 0L : state.rotationIndex());
        map.put("lastRunStatus", state == null ? "NEVER" : state.lastRunStatus());
        map.put("lastRunAt", state == null ? null : state.lastRunAt());
        map.put("lastRunError", state == null ? null : state.lastRunError());
        return map;
    }

    private SyncState fromMap(Map<String, Object> map) {
        return new SyncState(
                string(map.get("id"), DOC_ID),
                string(map.get("lastSyncTime"), null),
                longValue(map.get("rotationIndex"), 0L),
                string(map.get("lastRunStatus"), "NEVER"),
                string(map.get("lastRunAt"), null),
                string(map.get("lastRunError"), null)
        );
    }

    private String string(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private long longValue(Object value, long fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
