package com.devdevgo.jobs.repository;

import com.devdevgo.jobs.model.JobListingDocument;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobListingDocumentRepository extends ReactiveElasticsearchRepository<JobListingDocument, String> {
}
