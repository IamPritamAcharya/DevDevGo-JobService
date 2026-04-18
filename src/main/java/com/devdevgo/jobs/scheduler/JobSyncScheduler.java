package com.devdevgo.jobs.scheduler;

import com.devdevgo.jobs.model.SyncState;
import com.devdevgo.jobs.repository.SyncStateStore;
import com.devdevgo.jobs.service.JobSyncService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class JobSyncScheduler {

        private final JobSyncService syncService;
        private final SyncStateStore stateStore;
        private final boolean enabled;
        private final int batchSize;
        private final int purgeAfterDays;
        private final Duration minInterval;

        public JobSyncScheduler(
                        JobSyncService syncService,
                        SyncStateStore stateStore,
                        @Value("${jobs.sync.enabled:true}") boolean enabled,
                        @Value("${jobs.sync.batch-size:8}") int batchSize,
                        @Value("${jobs.sync.purge-after-days:14}") int purgeAfterDays,
                        @Value("${jobs.sync.min-interval:PT1H}") Duration minInterval) {
                this.syncService = syncService;
                this.stateStore = stateStore;
                this.enabled = enabled;
                this.batchSize = batchSize;
                this.purgeAfterDays = purgeAfterDays;
                this.minInterval = minInterval;
        }

        @PostConstruct
        public void onStartup() {
                if (!enabled)
                        return;

                syncService.restoreState()
                                .then(stateStore.load().defaultIfEmpty(new SyncState(0, "", 0L)))
                                .flatMap(state -> {
                                        if (isWithinMinInterval(state.lastSyncAt())) {
                                                log.info("Skipping startup sync — last sync was {} (min interval: {})",
                                                                state.lastSyncAt(), minInterval);
                                                return Mono.empty();
                                        }
                                        log.info("Triggering startup sync batch");
                                        return runBatch();
                                })
                                .subscribe(
                                                report -> log.info("Startup sync completed: {}", report),
                                                error -> log.error("Startup sync failed", error));
        }

        @Scheduled(cron = "${jobs.sync.cron:0 0 * * * *}")
        public void scheduledRun() {
                if (!enabled)
                        return;
                runBatch().subscribe(
                                report -> log.info("Scheduled sync completed: {}", report),
                                error -> log.error("Scheduled sync failed", error));
        }

        private boolean isWithinMinInterval(String lastSyncAt) {
                if (lastSyncAt == null || lastSyncAt.isBlank())
                        return false;
                try {
                        Instant last = Instant.parse(lastSyncAt);
                        return Duration.between(last, Instant.now()).compareTo(minInterval) < 0;
                } catch (Exception e) {
                        return false; // unparseable — allow the sync
                }
        }

        private reactor.core.publisher.Mono<com.devdevgo.jobs.model.SyncReport> runBatch() {
                return syncService.syncNextBatch(batchSize)
                                .flatMap(report -> syncService.purgeOlderThanDays(purgeAfterDays).thenReturn(report));
        }
}