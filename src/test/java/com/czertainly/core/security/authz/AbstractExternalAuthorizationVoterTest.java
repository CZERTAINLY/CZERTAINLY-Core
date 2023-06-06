package com.czertainly.core.security.authz;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterInvocation;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.access.AccessDecisionVoter.ACCESS_ABSTAIN;
import static org.springframework.security.access.AccessDecisionVoter.ACCESS_DENIED;

class AbstractExternalAuthorizationVoterTest {

    TestImplementationOfVoter voter = new TestImplementationOfVoter();

    Authentication authentication = createCzertainlyAuthentication();

    @Test
    void abstainsIfAuthenticationIsNotOfTypeCzertainlyAuthenticationToken() {
        // given
        Authentication auth = new UsernamePasswordAuthenticationToken(null, null);

        // when
        int result = voter.vote(auth, new Object(), List.of());

        // then
        assertEquals(ACCESS_ABSTAIN, result);
    }

    @Test
    void abstainsIfCantDecideForGivenObject() {
        // given
        voter.setCanDecideForGivenObject(false);

        // when
        int result = voter.vote(authentication, new Object(), List.of());

        // then
        assertEquals(ACCESS_ABSTAIN, result);
    }

    @Test
    void abstainsIfObjectIsNotSupported() {
        // given
        voter.setSupportsObject(false);

        // when
        int result = voter.vote(authentication, new FilterInvocation("/", "GET"), List.of());

        // then
        assertEquals(ACCESS_ABSTAIN, result);
    }

    @Test
    void returnsDecisionFromSubclass() {
        // given
        voter.setVoteResult(ACCESS_DENIED);

        // when
        int result = voter.vote(authentication, new Object(), List.of());

        // then
        assertEquals(ACCESS_DENIED, result);
    }

    @Test
    void returnsDecisionFromSubclassWhenAnonymousAuthIsUsed() {
        // given
        voter.setAnonymousVoteResult(ACCESS_DENIED);

        // when
        int result = voter.vote(getAnonymousToken(), new Object(), List.of());

        // then
        assertEquals(ACCESS_DENIED, result);
    }

    Authentication createCzertainlyAuthentication() {
        return new CzertainlyAuthenticationToken(
                new CzertainlyUserDetails(
                        new AuthenticationInfo(null, "FrantisekJednicka", List.of())
                )
        );
    }

    AnonymousAuthenticationToken getAnonymousToken() {
        return new AnonymousAuthenticationToken(
                UUID.randomUUID().toString(),
                new Object(),
                List.of(new SimpleGrantedAuthority("ANONYMOUS"))
        );
    }

    static class TestImplementationOfVoter extends AbstractExternalAuthorizationVoter<Object> {

        private int voteResult = ACCESS_GRANTED;
        private int anonymousVoteResult = ACCESS_GRANTED;
        private boolean canDecideForGivenObject = true;
        private boolean supportsObject = true;

        public void setVoteResult(int voteResult) {
            this.voteResult = voteResult;
        }

        public void setAnonymousVoteResult(int anonymousVoteResult) {
            this.anonymousVoteResult = anonymousVoteResult;
        }

        public void setCanDecideForGivenObject(boolean canDecideForGivenObject) {
            this.canDecideForGivenObject = canDecideForGivenObject;
        }

        public void setSupportsObject(boolean supportsObject) {
            this.supportsObject = supportsObject;
        }

        @Override
        protected int voteInternal(CzertainlyAuthenticationToken authentication, Object object, List<ExternalAuthorizationConfigAttribute> attributes) {
            return voteResult;
        }

        @Override
        protected int voteInternal(AnonymousAuthenticationToken authenticationToken, Object object, List<ExternalAuthorizationConfigAttribute> attributes) {
            return anonymousVoteResult;
        }

        @Override
        protected boolean canDecide(Authentication auth, Object object) {
            return canDecideForGivenObject;
        }

        @Override
        public boolean supports(Class clazz) {
            return supportsObject;
        }
    }
}