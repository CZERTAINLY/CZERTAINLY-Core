package com.czertainly.core.service.tsa.formatter.connector;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.api.model.core.oid.SystemOid;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.CertificateChain;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ExtendedContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.util.Date;
import java.util.Hashtable;

/**
 * BouncyCastle-backed stateless utility for formatting TSP tokens.
 *
 * <p>Each method is self-contained — no shared state between calls.
 * Both {@link #formatDtbs} and {@link #formatSigningResponse} build
 * all necessary BouncyCastle objects from scratch.
 */
public final class TimestampTokenAssembler {

    private TimestampTokenAssembler() {}

    /**
     * Phase 1: Drives BouncyCastle to build TSTInfo + SignedAttributes,
     * capturing the DER-encoded SignedAttributes as the data-to-be-signed.
     *
     * @return DER-encoded SignedAttributes bytes to be signed externally
     */
    public static byte[] formatDtbs(SignatureAlgorithm signatureAlgorithm,
                                    CertificateChain certificateChain,
                                    String policyOid,
                                    BigInteger serialNumber,
                                    Date genTime,
                                    Duration accuracy,
                                    boolean includeTsaName,
                                    boolean includeCMSAlgorithmProtection,
                                    boolean includeSigningTimeAttribute,
                                    boolean qualifiedTimestamp,
                                    TspRequest request) throws TspException {
        try {
            var contentSigner = new CapturingContentSigner(signatureAlgorithm);
            var tokenGen = buildTokenGenerator(signatureAlgorithm, contentSigner, certificateChain, policyOid,
                    accuracy, request.includeSignerCertificate(), includeTsaName,
                    includeCMSAlgorithmProtection, includeSigningTimeAttribute, genTime);

            var qcExtension = qualifiedTimestamp ? buildQcExtensions() : null;
            var tokenExtensions = mergeExtensions(request.requestExtensions(), qcExtension);
            tokenGen.generate(toBcRequest(request), serialNumber, genTime, tokenExtensions);
            return contentSigner.getCapturedBytes();
        } catch (TSPException | IOException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    "Failed to format DTBS for timestamp token", e,
                    "Internal error during DTBS formatting");
        }
    }

    /**
     * Phase 2: Injects the pre-computed signature and assembles the final token.
     *
     * @return fully assembled, verifiable TimeStampToken
     */
    public static TimeStampToken formatSigningResponse(SignatureAlgorithm signatureAlgorithm,
                                                       CertificateChain certificateChain,
                                                       String policyOid,
                                                       BigInteger serialNumber,
                                                       Date genTime,
                                                       Duration accuracy,
                                                       boolean includeTsaName,
                                                       boolean includeCMSAlgorithmProtection,
                                                       boolean includeSigningTimeAttribute,
                                                       boolean qualifiedTimestamp,
                                                       TspRequest request,
                                                       byte[] signature) throws TspException {
        try {
            var contentSigner = new InjectingContentSigner(signatureAlgorithm, signature);
            var tokenGen = buildTokenGenerator(signatureAlgorithm, contentSigner, certificateChain, policyOid,
                    accuracy, request.includeSignerCertificate(), includeTsaName,
                    includeCMSAlgorithmProtection, includeSigningTimeAttribute, genTime);

            var qcExtension = qualifiedTimestamp ? buildQcExtensions() : null;
            var tokenExtensions = mergeExtensions(request.requestExtensions(), qcExtension);
            return tokenGen.generate(toBcRequest(request), serialNumber, genTime, tokenExtensions);
        } catch (TSPException | IOException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    "Failed to assemble timestamp token", e,
                    "Internal error during token assembly");
        }
    }

    private static TimeStampTokenGenerator buildTokenGenerator(SignatureAlgorithm signatureAlgorithm,
                                                               ExtendedContentSigner contentSigner,
                                                               CertificateChain certificateChain,
                                                               String policyOid,
                                                               Duration accuracy,
                                                               boolean includeSignerCertificate,
                                                               boolean includeTsaName,
                                                               boolean includeCMSAlgorithmProtection,
                                                               boolean includeSigningTimeAttribute,
                                                               Date genTime) throws TspException {
        try {
            var digestCalcProvider = new JcaDigestCalculatorProviderBuilder()
                    .setProvider("BC").build();

            var signerInfoGenBuilder = new JcaSignerInfoGeneratorBuilder(digestCalcProvider);

            var attrs = new Hashtable<ASN1ObjectIdentifier, Attribute>();

            if (includeSigningTimeAttribute) {
                attrs.put(CMSAttributes.signingTime, new Attribute(
                        CMSAttributes.signingTime, new DERSet(new Time(genTime))));
            }

            if (includeCMSAlgorithmProtection) {
                var algProtection = new CMSAlgorithmProtection(
                        contentSigner.getDigestAlgorithmIdentifier(),
                        CMSAlgorithmProtection.SIGNATURE,
                        contentSigner.getAlgorithmIdentifier());
                attrs.put(CMSAttributes.cmsAlgorithmProtect, new Attribute(
                        CMSAttributes.cmsAlgorithmProtect, new DERSet(algProtection)));
            }

            if (!attrs.isEmpty()) {
                signerInfoGenBuilder.setSignedAttributeGenerator(
                        new DefaultSignedAttributeTableGenerator(new AttributeTable(attrs)));
            }

            var signerInfoGen = signerInfoGenBuilder.build(contentSigner, certificateChain.signingCertificate());
            var digestCalc = digestCalcProvider.get(signatureAlgorithm.getDigestAlgorithmIdentifier());

            var tsTokenGen = new TimeStampTokenGenerator(signerInfoGen, digestCalc, new ASN1ObjectIdentifier(policyOid));

            if (accuracy != null) {
                var accuracyMs = accuracy.toMillis();
                var seconds = (int) (accuracyMs / 1000);
                var millis = (int) (accuracyMs % 1000);
                if (seconds > 0) tsTokenGen.setAccuracySeconds(seconds);
                if (millis > 0) tsTokenGen.setAccuracyMillis(millis);
            }

            if (includeTsaName) {
                tsTokenGen.setTSA(
                        new GeneralName(
                                new JcaX509CertificateHolder(certificateChain.signingCertificate()).getSubject()
                        )
                );
            }

            if (includeSignerCertificate) {
                tsTokenGen.addCertificates(new JcaCertStore(certificateChain.chain()));
            }

            return tsTokenGen;

        } catch (OperatorCreationException | TSPException | CertificateEncodingException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    "Failed to initialise BouncyCastle timestamp formatter", e,
                    "Internal system error during timestamp setup");
        }
    }

    private static TimeStampRequest toBcRequest(TspRequest request) throws TspException, TSPException, IOException {
        var gen = new TimeStampRequestGenerator();
        gen.setCertReq(request.includeSignerCertificate());
        if (request.policy().isPresent()) {
            try {
                gen.setReqPolicy(new ASN1ObjectIdentifier(request.policy().get()));
            } catch (IllegalArgumentException e) {
                throw new TspException(TspFailureInfo.BAD_REQUEST,
                        "Invalid request policy OID: " + request.policy().get(), e,
                        "Invalid policy OID in timestamp request");
            }
        }

        var digestAlgId = new AlgorithmIdentifier(
                new ASN1ObjectIdentifier(request.hashAlgorithm().getOid()));

        if (request.nonce().isPresent()) {
            return gen.generate(digestAlgId, request.hashedMessage(), request.nonce().get());
        }
        return gen.generate(digestAlgId, request.hashedMessage());
    }

    private static Extensions buildQcExtensions() {
        try {
            // ETSI EN 319 422 Annex B: esi4-qtstStatement1 (OID 0.4.0.19422.1.1) MUST be present in the
            // qcStatements extension of a time-stamp token that is claimed to be a qualified electronic time-stamp.
            var qtstStatement = new QCStatement(new ASN1ObjectIdentifier(SystemOid.QTST_STATEMENT_1.getOid()));
            return new Extensions(
                    new Extension(Extension.qCStatements, false,
                            new DERSequence(qtstStatement).getEncoded()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build QC extensions", e);
        }
    }

    private static Extensions mergeExtensions(Extensions requestExtensions, Extensions serverExtensions) {
        if (requestExtensions == null && serverExtensions == null) return null;
        if (requestExtensions == null) return serverExtensions;
        if (serverExtensions == null) return requestExtensions;

        var gen = new ExtensionsGenerator();
        for (var oid : requestExtensions.getExtensionOIDs()) {
            gen.addExtension(requestExtensions.getExtension(oid));
        }
        // Server extensions take precedence on OID conflict
        for (var oid : serverExtensions.getExtensionOIDs()) {
            if (gen.hasExtension(oid)) gen.replaceExtension(serverExtensions.getExtension(oid));
            else gen.addExtension(serverExtensions.getExtension(oid));
        }
        return gen.generate();
    }
}