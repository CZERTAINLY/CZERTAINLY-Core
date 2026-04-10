package com.czertainly.core.service.tsa.certificatevalidation;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.qualified.QCStatement;

import java.security.cert.X509Certificate;

final class QualifiedCertificateValidator {

    private static final ASN1ObjectIdentifier QC_STATEMENTS_OID =
            Extension.qCStatements;

    private static final ASN1ObjectIdentifier QC_COMPLIANCE_OID =
            new ASN1ObjectIdentifier("0.4.0.19422.1.1");


    CertificateValidationResult validate(X509Certificate cert) {
        var qcStatementsBytes = cert.getExtensionValue(QC_STATEMENTS_OID.getId());
        if (qcStatementsBytes == null) {
            return CertificateValidationResult.invalid(
                    "Signer certificate does not contain QCStatements extension", cert);
        }

        var octetString = org.bouncycastle.asn1.ASN1OctetString.getInstance(qcStatementsBytes);
        var sequence = ASN1Sequence.getInstance(octetString.getOctets());

        for (var element : sequence) {
            var statement = QCStatement.getInstance(element);
            if (QC_COMPLIANCE_OID.equals(statement.getStatementId())) {
                return CertificateValidationResult.valid();
            }
        }

        return CertificateValidationResult.invalid(
                "Signer certificate does not contain QcCompliance statement (%s)".formatted(QC_COMPLIANCE_OID), cert);
    }
}
