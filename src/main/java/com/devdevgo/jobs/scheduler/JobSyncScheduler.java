package com.devdevgo.jobs.scheduler;

import com.devdevgo.jobs.service.JobSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobSyncScheduler {

    private final JobSyncService syncService;
    private final boolean enabled;
    private final int batchSize;
    private final int purgeAfterDays;

    public JobSyncScheduler(
            JobSyncService syncService,
            @Value("${jobs.sync.enabled:true}") boolean enabled,
            @Value("${jobs.sync.batch-size:2}") int batchSize,
            @Value("${jobs.sync.purge-after-days:14}") int purgeAfterDays
    ) {
        this.syncService = syncService;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.purgeAfterDays = purgeAfterDays;
    }

    @Scheduled(cron = "${jobs.sync.cron:0 0 * * * *}")
    public void run() {
        if (!enabled) return;

        syncService.syncNextBatch(batchSize)
                .flatMap(report -> syncService.purgeOlderThanDays(purgeAfterDays).thenReturn(report))
                .subscribe(
                        report -> log.info("Sync completed: {}", report),
                        error -> log.error("Sync failed", error)
                );
    }
}
