package com.czertainly.core.security.authz;

import com.czertainly.api.model.core.logging.enums.AuthMethod;
import com.czertainly.core.config.OpaSecuredAnnotationMetadataExtractor;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.SimpleMethodInvocation;

import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalMethodAuthorizationManagerTest {

    @Mock
    OpaClient opaClient;

    @Mock
    OpaSecuredAnnotationMetadataExtractor metadataExtractor;

    @Spy
    ObjectMapper om = new ObjectMapper();

    @InjectMocks
    ExternalMethodAuthorizationManager manager;

    CzertainlyAuthenticationToken authentication = createCzertainlyAuthentication();

    @Test
    void accessIsGrantedWhenOpaAuthorizesIt() throws NoSuchMethodException {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(accessGranted());

        when(metadataExtractor.extractAttributes(any())).thenReturn(List.of());

        // when
        AuthorizationDecision result = manager.check(() -> authentication, methodInvocationWithoutSecuredUUIDs());

        // then
        Assertions.assertTrue(result.isGranted());
    }

    @Test
    void accessIsDeniedWhenOpaDeniesIt() throws NoSuchMethodException {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenReturn(OpaResourceAccessResult.unauthorized());

        when(metadataExtractor.extractAttributes(any())).thenReturn(List.of());

        // when
        AuthorizationDecision result = manager.check(() -> authentication, methodInvocationWithoutSecuredUUIDs());

        // then
        Assertions.assertFalse(result.isGranted());
    }

    @Test
    void accessIsDeniedWhenRequestToOpaFails() throws NoSuchMethodException {
        // given
        when(opaClient.checkResourceAccess(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Ooops..."));

        when(metadataExtractor.extractAttributes(any())).thenReturn(List.of());

        // when
        AuthorizationDecision result = manager.check(() -> authentication, methodInvocationWithoutSecuredUUIDs());

        // then
        Assertions.assertFalse(result.isGranted());
    }

    @Test
    void sendsSecuredUUIDToOpa() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());
        when(metadataExtractor.extractAttributes(any())).thenReturn(List.of());

        // given
        MethodInvocation mi = methodInvocationWithSecuredUUID("abfbc322-29e1-11ed-a261-0242ac120002");

        // when
        manager.check(() -> authentication, mi);

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertEquals(List.of("abfbc322-29e1-11ed-a261-0242ac120002"), resource.getObjectUUIDs());
    }

    @Test
    void sendsListOfSecuredUUIDsToOpa() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());
        when(metadataExtractor.extractAttributes(any())).thenReturn(List.of());

        // given
        MethodInvocation mi = methodInvocationWithListOfSecuredUUIDs("abfbc322-29e1-11ed-a261-0242ac120002", "abfbc322-29e1-11ed-a261-0242ac120003");

        // when
        manager.check(() -> authentication, mi);

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertEquals(List.of("abfbc322-29e1-11ed-a261-0242ac120002", "abfbc322-29e1-11ed-a261-0242ac120003"), resource.getObjectUUIDs());
    }

    @Test
    void callsParentUUIDGetterWhenProvided() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        List<ExternalAuthorizationConfigAttribute> attributes = new ArrayList<>();
        attributes.add(new ExternalAuthorizationConfigAttribute("parentUUIDsGetter", TestParentUUIDGetter.class));
        when(metadataExtractor.extractAttributes(any())).thenReturn(attributes);

        // when
        manager.check(() -> authentication, methodInvocationWithSecuredUUID("abfbc322-29e1-11ed-a261-0242ac120002"));

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertEquals(List.of("abfbc322-29e1-11ed-a261-0242ac120002", "abfbc322-29e1-11ed-a261-0242ac120003"), resource.getParentObjectUUIDs());
    }

    @Test
    void noOpUUIDGetterIsIgnored() throws NoSuchMethodException {
        // setup
        ArgumentCaptor<OpaRequestedResource> resourceCaptor = ArgumentCaptor.forClass(OpaRequestedResource.class);
        when(opaClient.checkResourceAccess(any(), resourceCaptor.capture(), any(), any()))
                .thenReturn(accessGranted());

        // given
        List<ExternalAuthorizationConfigAttribute> attributes = new ArrayList<>();
        attributes.add(new ExternalAuthorizationConfigAttribute("parentUUIDsGetter", NoOpParentUUIDGetter.class));
        when(metadataExtractor.extractAttributes(any())).thenReturn(attributes);

        // when
        manager.check(() -> authentication, methodInvocationWithSecuredUUID("abfbc322-29e1-11ed-a261-0242ac120002"));

        // then
        OpaRequestedResource resource = resourceCaptor.getValue();
        assertNull(resource.getParentObjectUUIDs());
    }

    @Test
    void accessIsDeniedWhenParentUUIDGetterProvidedAndObjectUUIDsAreMissing() throws NoSuchMethodException {
        // given
        List<ExternalAuthorizationConfigAttribute> attributes = new ArrayList<>();
        attributes.add(new ExternalAuthorizationConfigAttribute("parentUUIDsGetter", TestParentUUIDGetter.class));
        when(metadataExtractor.extractAttributes(any())).thenReturn(attributes);
        MethodInvocation mi = methodInvocationWithoutSecuredUUIDs();

        // when
        AuthorizationDecision result = manager.check(() -> authentication, mi);

        // then
        assertFalse(result.isGranted());
    }

    @Test
    void anonymousPrincipalIsSendToOpaWhenAnonymousTokenIsUsed() throws JsonProcessingException, NoSuchMethodException {
        // setup
        ArgumentCaptor<String> principalCaptor = ArgumentCaptor.forClass(String.class);
        when(opaClient.checkResourceAccess(any(), any(), principalCaptor.capture(), any()))
                .thenReturn(accessGranted());
        when(metadataExtractor.extractAttributes(any())).thenReturn(List.of());

        // given
        Authentication anonymousToken = AuthenticationTokenTestHelper.getAnonymousToken("anonymousUser");

        // when
        manager.check(() -> anonymousToken, methodInvocationWithoutSecuredUUIDs());

        // then
        String principal = principalCaptor.getValue();
        assertEquals(principal, om.writeValueAsString(new AnonymousPrincipal("anonymousUser")));
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
                Arrays.stream(uuids).map(SecuredUUID::new).toList());
    }


    static class TestClass {

        public void ordinaryMethod() {
            // Test method
        }

        @SuppressWarnings("unused")
        public void methodWithSecuredUUID(SecuredUUID uuid) {
            // Test method
        }

        @SuppressWarnings("unused")
        public void methodWithListOfSecuredUUIDs(List<SecuredUUID> uuids) {
            // Test method
        }
    }

    static class TestParentUUIDGetter implements ParentUUIDGetter {

        public TestParentUUIDGetter() {
            // Test method
        }

        @Override
        public List<String> getParentsUUID(List<String> objectsUUID) {
            return List.of("abfbc322-29e1-11ed-a261-0242ac120002", "abfbc322-29e1-11ed-a261-0242ac120003");
        }
    }

}