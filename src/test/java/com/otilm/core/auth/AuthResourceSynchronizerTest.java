package com.otilm.core.auth;

import com.otilm.api.model.core.auth.ResourcePermissionsDto;
import com.otilm.api.model.core.auth.RoleDto;
import com.otilm.api.model.core.auth.RolePermissionsRequestDto;
import com.otilm.api.model.core.auth.RoleWithPaginationDto;
import com.otilm.api.model.core.auth.SubjectPermissionsDto;
import com.otilm.core.model.auth.Resource;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.model.auth.ResourceSyncRequestDto;
import com.otilm.core.security.authn.client.ResourceApiClient;
import com.otilm.core.security.authn.client.RoleManagementApiClient;
import com.otilm.core.security.exception.AuthenticationServiceException;
import com.otilm.core.util.AuthHelper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.ConnectException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the startup step that realigns the auditor role with the resource catalogue the same boot has just synced.
 * The mapping itself is pinned by {@link ReadOnlyRolePermissionsTest}; what matters here is that the role is found,
 * written only when it is out of step, and that nothing here can stop the platform from starting.
 */
class AuthResourceSynchronizerTest {

    private static final String AUDITOR_ROLE_UUID = UUID.randomUUID().toString();

    private ContextRefreshListener contextRefreshListener;
    private ResourceApiClient resourceApiClient;
    private RoleManagementApiClient roleManagementApiClient;
    private AuthResourceSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        contextRefreshListener = mock(ContextRefreshListener.class);
        resourceApiClient = mock(ResourceApiClient.class);
        roleManagementApiClient = mock(RoleManagementApiClient.class);

        synchronizer = new AuthResourceSynchronizer();
        synchronizer.setEndpointsListener(contextRefreshListener);
        synchronizer.setEndPointApiClient(resourceApiClient);
        synchronizer.setRoleManagementApiClient(roleManagementApiClient);

