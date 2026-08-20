package com.otilm.core.integration.security.authz;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.security.authz.SecuredParentUUID;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.security.authz.opa.dto.OpaRequestedResource;
import com.otilm.core.service.LocationExternalService;
import com.otilm.core.service.SigningRecordExternalService;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression suite for the static {@link com.otilm.core.security.authz.ExternalAuthorization} annotation exercised
 * through the real Spring AOP stack, entered via the advisor wired in {@code MethodSecurityConfig}.
 *
 * <p>
 * The sibling {@code ExternalAuthorizationDynamicITest} covers {@code @ExternalAuthorizationDynamic}, whose resource is
 * resolved from an argument. This class covers the annotation used by every other guarded service method, pinning the
 * request actually sent to OPA: resource, action, object UUIDs and parent-before-child ordering.
 *
 * <p>
 * Both target resources are declared without owner or group associations, so a denied OPA decision is not rescued by
 * the group/owner fallback in {@code ExternalAuthorizationCore.checkGroupOwnerAssociations} and reaches the caller as a
 * denial. Choosing resources with associations would test the fallback instead of the advisor.
 */
class ExternalAuthorizationStaticITest extends BaseSpringBootTest {

    @Autowired
    private SigningRecordExternalService signingRecordService;

    @Autowired
    private LocationExternalService locationService;

    /**
     * Asserts OPA was consulted as well as that the call succeeded: an unadvised bean would also return a result, so
     * the return value alone would pass even if the advisor never ran.
     */
    @Test
    void permitsWhenOpaAuthorizesAnnotatedResourceAndAction() {
        assertThat(signingRecordService.getSearchableFieldInformation())
                .as("an authorized call runs to completion through the advisor")
                .isNotNull();
        assertThat(captureOpaRequests()).isNotEmpty();
    }

    /**
     * A token-type rejection produces the same {@link AuthorizationDeniedException} without ever consulting OPA. The
     * captured request is therefore the discriminator: asserting the type alone would stay green even if the platform
     * token stopped being injected and this path went unexercised.
     */
    @Test
    void deniesWhenOpaRejectsAnnotatedResourceAndAction() {
        denyResourceAccess(Resource.SIGNING_RECORD, ResourceAction.LIST);

        assertThatThrownBy(() -> signingRecordService.getSearchableFieldInformation())
                .isInstanceOf(AuthorizationDeniedException.class);

        assertThat(captureOpaRequests()).anySatisfy(request -> {
            assertThat(request.getProperties()).containsEntry("name", Resource.SIGNING_RECORD.getCode());
            assertThat(request.getProperties()).containsEntry("action", ResourceAction.LIST.getCode());
        });
    }

    /**
     * An unrecognised token type is rejected by {@code AbstractExternalAuthorizationManager} before any policy call, so
     * the absence of an OPA request is what distinguishes this denial from one OPA itself made.
     */
    @Test
    void deniesWhenAuthenticationIsNotAPlatformToken() {
        SecurityContextHolder
                .getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("someone", "secret", List.of()));

        assertThatThrownBy(() -> signingRecordService.getSearchableFieldInformation())
                .isInstanceOf(AuthorizationDeniedException.class);
        verify(opaClient, never()).checkResourceAccess(any(), any(), any(), any());
    }

    @Test
    void sendsAnnotatedResourceAndActionToOpa() {
        signingRecordService.getSearchableFieldInformation();

        assertThat(captureOpaRequests())
                .as("the resource and action named on the annotation are what OPA is asked about")
                .anySatisfy(request -> {
                    assertThat(request.getProperties()).containsEntry("name", Resource.SIGNING_RECORD.getCode());
                    assertThat(request.getProperties()).containsEntry("action", ResourceAction.LIST.getCode());
                });
    }

    /**
     * The parent has to be evaluated before the child so that a denied parent can short-circuit the child check, as
     * {@link #skipsChildCheckWhenParentIsDenied} requires. Submission order is the only evidence of that sequencing.
     */
    @Test
    void checksParentBeforeChild() {
        assertThatThrownBy(() -> locationService
                .getLocation(SecuredParentUUID.fromUUID(UUID.randomUUID()), SecuredUUID.fromUUID(UUID.randomUUID())))
                .as("authorization passes, so the method body runs and fails to find the location")
                .isInstanceOf(NotFoundException.class);

        List<OpaRequestedResource> requests = captureOpaRequests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getProperties())
                .containsEntry("name", Resource.ENTITY.getCode())
                .containsEntry("action", ResourceAction.DETAIL.getCode());
        assertThat(requests.get(1).getProperties())
                .containsEntry("name", Resource.LOCATION.getCode())
                .containsEntry("action", ResourceAction.DETAIL.getCode());
    }

    /**
     * Each check carries the UUID of the object it guards. A check reusing the other's UUID would ask OPA about an
     * object the caller never named, and the answer would authorize the wrong one.
     */
    @Test
    void sendsEachCheckItsOwnObjectUuid() {
        UUID entityUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();

        assertThatThrownBy(() -> locationService
                .getLocation(SecuredParentUUID.fromUUID(entityUuid), SecuredUUID.fromUUID(locationUuid)))
                .isInstanceOf(NotFoundException.class);

        List<OpaRequestedResource> requests = captureOpaRequests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getObjectUUIDs()).containsExactly(entityUuid.toString());
        assertThat(requests.get(1).getObjectUUIDs()).containsExactly(locationUuid.toString());
    }

    /**
     * The {@code parentName} and {@code parentAction} properties are internal routing hints and must not reach OPA.
     */
    @Test
    void omitsInternalRoutingHintsFromTheOpaRequest() {
        assertThatThrownBy(() -> locationService
                .getLocation(SecuredParentUUID.fromUUID(UUID.randomUUID()), SecuredUUID.fromUUID(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);

        assertThat(captureOpaRequests())
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.getProperties())
                        .doesNotContainKeys("parentName", "parentAction"));
    }

    /**
     * A denied parent must short-circuit: the child resource is never submitted to OPA. Without this ordering a caller
     * denied on the parent could still have the child evaluated, which leaks whether the child permits the action.
     */
    @Test
    void skipsChildCheckWhenParentIsDenied() {
        denyResourceAccess(Resource.ENTITY, ResourceAction.DETAIL);

        assertThatThrownBy(() -> locationService
                .getLocation(SecuredParentUUID.fromUUID(UUID.randomUUID()), SecuredUUID.fromUUID(UUID.randomUUID())))
                .isInstanceOf(AuthorizationDeniedException.class);

        assertThat(captureOpaRequests())
                .as("only the parent resource is submitted to OPA")
                .allSatisfy(request -> assertThat(request.getProperties())
                        .containsEntry("name", Resource.ENTITY.getCode()));
    }
}
