package com.czertainly.core.util;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.OpaObjectAccessResult;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@Import(SpringBootTestContext.class)
@Transactional
@Rollback
public class BaseSpringBootTest {

    @Autowired
    OpaClient opaClient;

    @BeforeEach
    public void setupAuth() {
        mockSuccessfulCheckResourceAccess();
        mockSuccessfulCheckObjectAccess();
        injectAuthentication();
    }

    protected void mockSuccessfulCheckResourceAccess() {
        OpaResourceAccessResult accessAllowed = new OpaResourceAccessResult();
        accessAllowed.setAuthorized(true);
        accessAllowed.setAllow(List.of());

        Mockito.when(
                opaClient.checkResourceAccess(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())
        ).thenReturn(accessAllowed);
    }

    protected void mockSuccessfulCheckObjectAccess() {
        OpaObjectAccessResult objectAccessAllowed = new OpaObjectAccessResult();
        objectAccessAllowed.setActionAllowedForGroupOfObjects(true);
        objectAccessAllowed.setAllowedObjects(List.of());
        objectAccessAllowed.setForbiddenObjects(List.of());

        Mockito.when(
                opaClient.checkObjectAccess(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())
        ).thenReturn(objectAccessAllowed);
    }

    protected void injectAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(getAuthentication());
    }

    protected Authentication getAuthentication() {
        AuthenticationInfo info = new AuthenticationInfo(null, "username", List.of());
        return new CzertainlyAuthenticationToken(new CzertainlyUserDetails(info));
    }
}
