package com.czertainly.core.security.authz;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.AnonymousPrincipal;
import com.czertainly.core.security.authz.opa.dto.OpaRequestDetails;
import com.czertainly.core.security.authz.opa.dto.OpaRequestedResource;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.czertainly.core.util.AuthenticationTokenTestHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.access.AccessDecisionVoter.*;

@ExtendWith(MockitoExtension.class)
class ExternalFilterAuthorizationVoterTest {

    @Mock
    OpaClient opaClient;

    @Mock
    WebAuthenticationDetails details;

    @Spy
    ObjectMapper om = new ObjectMapper();

    @InjectMocks
    ExternalFilterAuthorizationVoter voter;


    CzertainlyAuthenticationToken authentication = createCzertainlyAuthentication();

    FilterInvocation fi = new FilterInvocation("/v1/groups", "GET");

    @Test
    void accessIsGrantedWhenOpaAuthorizesIt() {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(accessGranted());

        // when
        int result = voter.vote(authentication, fi, List.of());

        // then
        assertEquals(ACCESS_GRANTED, result);
    }

    @Test
    void accessIsDeniedWhenOpaDeniesIt() {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(OpaResourceAccessResult.unauthorized());

        // when
        int result = voter.vote(authentication, fi, List.of());

        // then
        assertEquals(ACCESS_DENIED, result);
    }

    @Test
    void endpointUrlIsPassedToOpa() {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        FilterInvocation fi = new FilterInvocation("/v1/groups", "GET");

        // when
        voter.vote(authentication, fi, List.of());

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertEquals(List.of("v1", "groups"), resource.getUrl());
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
        voter.vote(authentication, fi, List.of());

        // then
        OpaRequestDetails details = detailsCaptor.getValue();
        assertEquals("127.0.0.1", details.getRemoteAddress());
    }

    @Test
    void accessIsDeniedWhenRequestToOpaFails() {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Ooops..."));

        // when
        int result = voter.vote(authentication, fi, List.of());

        // then
        assertEquals(ACCESS_DENIED, result);
    }

    @Test
    void abstainsIfCantDecideForGivenUrl() {
        // given
        voter.setToAuthorizeRequestsMatcher(new AntPathRequestMatcher("/my/url"));
        FilterInvocation fi = new FilterInvocation("/another/url", "GET");

        // when
        int result = voter.vote(authentication, fi, List.of());

        // then
        assertEquals(ACCESS_ABSTAIN, result);
    }

    @Test
    void anonymousPrincipalIsSendToOpaWhenAnonymousTokenIsUsed() throws JsonProcessingException {
        // setup
        ArgumentCaptor<String> principalCaptor = ArgumentCaptor.forClass(String.class);
        when(opaClient.checkResourceAccess(any(), any(), principalCaptor.capture(), any()))
                .thenReturn(accessGranted());

        // given
        Authentication authentication = AuthenticationTokenTestHelper.getAnonymousToken("anonymousUser");

        // when
        voter.vote(authentication, fi, List.of());

        // then
        String principal = principalCaptor.getValue();
        assertEquals(principal, om.writeValueAsString(new AnonymousPrincipal("anonymousUser")));
    }

    @Test
    void anonymousRequestIsNotAuthorizedWhenExplicitlyExcluded() {
        // given
        Authentication authentication = AuthenticationTokenTestHelper.getAnonymousToken("anonymousUser");
        voter.setDoNotAuthorizeAnonymousRequestsMatcher(new AntPathRequestMatcher("/error"));
        FilterInvocation fi = new FilterInvocation("/error", "GET");

        // when
        int result = voter.vote(authentication, fi, List.of());

        // then
        assertEquals(ACCESS_ABSTAIN, result);
    }

    CzertainlyAuthenticationToken createCzertainlyAuthentication() {
        return new CzertainlyAuthenticationToken(
                new CzertainlyUserDetails(
                        new AuthenticationInfo("FrantisekJednicka", List.of())
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