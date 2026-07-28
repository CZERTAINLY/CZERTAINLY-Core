package com.otilm.core.service.scep.impl;

import com.otilm.api.exception.ScepException;
import com.otilm.api.model.core.scep.FailInfo;
import com.otilm.core.dao.entity.scep.ScepProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the challenge password gate {@link ScepServiceImpl#validateChallengePassword}.
 * A renewal PKCSReq carries no challengePassword attribute (RFC 8894 §3.3.1.2) — it authenticates
 * with the existing certificate's key instead — so the gate must tolerate an absent request
 * password for an authenticated renewal and reject it otherwise, never dereference it blindly.
 */
class ScepServiceImplChallengePasswordTest {

    private static final String PROFILE_PASSWORD = "mysecretpassword";

    private ScepServiceImpl service;
    private ScepProfile profile;

    @BeforeEach
    void setUp() {
        service = new ScepServiceImpl();
        profile = mock(ScepProfile.class);
        ReflectionTestUtils.setField(service, "scepProfile", profile);
    }

    @Test
    void profileWithoutChallengePassword_absentRequestPassword_passes() {
        when(profile.getChallengePassword()).thenReturn(null);

        assertDoesNotThrow(() -> service.validateChallengePassword(null, false));
    }

    @Test
    void profileWithEmptyChallengePassword_absentRequestPassword_passes() {
        when(profile.getChallengePassword()).thenReturn("");

        assertDoesNotThrow(() -> service.validateChallengePassword(null, false));
    }

    @Test
    void matchingRequestPassword_passes() {
        when(profile.getChallengePassword()).thenReturn(PROFILE_PASSWORD);

        assertDoesNotThrow(() -> service.validateChallengePassword(PROFILE_PASSWORD, false));
    }

    @Test
    void mismatchedRequestPassword_rejectedWithBadMessageCheck() {
        when(profile.getChallengePassword()).thenReturn(PROFILE_PASSWORD);

        ScepException thrown = assertThrows(ScepException.class,
                () -> service.validateChallengePassword("wrong", false));
        assertEquals(FailInfo.BAD_MESSAGE_CHECK, thrown.getFailInfo());
    }

    /**
     * The reported crash (issue #1887): a renewal-style PKCSReq without a challengePassword against a
     * profile that has one configured must yield a SCEP rejection, not a {@code NullPointerException}.
     */
    @Test
    void absentRequestPassword_notARenewal_rejectedWithBadMessageCheck() {
        when(profile.getChallengePassword()).thenReturn(PROFILE_PASSWORD);

        ScepException thrown = assertThrows(ScepException.class,
                () -> service.validateChallengePassword(null, false));
        assertEquals(FailInfo.BAD_MESSAGE_CHECK, thrown.getFailInfo());
    }

    @Test
    void absentRequestPassword_authenticatedRenewal_passes() {
        when(profile.getChallengePassword()).thenReturn(PROFILE_PASSWORD);

        assertDoesNotThrow(() -> service.validateChallengePassword(null, true));
    }

    /**
     * The waiver only covers an <em>absent</em> password. A renewal that does supply one must still
     * match, so a wrong shared secret is never silently ignored.
     */
    @Test
    void mismatchedRequestPassword_authenticatedRenewal_stillRejected() {
        when(profile.getChallengePassword()).thenReturn(PROFILE_PASSWORD);

        ScepException thrown = assertThrows(ScepException.class,
                () -> service.validateChallengePassword("wrong", true));
        assertEquals(FailInfo.BAD_MESSAGE_CHECK, thrown.getFailInfo());
    }
}
