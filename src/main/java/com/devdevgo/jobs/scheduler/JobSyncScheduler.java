package com.devdevgo.jobs.scheduler;

import com.devdevgo.jobs.service.JobSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobSyncScheduler {

    private final JobSyncService syncService;
    private final boolean enabled;
    private final int batchSize;

    public JobSyncScheduler(
            JobSyncService syncService,
            @Value("${jobs.sync.enabled:true}") boolean enabled,
            @Value("${jobs.sync.batch-size:8}") int batchSize
    ) {
        this.syncService = syncService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmStart() {
        triggerSync("startup");
    }

    @Scheduled(fixedDelayString = "${jobs.sync.interval:PT1H}")
    public void run() {
        triggerSync("scheduled");
    }

    private void triggerSync(String trigger) {
        if (!enabled) {
            log.debug("Skipping {} sync because syncing is disabled", trigger);
            return;
        }

        syncService.syncIfDue(batchSize)
                .subscribe(
                        report -> log.info("{} sync completed: {}", trigger, report),
                        error -> log.error("{} sync failed", trigger, error)
                );
    }
}
