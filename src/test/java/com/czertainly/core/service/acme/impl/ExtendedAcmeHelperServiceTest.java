package com.czertainly.core.service.acme.impl;

import com.czertainly.api.model.core.authority.RevocationReason;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class ExtendedAcmeHelperServiceTest {

    @Test
    public void testRevokeCertificate_wrongReason() {
        int code = 123;
        final RevocationReason reason = RevocationReason.fromCode(code);

        Assertions.assertNull(reason);
        final String details = "Allowed revocation reason codes: " + Arrays.toString(Arrays.stream(RevocationReason.values()).map(RevocationReason::getCode).toArray());

        Assertions.assertTrue(details.contains("[0"));
    }

}
