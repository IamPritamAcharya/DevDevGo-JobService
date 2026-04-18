package com.devdevgo.jobs.client;

import com.devdevgo.jobs.config.AdzunaProperties;
import com.devdevgo.jobs.dto.AdzunaSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AdzunaClient {

    private final WebClient adzunaWebClient;
    private final AdzunaProperties properties;

    public Mono<AdzunaSearchResponse> searchJobs(
            String country,
            String what,
            String where,
            int page,
            int resultsPerPage) {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppKey())) {
            return Mono.error(
                    new AdzunaApiException("Adzuna appId/appKey are missing. Set ADZUNA_APP_ID and ADZUNA_APP_KEY."));
        }

        String effectiveCountry = StringUtils.hasText(country) ? country : properties.getCountry();

        return adzunaWebClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/jobs/{country}/search/{page}")
                            .queryParam("app_id", properties.getAppId())
                            .queryParam("app_key", properties.getAppKey())
                            .queryParam("results_per_page", resultsPerPage)
                            .queryParam("content-type", "application/json");

                    if (StringUtils.hasText(what)) {
                        builder.queryParam("what", what);
                    }
                    if (StringUtils.hasText(where)) {
                        builder.queryParam("where", where);
                    }

                    return builder.build(effectiveCountry, page);
                })
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AdzunaApiException(
                                        "Adzuna API failed with status " + response.statusCode().value()
                                                + (StringUtils.hasText(body) ? " - " + body : "")))))
                .bodyToMono(AdzunaSearchResponse.class);
    }
}