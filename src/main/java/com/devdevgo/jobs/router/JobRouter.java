package com.devdevgo.jobs.router;

import com.devdevgo.jobs.handler.JobHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class JobRouter {

    @Bean
    RouterFunction<ServerResponse> jobRoutes(JobHandler handler) {
        return route(GET("/api/v1/jobs/search"), handler::search)
                .andRoute(GET("/api/v1/jobs/ping"), handler::ping);
    }
}