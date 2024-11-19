package com.czertainly.core.logging.data;

import java.util.UUID;

public record CertificateLogData(
        UUID uuid,
        String dn,
        String issuerDn,
        String serialNumber,
        String fingerprint
) {
}
