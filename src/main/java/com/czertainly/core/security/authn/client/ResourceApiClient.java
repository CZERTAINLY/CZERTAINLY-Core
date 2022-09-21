package com.czertainly.core.security.authn.client;

import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.core.model.auth.SyncRequestDto;
import com.czertainly.core.model.auth.SyncResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

public class ResourceApiClient extends CzertainlyBaseAuthenticationClient {

    private static final ParameterizedTypeReference<List<SyncRequestDto>> ENDPOINT_LIST_TYPE_REF = new ParameterizedTypeReference<>() {
    };

    @Value("${auth-service.endpoint-sync-uri:/auth/endpoints/sync}")
    private String syncContext;

    private static final String RESOURCE_CONTEXT = "/auth/resources";

    public SyncResponseDto syncEndPoints(List<SyncRequestDto> requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);

        return processRequest(r -> r
                        .uri(syncContext)
                        .body(Mono.just(requestDto), ENDPOINT_LIST_TYPE_REF)
                        .retrieve()
                        .toEntity(SyncResponseDto.class)
                        .block().getBody(),
                request);
    }

    public List<ResourceDetailDto> getAllResources() {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);

        return processRequest(r -> r
                        .uri(RESOURCE_CONTEXT)
                        .retrieve()
                        .toEntityList(ResourceDetailDto.class)
                        .block().getBody(),
                request);
    }
}
