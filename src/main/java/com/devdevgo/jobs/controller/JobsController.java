package com.devdevgo.jobs.controller;

import com.devdevgo.jobs.model.JobSearchResponse;
import com.devdevgo.jobs.model.PingResponse;
import com.devdevgo.jobs.model.SyncReport;
import com.devdevgo.jobs.service.JobSearchService;
import com.devdevgo.jobs.service.JobSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/jobs")
public class JobsController {

    private final JobSearchService searchService;
    private final JobSyncService syncService;

    @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PingResponse> ping() {
        return Mono.just(new PingResponse(
                Instant.now().toString(),
                200,
                "devdevgo jobs service is running",
                "/api/v1/jobs/ping"
        ));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JobSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return searchService.search(q, location, tag, Math.max(page, 1), Math.max(size, 1));
    }

    @PostMapping(value = "/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SyncReport> sync(@RequestParam(defaultValue = "2") int batchSize) {
        return syncService.syncNextBatch(Math.max(batchSize, 1));
    }
}
