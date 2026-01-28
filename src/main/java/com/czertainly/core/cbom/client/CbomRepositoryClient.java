package com.czertainly.core.cbom.client;

import java.util.List;

import org.springframework.context.annotation.DependsOn;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import com.czertainly.api.clients.CzertainlyBaseApiClient;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.model.cbom.BomCreateResponseDto;
import com.czertainly.core.model.cbom.BomEntryDto;
import com.czertainly.core.model.cbom.BomResponseDto;
import com.czertainly.core.model.cbom.BomSearchRequestDto;
import com.czertainly.core.model.cbom.BomVersionDto;
import com.czertainly.core.settings.SettingsCache;

import reactor.core.publisher.Mono;

@Component
@DependsOn("settingService")
public class CbomRepositoryClient extends CzertainlyBaseApiClient {

    private String cbomRepositoryBaseUrl;

    private static final String CBOM_CREATE = "/v1/bom";
    private static final String CBOM_SEARCH = "/v1/bom";
    private static final String CBOM_READ = "/v1/bom/{urn}";
    private static final String CBOM_READ_VERSIONS = "/v1/bom/{urn}/versions";

    public CbomRepositoryClient() {
        PlatformSettingsDto platformSettings = SettingsCache.getSettings(SettingsSection.PLATFORM);
        this.cbomRepositoryBaseUrl = platformSettings.getUtils().getCbomRepositoryUrl();
    }

    public void create(final CbomUploadRequestDto data) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);
        try {
            processRequest(r -> r
                .uri(cbomRepositoryBaseUrl + CBOM_CREATE)
                .body(Mono.just(data), CbomUploadRequestDto.class)
                .retrieve()
                .toEntity(BomCreateResponseDto.class)
                .block().getBody(),
                request);
        } catch (Exception e) {
            throw new CbomRepositoryException("Can't create new CBOM document", e);
        }
    }

    public List<BomEntryDto> search(final BomSearchRequestDto query) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);
        try {
            return processRequest(r -> r
                .uri(uriBuilder -> uriBuilder
                    .path(CBOM_SEARCH)
                    .queryParam("after", query.getAfter())
                    .build())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<BomEntryDto>>() {})
                .block().getBody(),
                request);
        } catch (Exception e) {
            throw new CbomRepositoryException("Can't search for CBOM documents", e);
        }
    }

    public BomResponseDto read(final String urn, final Integer version) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);
        try {
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
        } catch (Exception e) {
            throw new CbomRepositoryException("Can't read CBOM document", e);
        }
    }

    public List<BomVersionDto> versions(final String urn) throws CbomRepositoryException {
        final WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);
        try {
            return processRequest(r -> r
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder
                    .path(CBOM_READ_VERSIONS);
                    return builder.build(urn);
                })
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<BomVersionDto>>() {})
                .block().getBody(),
                request);
        } catch (Exception e) {
            throw new CbomRepositoryException("Can't retrieve CBOM versions", e);
        }
    }

    @Override
    protected String getServiceUrl() {
        return cbomRepositoryBaseUrl;
    }
}
