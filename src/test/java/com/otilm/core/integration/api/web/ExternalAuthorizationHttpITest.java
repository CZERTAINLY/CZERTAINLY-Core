package com.otilm.core.integration.api.web;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression suite for {@code @ExternalAuthorization} over HTTP: the real security filter chain, the method-security
 * advisor on the service bean behind the controller, and the translation of a denial into a response by
 * {@code ExceptionHandlingAdvice.handleAccessDeniedException}.
 *
 * <p>
 * The denial cases double as the guard on request-scoped denied-permission recording: the test schema has no Spring
 * Session tables, so if the denied resource/action ever goes back to session scope, recording it creates a session and
 * these tests fail on the {@code core.spring_session} insert. Add the DDL to make that failure go away and the guard is
 * gone with it.
 *
 * <p>
 * {@code /v1/statistics/signingRecords} is the target because {@code SigningRecordServiceImpl} guards it with both a
 * resource and a parent resource, and takes a {@code SecurityFilter} rather than a {@code SecuredUUID} — so one
 * endpoint exercises parent-before-child ordering and the no-object-UUID request shape that
 * {@code ExternalMethodAuthorizationManager} derives from the method arguments.
 */
@AutoConfigureMockMvc
class ExternalAuthorizationHttpITest extends BaseSpringBootTest {

    private static final String STATISTICS_ENDPOINT = "/v1/statistics/signingRecords";

    @Autowired
    private MockMvc mockMvc;

    /**
     * The body's own {@code statusCode} field is not asserted: it is filled in by the interfaces module, which reported
     * 400 for every error until interfaces#887. Assert it once core consumes a build carrying that fix.
     */
    @Test
    void returnsForbiddenProblemJsonNamingTheDeniedResourceAndAction() throws Exception {
        denyResourceAccess(Resource.SIGNING_RECORD, ResourceAction.LIST);

        mockMvc
                .perform(get(STATISTICS_ENDPOINT))
                .andExpectAll(status().isForbidden(), content().contentType("application/problem+json"),
                        jsonPath("$.code").value("ACCESS_DENIED"),
                        jsonPath("$.message").value("Access Denied. Required 'List' permission for 'Signing Record'"));
    }

    /**
     * The denied pair reported to the caller must be the check that actually failed. When the parent is denied the
     * message has to name the parent resource, otherwise an operator is sent to fix a permission that was never the
     * problem.
     */
    @Test
    void namesTheParentResourceWhenTheParentCheckIsDenied() throws Exception {
        denyResourceAccess(Resource.SIGNING_PROFILE, ResourceAction.LIST);

        mockMvc
                .perform(get(STATISTICS_ENDPOINT))
                .andExpectAll(status().isForbidden(), jsonPath("$.code").value("ACCESS_DENIED"),
                        jsonPath("$.message").value("Access Denied. Required 'List' permission for 'Signing Profile'"));
    }

    /**
     * The other branch of the denied-pair reader: an object-level OPA failure propagates the
     * {@code AccessDeniedException} that {@code OpaClient} raises straight out of
     * {@code AuthHelper.loadObjectPermissions}, and nothing on that path records a resource/action pair. The caller
     * still gets a 403 rather than a 500, with the generic message instead of a named permission.
     *
     * <p>
     * A resource-level denial cannot exercise this branch — {@code ExternalAuthorizationCore.decideBasedOnOpaResult}
     * records the pair before returning one, including when the OPA call itself failed.
     */
    @Test
    void returnsForbiddenWhenTheDenialRecordedNoPermissionPair() throws Exception {
        when(opaClient.checkObjectAccess(any(), any(), any(), any()))
                .thenThrow(new AccessDeniedException("An error occurred when calling OPA."));

        mockMvc
                .perform(get(STATISTICS_ENDPOINT))
                .andExpectAll(status().isForbidden(), jsonPath("$.code").value("ACCESS_DENIED"),
                        jsonPath("$.message").value(startsWith("Access denied for the specified operation")));
    }

    /**
     * A method taking a {@code SecurityFilter} authorizes the action broadly and narrows the result set afterwards, so
     * no object UUIDs are submitted. Sending UUIDs here would change the policy input for every list endpoint.
     */
    @Test
    void sendsNoObjectUuidsForASecurityFilterEndpoint() throws Exception {
        mockMvc.perform(get(STATISTICS_ENDPOINT)).andExpect(status().isOk());

        assertThat(captureOpaRequests())
                .as("the guarded service method was reached through the advisor")
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.getObjectUUIDs()).isNull());
    }
}
