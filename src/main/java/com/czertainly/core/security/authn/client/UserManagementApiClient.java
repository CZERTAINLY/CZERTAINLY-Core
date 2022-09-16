package com.czertainly.core.security.authn.client;

import com.czertainly.api.model.core.auth.MergedPermissionsDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;
import com.czertainly.api.model.core.auth.UserWithPaginationDto;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

public class UserManagementApiClient extends CzertainlyBaseAuthenticationClient {

    private static final String USER_BASE_CONTEXT = "/auth/users";
    private static final String USER_DETAIL_CONTEXT = USER_BASE_CONTEXT + "/{userUuid}";
    private static final String USER_PERMISSION_CONTEXT = USER_DETAIL_CONTEXT + "/permissions";
    private static final String USER_ROLE_CONTEXT = USER_DETAIL_CONTEXT + "/roles";

    public UserManagementApiClient(String authServiceBaseUrl, WebClient client) {
        super(authServiceBaseUrl, client);
    }

    public UserManagementApiClient() {
    }

    public UserWithPaginationDto getUsers() {

        WebClient.RequestBodySpec request = prepareRequest(HttpMethod.GET).uri(USER_BASE_CONTEXT);

        return processRequest(r -> r
                        .retrieve()
                        .toEntity(UserWithPaginationDto.class)
                        .block().getBody(),
                request);
    }


    public UserDetailDto getUserDetail(String userUuid) {
        WebClient.RequestBodySpec request = prepareRequest(HttpMethod.GET).uri(USER_DETAIL_CONTEXT.replace("{userUuid}", userUuid));

        return processRequest(r -> r
                        .retrieve()
                        .toEntity(UserDetailDto.class)
                        .block().getBody(),
                request);
    }

    public UserDto createUser(UserRequestDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.POST);

        return processRequest(r -> r
                        .uri(USER_BASE_CONTEXT)
                        .body(Mono.just(requestDto), UserRequestDto.class)
                        .retrieve()
                        .toEntity(UserDto.class)
                        .block().getBody(),
                request);
    }

    public UserDto updateUser(String userUuid, UserUpdateRequestDto requestDto) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.PUT);

        return processRequest(r -> r
                        .uri(USER_DETAIL_CONTEXT, userUuid)
                        .body(Mono.just(requestDto), UserRequestDto.class)
                        .retrieve()
                        .toEntity(UserDto.class)
                        .block().getBody(),
                request);
    }

    public UserDto removeUser(String userUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.DELETE);

        return processRequest(r -> r
                        .uri(USER_DETAIL_CONTEXT, userUuid)
                        .retrieve()
                        .toEntity(UserDto.class)
                        .block().getBody(),
                request);
    }

    public MergedPermissionsDto getPermissions(String userUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.GET);

        return processRequest(r -> r
                        .uri(USER_PERMISSION_CONTEXT, userUuid)
                        .retrieve()
                        .toEntity(MergedPermissionsDto.class)
                        .block().getBody(),
                request);
    }

    public UserDetailDto updateRoles(String userUuid, List<String> roles) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.PATCH);

        return processRequest(r -> r
                        .uri(USER_ROLE_CONTEXT, userUuid)
                        .body(Mono.just(roles), UserRequestDto.class)
                        .retrieve()
                        .toEntity(UserDetailDto.class)
                        .block().getBody(),
                request);
    }

    public UserDetailDto updateRole(String userUuid, String roleUuid) {
        WebClient.RequestBodyUriSpec request = prepareRequest(HttpMethod.PUT);

        return processRequest(r -> r
                        .uri(USER_ROLE_CONTEXT + "/" + roleUuid, userUuid)
                        .retrieve()
                        .toEntity(UserDetailDto.class)
                        .block().getBody(),
                request);
    }

}
