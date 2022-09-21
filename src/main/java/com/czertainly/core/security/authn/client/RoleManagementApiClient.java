package com.czertainly.core.security.authn.client;

import com.czertainly.api.model.core.auth.ObjectPermissionsDto;
import com.czertainly.api.model.core.auth.ObjectPermissionsRequestDto;
import com.czertainly.api.model.core.auth.ResourcePermissionsDto;
import com.czertainly.api.model.core.auth.RoleDetailDto;
import com.czertainly.api.model.core.auth.RolePermissionsRequestDto;
import com.czertainly.api.model.core.auth.RoleRequestDto;
import com.czertainly.api.model.core.auth.RoleWithPaginationDto;
import com.czertainly.api.model.core.auth.SubjectPermissionsDto;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

public class RoleManagementApiClient extends CzertainlyBaseAuthenticationClient {

    private static final String ROLE_BASE_CONTEXT = "/auth/roles";
    private static final String ROLE_DETAIL_CONTEXT = ROLE_BASE_CONTEXT + "/{roleUuid}";
    private static final String ROLE_PERMISSION_CONTEXT = ROLE_BASE_CONTEXT + "/{roleUuid}/permissions";
    private static final String ROLE_PERMISSION_RESOURCE_CONTEXT = ROLE_BASE_CONTEXT + "/{roleUuid}/permissions/{resourceUuid}";

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

    public RoleDetailDto createRole(RoleRequestDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);

        return processRequest(r -> r
                        .uri(ROLE_BASE_CONTEXT)
                        .body(Mono.just(requestDto), RoleRequestDto.class)
                        .retrieve()
                        .toEntity(RoleDetailDto.class)
                        .block().getBody(),
                request);
    }

    public RoleDetailDto updateRole(String roleUuid, RoleRequestDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.PUT);

        return processRequest(r -> r
                        .uri(ROLE_DETAIL_CONTEXT, roleUuid)
                        .body(Mono.just(requestDto), RoleRequestDto.class)
                        .retrieve()
                        .toEntity(RoleDetailDto.class)
                        .block().getBody(),
                request);
    }

    public void deleteRole(String roleUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.DELETE);

        processRequest(r -> r
                        .uri(ROLE_DETAIL_CONTEXT, roleUuid)
                        .retrieve()
                        .toEntity(Void.class)
                        .block().getBody(),
                request);
    }

    public SubjectPermissionsDto getPermissions(String roleUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);

        return processRequest(r -> r
                        .uri(ROLE_PERMISSION_CONTEXT, roleUuid)
                        .retrieve()
                        .toEntity(SubjectPermissionsDto.class)
                        .block().getBody(),
                request);
    }

    public SubjectPermissionsDto savePermissions(String roleUuid, RolePermissionsRequestDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);

        return processRequest(r -> r
                        .uri(ROLE_PERMISSION_CONTEXT, roleUuid)
                        .body(Mono.just(requestDto), SubjectPermissionsDto.class)
                        .retrieve()
                        .toEntity(SubjectPermissionsDto.class)
                        .block().getBody(),
                request);
    }


    public ResourcePermissionsDto getPermissionResource(String roleUuid, String resourceUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);

        return processRequest(r -> r
                        .uri(ROLE_PERMISSION_RESOURCE_CONTEXT, roleUuid, resourceUuid)
                        .retrieve()
                        .toEntity(ResourcePermissionsDto.class)
                        .block().getBody(),
                request);
    }

    public List<ObjectPermissionsDto> getResourcePermissionObjects(String roleUuid, String resourceUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);

        return processRequest(r -> r
                        .uri(ROLE_PERMISSION_RESOURCE_CONTEXT + "/objects", roleUuid, resourceUuid)
                        .retrieve()
                        .toEntityList(ObjectPermissionsDto.class)
                        .block().getBody(),
                request);
    }

    public void addResourcePermissionObjects(String roleUuid, String resourceUuid, List<ObjectPermissionsRequestDto> dto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);

        processRequest(r -> r
                        .uri(ROLE_PERMISSION_RESOURCE_CONTEXT + "/objects", roleUuid, resourceUuid)
                        .body(Mono.just(dto), ObjectPermissionsDto.class)
                        .retrieve()
                        .toEntity(Void.class)
                        .block().getBody(),
                request);
    }

    public void updateResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid, ObjectPermissionsRequestDto dto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.PUT);

        processRequest(r -> r
                        .uri(ROLE_PERMISSION_RESOURCE_CONTEXT + "/objects/" + objectUuid, roleUuid, resourceUuid)
                        .body(Mono.just(dto), ObjectPermissionsDto.class)
                        .retrieve()
                        .toEntity(Void.class)
                        .block().getBody(),
                request);
    }

    public void removeResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.DELETE);

        processRequest(r -> r
                        .uri(ROLE_PERMISSION_RESOURCE_CONTEXT + "/objects/" + objectUuid, roleUuid, resourceUuid)
                        .retrieve()
                        .toEntity(Void.class)
                        .block().getBody(),
                request);
    }

}
