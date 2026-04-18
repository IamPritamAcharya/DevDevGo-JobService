package com.devdevgo.jobs.client;

import com.devdevgo.jobs.config.AdzunaProperties;
import com.devdevgo.jobs.dto.AdzunaSearchResponse;
import com.devdevgo.jobs.model.SearchProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Component
public class AdzunaClient {

    private final WebClient adzunaWebClient;
    private final AdzunaProperties properties;

    public AdzunaClient(WebClient.Builder webClientBuilder, AdzunaProperties properties) {
        this.adzunaWebClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
        this.properties = properties;
    }

    public Mono<AdzunaSearchResponse> search(SearchProfile profile, int page, int resultsPerPage) {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppKey())) {
            return Mono.error(new AdzunaApiException("Missing Adzuna credentials. Set ADZUNA_APP_ID and ADZUNA_APP_KEY."));
        }

        String country = StringUtils.hasText(profile.where()) ? profile.where() : properties.getCountry();

        return adzunaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/jobs/{country}/search/{page}")
                        .queryParam("app_id", properties.getAppId())
                        .queryParam("app_key", properties.getAppKey())
                        .queryParam("results_per_page", resultsPerPage)
                        .queryParam("sort_by", "date")
                        .queryParamIfPresent("what", StringUtils.hasText(profile.what())
                                ? Optional.of(profile.what()) : Optional.empty())
                        .build(country, page))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AdzunaApiException(
                                        "Adzuna API failed with status " + response.statusCode().value() +
                                                (StringUtils.hasText(body) ? " - " + body : "")))))
                .bodyToMono(AdzunaSearchResponse.class);
    }
}