        when(contextRefreshListener.getResources()).thenReturn(List.of(
                resource(Resource.CERTIFICATE, ResourceAction.LIST, ResourceAction.DETAIL, ResourceAction.REVOKE),
                resource(Resource.SECRET, ResourceAction.GET_SECRET_CONTENT)));
    }

    @Test
    void rebuildsTheAuditorPermissionsFromTheCatalogueItJustSynced() {
        auditorRoleExists(true);
        when(roleManagementApiClient.getPermissions(AUDITOR_ROLE_UUID)).thenReturn(storedPermissions());

        synchronizer.register();

        ArgumentCaptor<RolePermissionsRequestDto> saved = ArgumentCaptor.forClass(RolePermissionsRequestDto.class);
        verify(roleManagementApiClient).savePermissions(eq(AUDITOR_ROLE_UUID), saved.capture());
        assertThat(saved.getValue().getAllowAllResources()).isFalse();
        assertThat(saved.getValue().getResources()).singleElement().satisfies(certificates -> {
            assertThat(certificates.getName()).isEqualTo(Resource.CERTIFICATE.getCode());
            assertThat(certificates.getActions())
                    .containsExactly(ResourceAction.DETAIL.getCode(), ResourceAction.LIST.getCode());
        });
    }

    /** The role is identified the way the migrations identify system roles - by name among the system roles. */
    @Test
    void ignoresARoleNamedAuditorThatIsNotASystemRole() {
        auditorRoleExists(false);

        synchronizer.register();

        verify(roleManagementApiClient, never()).savePermissions(any(), any());
    }

    /** A deployment whose migration has not created the role yet must still boot, and say why it did nothing. */
    @Test
    void skipsReconciliationWhenTheAuditorRoleIsAbsent() {
        when(roleManagementApiClient.getRoles()).thenReturn(roles(role("superadmin", true)));

        synchronizer.register();

        verify(roleManagementApiClient, never()).savePermissions(any(), any());
    }

    @Test
    void leavesTheRoleAloneWhenItAlreadyHoldsTheDerivedGrants() {
        auditorRoleExists(true);
        when(roleManagementApiClient.getPermissions(AUDITOR_ROLE_UUID)).thenReturn(storedPermissions(
                storedResource(Resource.CERTIFICATE, ResourceAction.LIST.getCode(), ResourceAction.DETAIL.getCode())));

        synchronizer.register();

        verify(roleManagementApiClient, never()).savePermissions(any(), any());
    }

    /**
     * The catalogue the role is derived from is the one the auth service just accepted. If that sync did not happen,
     * the auth service does not know the resources and actions yet and would reject the permission save outright.
     */
    @Test
    void doesNotReconcileWhenTheResourceSyncFailed() {
        when(resourceApiClient.syncResources(any())).thenThrow(unreachableAuthService());

        assertThatCode(() -> synchronizer.register()).doesNotThrowAnyException();

        verifyNoInteractions(roleManagementApiClient);
    }

    @Test
    void completesStartupWhenTheAuthServiceRejectsThePermissionSave() {
        auditorRoleExists(true);
        when(roleManagementApiClient.getPermissions(AUDITOR_ROLE_UUID)).thenReturn(storedPermissions());
        when(roleManagementApiClient.savePermissions(any(), any()))
                .thenThrow(new AuthenticationServiceException("Unknown action 'ANY'"));

        assertThatCode(() -> synchronizer.register()).doesNotThrowAnyException();
    }

    @Test
    void completesStartupWhenTheRoleLookupFails() {
        when(roleManagementApiClient.getRoles()).thenThrow(unreachableAuthService());

        assertThatCode(() -> synchronizer.register()).doesNotThrowAnyException();

        verify(roleManagementApiClient, never()).savePermissions(any(), any());
    }

    /**
     * The derived set is large and reprinting it on every boot buries the one thing worth noticing - that it moved.
     * Counts say that; the payload does not.
     */
    @Test
    void logsACountSummaryOfTheChangeRatherThanThePayload() {
        auditorRoleExists(true);
        when(roleManagementApiClient.getPermissions(AUDITOR_ROLE_UUID)).thenReturn(storedPermissions());

        ListAppender<ILoggingEvent> logged = captureLogsOfSynchronizer();
        synchronizer.register();

        assertThat(logged.list)
                .filteredOn(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains(AuthHelper.AUDITOR_ROLE_NAME)
                        .contains("1 resources")
                        .contains("2 actions")
                        .doesNotContain(ResourceAction.DETAIL.getCode()));
    }

    private ListAppender<ILoggingEvent> captureLogsOfSynchronizer() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(AuthResourceSynchronizer.class)).addAppender(appender);
        return appender;
    }

    private void auditorRoleExists(boolean systemRole) {
        RoleDto auditor = role(AuthHelper.AUDITOR_ROLE_NAME, systemRole);
        auditor.setUuid(AUDITOR_ROLE_UUID);
        when(roleManagementApiClient.getRoles()).thenReturn(roles(role("superadmin", true), auditor));
    }

    private static WebClientRequestException unreachableAuthService() {
        return new WebClientRequestException(new ConnectException("Connection refused"), HttpMethod.POST,
                URI.create("http://localhost:8080/auth/resources/sync"), new HttpHeaders());
    }

    private static RoleWithPaginationDto roles(RoleDto... roles) {
        RoleWithPaginationDto dto = new RoleWithPaginationDto();
        dto.setData(List.of(roles));
        return dto;
    }

    private static RoleDto role(String name, boolean systemRole) {
        RoleDto dto = new RoleDto();
        dto.setUuid(UUID.randomUUID().toString());
        dto.setName(name);
        dto.setSystemRole(systemRole);
        return dto;
    }

    private static SubjectPermissionsDto storedPermissions(ResourcePermissionsDto... resources) {
        SubjectPermissionsDto dto = new SubjectPermissionsDto();
        dto.setAllowAllResources(false);
        dto.setResources(List.of(resources));
        return dto;
    }

    private static ResourcePermissionsDto storedResource(Resource resource, String... actions) {
        ResourcePermissionsDto dto = new ResourcePermissionsDto();
        dto.setName(resource.getCode());
        dto.setAllowAllActions(false);
        dto.setActions(List.of(actions));
        dto.setObjects(List.of());
        return dto;
    }

    private static ResourceSyncRequestDto resource(Resource resource, ResourceAction... actions) {
        ResourceSyncRequestDto dto = new ResourceSyncRequestDto();
        dto.setName(resource);
        dto.setActions(Arrays.stream(actions).map(ResourceAction::getCode).toList());
        return dto;
    }
}
