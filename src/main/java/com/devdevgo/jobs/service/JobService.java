package com.devdevgo.jobs.service;

import com.devdevgo.jobs.client.AdzunaClient;
import com.devdevgo.jobs.dto.AdzunaJobAd;
import com.devdevgo.jobs.dto.AdzunaSearchResponse;
import com.devdevgo.jobs.dto.JobCard;
import com.devdevgo.jobs.dto.JobSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final AdzunaClient adzunaClient;

    public Mono<JobSearchResult> searchJobs(
            String what,
            String where,
            String country,
            int page,
            int resultsPerPage) {
        return adzunaClient.searchJobs(country, what, where, page, resultsPerPage)
                .map(response -> mapToResult(response, what, where, country, page, resultsPerPage));
    }

    private JobSearchResult mapToResult(
            AdzunaSearchResponse response,
            String what,
            String where,
            String country,
            int page,
            int resultsPerPage) {
        List<JobCard> jobs = response.getResults()
                .stream()
                .map(this::mapJob)
                .toList();

        long total = response.getCount() != null ? response.getCount() : jobs.size();

        return new JobSearchResult(
                StringUtils.hasText(what) ? what : null,
                StringUtils.hasText(where) ? where : null,
                StringUtils.hasText(country) ? country : null,
                page,
                resultsPerPage,
                total,
                jobs);
    }

    private JobCard mapJob(AdzunaJobAd job) {
        String company = job.getCompany() != null ? job.getCompany().getDisplayName() : null;
        String location = job.getLocation() != null ? job.getLocation().getDisplayName() : null;
        String category = job.getCategory() != null ? job.getCategory().getLabel() : null;
        String categoryTag = job.getCategory() != null ? job.getCategory().getTag() : null;

        return new JobCard(
                job.getId(),
                job.getTitle(),
                company,
                location,
                job.getDescription(),
                job.getRedirectUrl(),
                job.getCreated(),
                job.getContractType(),
                job.getContractTime(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                Boolean.TRUE.equals(job.getSalaryIsPredicted()),
                job.getLatitude(),
                job.getLongitude(),
                category,
                categoryTag);
    }
}