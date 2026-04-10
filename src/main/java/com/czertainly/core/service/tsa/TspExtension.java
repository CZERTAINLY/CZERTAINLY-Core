package com.czertainly.core.service.tsa;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;

/**
 * Resolved extension with parsed OID.
 *
 * @param oid              the extension OID
 * @param criticalAllowed  criticality constraint: {@code true} = only critical, {@code false} = only non-critical
 */
public record TspExtension(
        ASN1ObjectIdentifier oid,
        Boolean criticalAllowed) {
}
