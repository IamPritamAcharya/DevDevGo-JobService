package com.devdevgo.jobs.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;

@Configuration
@ConditionalOnProperty(prefix = "jobs.elasticsearch", name = "enabled", havingValue = "true")
@EnableReactiveElasticsearchRepositories(basePackages = "com.devdevgo.jobs.repository")
public class ElasticsearchConfig {
}
