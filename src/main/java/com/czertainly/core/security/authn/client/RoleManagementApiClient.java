package com.czertainly.core.security.authn.client;

import com.czertainly.api.model.core.auth.*;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

public class RoleManagementApiClient extends CzertainlyBaseAuthenticationClient {

    private static final String ROLE_BASE_CONTEXT = "/auth/roles";
    private static final String ROLE_DETAIL_CONTEXT = ROLE_BASE_CONTEXT + "/{roleUuid}";

    public RoleManagementApiClient(String authServiceBaseUrl, WebClient client) {
        super(authServiceBaseUrl, client);
    }

    public RoleManagementApiClient() {
    }

    public RoleWithPaginationDto getRoles() {

        WebClient.RequestBodySpec request = prepareRequest(HttpMethod.GET).uri(ROLE_BASE_CONTEXT);

        return processRequest(r -> r
                        .retrieve()
                        .toEntity(RoleWithPaginationDto.class)
                        .block().getBody(),
                request);
    }


    public RoleDetailDto getRoleDetail(String roleUuid) {
        WebClient.RequestBodySpec request = prepareRequest(HttpMethod.GET).uri(ROLE_DETAIL_CONTEXT.replace("{roleUuid}", roleUuid));

        return processRequest(r -> r
                        .retrieve()
                        .toEntity(RoleDetailDto.class)
                        .block().getBody(),
                request);
    }

    public RoleDto createRole(RoleRequestDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);

        return processRequest(r -> r
                        .uri(ROLE_BASE_CONTEXT)
                        .body(Mono.just(requestDto), RoleRequestDto.class)
                        .retrieve()
                        .toEntity(RoleDto.class)
                        .block().getBody(),
                request);
    }

    public RoleDto updateRole(String roleUuid, RoleRequestDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.PUT);

        return processRequest(r -> r
                        .uri(ROLE_DETAIL_CONTEXT, roleUuid)
                        .body(Mono.just(requestDto), RoleRequestDto.class)
                        .retrieve()
                        .toEntity(RoleDto.class)
                        .block().getBody(),
                request);
    }

    public RoleDto deleteRole(String roleUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.DELETE);

        return processRequest(r -> r
                        .uri(ROLE_DETAIL_CONTEXT, roleUuid)
                        .retrieve()
                        .toEntity(RoleDto.class)
                        .block().getBody(),
                request);
    }

    public List<PermissionDto> getPermissions(String roleUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);

        return processRequest(r -> r
                        .uri(ROLE_DETAIL_CONTEXT, roleUuid)
                        .retrieve()
                        .toEntityList(PermissionDto.class)
                        .block().getBody(),
                request);
    }

    public RoleDetailDto addPermissions(String roleUuid, PermissionDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.PATCH);

        return processRequest(r -> r
                        .uri(ROLE_DETAIL_CONTEXT, roleUuid)
                        .body(Mono.just(requestDto), PermissionDto.class)
                        .retrieve()
                        .toEntity(RoleDetailDto.class)
                        .block().getBody(),
                request);
    }
}
