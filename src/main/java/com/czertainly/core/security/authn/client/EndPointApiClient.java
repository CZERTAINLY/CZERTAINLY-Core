package com.czertainly.core.security.authn.client;

import com.czertainly.core.model.auth.SyncRequestDto;
import com.czertainly.core.model.auth.SyncResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

public class EndPointApiClient extends CzertainlyBaseAuthenticationClient {

    private static final ParameterizedTypeReference<List<SyncRequestDto>> ENDPOINT_LIST_TYPE_REF = new ParameterizedTypeReference<>() {
    };
    @Value("${auth-service.endpoint-sync-uri:/auth/endpoints/sync}")
    private String syncContext;

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
}
