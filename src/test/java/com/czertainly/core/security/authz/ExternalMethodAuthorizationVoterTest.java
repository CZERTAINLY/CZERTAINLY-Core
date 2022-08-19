package com.czertainly.core.security.authz;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.AnonymousPrincipal;
import com.czertainly.core.security.authz.opa.dto.OpaRequestedResource;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.czertainly.core.util.AuthenticationTokenTestHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.SimpleMethodInvocation;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.access.AccessDecisionVoter.ACCESS_DENIED;
import static org.springframework.security.access.AccessDecisionVoter.ACCESS_GRANTED;

@ExtendWith(MockitoExtension.class)
class ExternalMethodAuthorizationVoterTest {

    @Mock
    OpaClient opaClient;

    @Spy
    ObjectMapper om = new ObjectMapper();

    @InjectMocks
    ExternalMethodAuthorizationVoter voter;

    CzertainlyAuthenticationToken authentication = createCzertainlyAuthentication();

    @Test
    void accessIsGrantedWhenOpaAuthorizesIt() throws NoSuchMethodException {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(accessGranted());

        // when
        int result = voter.vote(authentication, methodInvocationWithoutSecuredUUIDs(), List.of());

        // then
        assertEquals(ACCESS_GRANTED, result);
    }

    @Test
    void accessIsDeniedWhenOpaDeniesIt() throws NoSuchMethodException {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(OpaResourceAccessResult.unauthorized());

        // when
        int result = voter.vote(authentication, methodInvocationWithoutSecuredUUIDs(), List.of());

        // then
        assertEquals(ACCESS_DENIED, result);
    }


    @Test
    void accessIsDeniedWhenRequestToOpaFails() throws NoSuchMethodException {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Ooops..."));

        // when
        int result = voter.vote(authentication, methodInvocationWithoutSecuredUUIDs(), List.of());

        // then
        assertEquals(ACCESS_DENIED, result);
    }

    @Test
    void doesNotSendCustomObjectTypeAttributesToOpa() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        Collection<ConfigAttribute> attributes = new ArrayList<>();
        attributes.add(new ExternalAuthorizationConfigAttribute("string", "GROUPS"));
        attributes.add(new ExternalAuthorizationConfigAttribute("boolean", true));
        attributes.add(new ExternalAuthorizationConfigAttribute("int", 42));
        attributes.add(new ExternalAuthorizationConfigAttribute("float", 3.14f));
        attributes.add(new ExternalAuthorizationConfigAttribute("double", 3.14d));
        attributes.add(new ExternalAuthorizationConfigAttribute("object", new Object()));

        // when
        voter.vote(authentication, methodInvocationWithoutSecuredUUIDs(), attributes);

        // then
        Map<String, String> properties = resourceCaptor.getValue().getProperties();
        assertEquals("GROUPS", properties.get("string"));
        assertEquals("true", properties.get("boolean"));
        assertEquals("42", properties.get("int"));
        assertEquals("3.14", properties.get("float"));
        assertEquals("3.14", properties.get("double"));
        assertEquals(5, properties.size());
    }

    @Test
    void sendsSecuredUUIDToOpa() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        MethodInvocation mi = methodInvocationWithSecuredUUID("123-456");

        // when
        voter.vote(authentication, mi, List.of());

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertEquals(List.of("123-456"), resource.getObjectUUIDs());
    }

    @Test
    void sendsListOfSecuredUUIDsToOpa() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        MethodInvocation mi = methodInvocationWithListOfSecuredUUIDs("123-456", "789-abc");

        // when
        voter.vote(authentication, mi, List.of());

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertEquals(List.of("123-456", "789-abc"), resource.getObjectUUIDs());
    }

    @Test
    void callsParentUUIDGetterWhenProvided() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        Collection<ConfigAttribute> attributes = new ArrayList<>();
        attributes.add(new ExternalAuthorizationConfigAttribute("parentUUIDsGetter", TestParentUUIDGetter.class));

        // when
        voter.vote(authentication, methodInvocationWithSecuredUUID("123-456"), attributes);

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertEquals(List.of("abc-def", "987-asd"), resource.getParentObjectUUIDs());
    }

    @Test
    void NoOpUUIDGetterIsIgnored() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        Collection<ConfigAttribute> attributes = new ArrayList<>();
        attributes.add(new ExternalAuthorizationConfigAttribute("parentUUIDsGetter", NoOpParentUUIDGetter.class));

        // when
        voter.vote(authentication, methodInvocationWithSecuredUUID("123-456"), attributes);

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertNull(resource.getParentObjectUUIDs());
    }

    @Test
    void accessIsDeniedWhenParentUUIDGetterProvidedAndObjectUUIDsAreMissing() throws NoSuchMethodException {
        // given
        Collection<ConfigAttribute> attributes = new ArrayList<>();
        attributes.add(new ExternalAuthorizationConfigAttribute("parentUUIDsGetter", TestParentUUIDGetter.class));
        MethodInvocation mi = methodInvocationWithoutSecuredUUIDs();

        // when
        int result = voter.vote(authentication, mi, attributes);

        // then
        assertEquals(ACCESS_DENIED, result);
    }

    @Test
    void anonymousPrincipalIsSendToOpaWhenAnonymousTokenIsUsed() throws JsonProcessingException, NoSuchMethodException {
        // setup
        ArgumentCaptor<String> principalCaptor = ArgumentCaptor.forClass(String.class);
        when(opaClient.checkResourceAccess(any(), any(), principalCaptor.capture(), any()))
                .thenReturn(accessGranted());

        // given
        Authentication authentication = AuthenticationTokenTestHelper.getAnonymousToken("anonymousUser");

        // when
        voter.vote(authentication, methodInvocationWithoutSecuredUUIDs(), List.of());

        // then
        String principal = principalCaptor.getValue();
        assertEquals(principal, om.writeValueAsString(new AnonymousPrincipal("anonymousUser")));
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

    MethodInvocation methodInvocationWithoutSecuredUUIDs() throws NoSuchMethodException {
        return new SimpleMethodInvocation(
                new Object(),
                TestClass.class.getMethod("ordinaryMethod")
        );
    }


    @SuppressWarnings("SameParameterValue")
    MethodInvocation methodInvocationWithSecuredUUID(String uuid) throws NoSuchMethodException {
        return new SimpleMethodInvocation(
                new Object(),
                TestClass.class.getMethod("methodWithSecuredUUID", SecuredUUID.class),
                new SecuredUUID(uuid)
        );
    }

    MethodInvocation methodInvocationWithListOfSecuredUUIDs(String... uuids) throws NoSuchMethodException {
        return new SimpleMethodInvocation(
                new Object(),
                TestClass.class.getMethod("methodWithListOfSecuredUUIDs", List.class),
                Arrays.stream(uuids).map(SecuredUUID::new).collect(Collectors.toList())
        );
    }


    static class TestClass {

        public void ordinaryMethod() {
        }

        @SuppressWarnings("unused")
        public void methodWithSecuredUUID(SecuredUUID uuid) {
        }

        @SuppressWarnings("unused")
        public void methodWithListOfSecuredUUIDs(List<SecuredUUID> uuids) {
        }
    }

    static class TestParentUUIDGetter implements ParentUUIDGetter {

        public TestParentUUIDGetter() {
        }

        @Override
        public List<String> getParentsUUID(List<String> objectsUUID) {
            return List.of("abc-def", "987-asd");
        }
    }

}