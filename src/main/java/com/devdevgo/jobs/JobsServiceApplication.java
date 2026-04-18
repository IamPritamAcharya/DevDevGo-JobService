package com.devdevgo.jobs;

import com.devdevgo.jobs.config.AdzunaProperties;
import com.devdevgo.jobs.config.FirebaseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        DataElasticsearchAutoConfiguration.class,
        DataElasticsearchReactiveRepositoriesAutoConfiguration.class,
        DataElasticsearchRepositoriesAutoConfiguration.class
})
@EnableScheduling
@ConfigurationPropertiesScan(basePackageClasses = {AdzunaProperties.class, FirebaseProperties.class})
public class JobsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobsServiceApplication.class, args);
    }
}
