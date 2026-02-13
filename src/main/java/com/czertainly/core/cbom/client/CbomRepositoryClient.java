package com.czertainly.core.cbom.client;

import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.model.cbom.BomCreateResponseDto;
import com.czertainly.core.model.cbom.BomEntryDto;
import com.czertainly.core.model.cbom.BomResponseDto;
import com.czertainly.core.model.cbom.BomSearchRequestDto;
import com.czertainly.core.model.cbom.BomVersionDto;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.api.exception.CbomRepositoryException;
import lombok.Getter;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;


@Component
@DependsOn("settingService")
public class CbomRepositoryClient {

    private WebClient client;
    private static final String CBOM_CREATE = "/v1/bom";
    private static final String CBOM_SEARCH = "/v1/bom";
    private static final String CBOM_READ = "/v1/bom/{urn}";
    private static final String CBOM_READ_VERSIONS = "/v1/bom/{urn}/versions";

    @Getter
    private final String cbomRepositoryBaseUrl;

    public CbomRepositoryClient() {
        PlatformSettingsDto platformSettings = SettingsCache.getSettings(SettingsSection.PLATFORM);
        this.cbomRepositoryBaseUrl = platformSettings.getUtils().getCbomRepositoryUrl();
    }

    public BomCreateResponseDto create(final CbomUploadRequestDto data) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);
        return processRequest(r -> r
                        .uri(cbomRepositoryBaseUrl + CBOM_CREATE)
                        .body(Mono.just(data), CbomUploadRequestDto.class)
                        .header(HttpHeaders.CONTENT_TYPE, "application/vnd.cyclonedx+json")
                        .retrieve()
                        .toEntity(BomCreateResponseDto.class)
                        .block().getBody(),
                request);
    }

    public List<BomEntryDto> search(final BomSearchRequestDto query) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);
        return processRequest(r -> r
                        .uri(uriBuilder -> uriBuilder
                                .path(CBOM_SEARCH)
                                .queryParam("after", query.getAfter())
                                .build())
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<List<BomEntryDto>>() {
                        })
                        .block().getBody(),
                request);
    }

    public BomResponseDto read(final String urn, final Integer version) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);
        return processRequest(r -> r
                        .uri(uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path(CBOM_READ);
                            if (version != null) {
                                builder.queryParam("version", version);
                            }
                            return builder.build(urn);
                        })
                        .retrieve()
                        .toEntity(BomResponseDto.class)
                        .block().getBody(),
                request);
    }

    public List<BomVersionDto> versions(final String urn) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);
        return processRequest(r -> r
                        .uri(uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path(CBOM_READ_VERSIONS);
                            return builder.build(urn);
                        })
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<List<BomVersionDto>>() {
                        })
                        .block().getBody(),
                request);
    }

    private WebClient.RequestBodyUriSpec prepareRequest(final HttpMethod method) {
        if (client == null) {
            client = WebClient
                    .builder()
                    .filter(ExchangeFilterFunction.ofResponseProcessor(CbomRepositoryClient::handleHttpExceptions))
                    .baseUrl(cbomRepositoryBaseUrl)
                    .build();
        }
        return client.method(method);
    }

    private static <T, R> R processRequest(Function<T, R> func, T request) throws CbomRepositoryException {
        try {
            return func.apply(request);
        } catch (Exception e) {
            Throwable unwrappedCause = Exceptions.unwrap(e);
            if (unwrappedCause instanceof CbomRepositoryException cbomRepositoryException) {
                throw cbomRepositoryException;
            }
            if (unwrappedCause instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                throw new CbomRepositoryException(ProblemDetail.forStatus(wcre.getStatusCode()));
            }
            throw e;
        }
    }

    public static Mono<ClientResponse> handleHttpExceptions(final ClientResponse clientResponse) {
        if (clientResponse.statusCode().isError()) {
            return clientResponse.bodyToMono(ProblemDetail.class)
                    .flatMap(problemDetail -> Mono.<ClientResponse>error(new CbomRepositoryException(problemDetail)))
                    .switchIfEmpty(Mono.defer(() -> {
                        ProblemDetail pd = ProblemDetail.forStatus(clientResponse.statusCode());
                        return Mono.error(new CbomRepositoryException(pd));
                    }))
                    .onErrorResume(e -> {
                        if (e instanceof CbomRepositoryException) return Mono.error(e);
                        return Mono.error(new CbomRepositoryException(ProblemDetail.forStatus(clientResponse.statusCode())));
                    });
        }
        return Mono.just(clientResponse);
    }
}
