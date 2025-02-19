package com.czertainly.core.security.authz;

import com.czertainly.api.model.core.logging.enums.AuthMethod;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.AnonymousPrincipal;
import com.czertainly.core.security.authz.opa.dto.OpaRequestDetails;
import com.czertainly.core.security.authz.opa.dto.OpaRequestedResource;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.czertainly.core.service.impl.AuditLogServiceImpl;
import com.czertainly.core.util.AuthenticationTokenTestHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalFilterAuthorizationManagerTest {

    @Mock
    OpaClient opaClient;

    @Mock
    WebAuthenticationDetails details;

    @Spy
    ObjectMapper om = new ObjectMapper();

    @InjectMocks
    ExternalFilterAuthorizationManager manager;

    CzertainlyAuthenticationToken authentication = createCzertainlyAuthentication();

    RequestAuthorizationContext authorizationContext;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/v1/groups");
        authorizationContext = new RequestAuthorizationContext(request);
        manager = new ExternalFilterAuthorizationManager(opaClient, new ObjectMapper(), new AuditLogServiceImpl());
    }

    @Test
    void accessIsGrantedWhenOpaAuthorizesIt() {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(accessGranted());

        // when
        AuthorizationResult result = manager.authorize(() -> authentication, authorizationContext);

        // then
        assertTrue(result.isGranted());
    }

    @Test
    void accessIsDeniedWhenOpaDeniesIt() {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(OpaResourceAccessResult.unauthorized());

        // when
        AuthorizationResult result = manager.authorize(() -> authentication, authorizationContext);

        // then
        assertFalse(result.isGranted());
    }

    @Test
    void endpointUrlIsPassedToOpa() {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // when
        manager.authorize(() -> authentication, authorizationContext);

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertTrue( resource.getUrl().containsAll(List.of("v1", "groups")));
    }

    @Test
    void remoteAddressIsPassedToOpaIfPresent() {
        // setup
        ArgumentCaptor<OpaRequestDetails> detailsCaptor = ArgumentCaptor.forClass(OpaRequestDetails.class);
        when(opaClient.checkResourceAccess(any(), any(), any(), detailsCaptor.capture()))
                .thenReturn(accessGranted());

        // given
        when(details.getRemoteAddress()).thenReturn("127.0.0.1");
        authentication.setDetails(details);

        // when
        manager.authorize(() -> authentication, authorizationContext);

        // then
        OpaRequestDetails opaRequestDetails = detailsCaptor.getValue();
        assertEquals("127.0.0.1", opaRequestDetails.getRemoteAddress());
    }

    @Test
    void accessIsDeniedWhenRequestToOpaFails() {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Ooops..."));

        // when
        AuthorizationResult result = manager.authorize(() -> authentication, authorizationContext);

        // then
        assertFalse(result.isGranted());
    }

    @Test
    void abstainsIfCantDecideForGivenUrl() {
        // given
        manager.setToAuthorizeRequestsMatcher(new AntPathRequestMatcher("/my/url"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/another/url");
        authorizationContext = new RequestAuthorizationContext(request);

        // when
        AuthorizationResult result = manager.authorize(() -> authentication, authorizationContext);

        // then
        assertFalse(result.isGranted());
    }

    @Test
    void anonymousPrincipalIsSendToOpaWhenAnonymousTokenIsUsed() throws JsonProcessingException {
        // setup
        ArgumentCaptor<String> principalCaptor = ArgumentCaptor.forClass(String.class);
        when(opaClient.checkResourceAccess(any(), any(), principalCaptor.capture(), any()))
                .thenReturn(accessGranted());

        // given
        Authentication anonymousAuthenticationToken = AuthenticationTokenTestHelper.getAnonymousToken("anonymousUser");

        // when
        manager.authorize(() -> anonymousAuthenticationToken, authorizationContext);

        // then
        String principal = principalCaptor.getValue();
        assertEquals(principal, om.writeValueAsString(new AnonymousPrincipal("anonymousUser")));
    }

    @Test
    void anonymousRequestIsNotAuthorizedWhenExplicitlyExcluded() {
        // given
        Authentication anonymousToken = AuthenticationTokenTestHelper.getAnonymousToken("anonymousUser");
        manager.setDoNotAuthorizeAnonymousRequestsMatcher(new AntPathRequestMatcher("/error"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/error");
        authorizationContext = new RequestAuthorizationContext(request);

        // when
        AuthorizationResult result = manager.authorize(() -> anonymousToken, authorizationContext);

        // then
        assertFalse(result.isGranted());
    }

    CzertainlyAuthenticationToken createCzertainlyAuthentication() {
        return new CzertainlyAuthenticationToken(
                new CzertainlyUserDetails(
                        new AuthenticationInfo(AuthMethod.USER_PROXY, null, "FrantisekJednicka", List.of())
                )
        );
    }

    OpaResourceAccessResult accessGranted() {
        OpaResourceAccessResult result = new OpaResourceAccessResult();
        result.setAuthorized(true);
        result.setAllow(List.of());
        return result;
    }
}