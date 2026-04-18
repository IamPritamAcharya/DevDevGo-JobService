package com.devdevgo.jobs.handler;

import com.devdevgo.jobs.client.AdzunaApiException;
import com.devdevgo.jobs.dto.ApiErrorResponse;
import com.devdevgo.jobs.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class JobHandler {

    private final JobService jobService;

    public Mono<ServerResponse> search(ServerRequest request) {
        String what = request.queryParam("what").orElse("");
        String where = request.queryParam("where").orElse("");
        String country = request.queryParam("country").orElse("");
        int page = readPositiveInt(request, "page", 1);
        int resultsPerPage = readPositiveInt(request, "resultsPerPage", 10);

        return jobService.searchJobs(what, where, country, page, resultsPerPage)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result))
                .onErrorResume(IllegalArgumentException.class,
                        ex -> error(HttpStatus.BAD_REQUEST, ex.getMessage(), request.path()))
                .onErrorResume(AdzunaApiException.class,
                        ex -> error(HttpStatus.BAD_GATEWAY, ex.getMessage(), request.path()))
                .onErrorResume(Exception.class, ex -> error(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unexpected error: " + ex.getMessage(), request.path()));
    }

    public Mono<ServerResponse> ping(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiErrorResponse(
                        LocalDateTime.now().toString(),
                        200,
                        "OK",
                        "devdevgo jobs service is running",
                        request.path()));
    }

    private int readPositiveInt(ServerRequest request, String name, int defaultValue) {
        return request.queryParam(name)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) {
                        throw new IllegalArgumentException(name + " must be greater than 0");
                    }
                    return parsed;
                })
                .orElse(defaultValue);
    }

    private Mono<ServerResponse> error(HttpStatus status, String message, String path) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiErrorResponse(
                        LocalDateTime.now().toString(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        path));
    }
}