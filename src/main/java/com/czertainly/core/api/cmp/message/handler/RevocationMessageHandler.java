package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.core.acme.CertificateRevocationRequest;
import com.czertainly.api.model.core.acme.Problem;
import com.czertainly.api.model.core.authority.CertificateRevocationReason;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.scep.FailInfo;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.api.cmp.mock.MockCaImpl;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.service.acme.AcmeConstants;
import com.czertainly.core.service.acme.message.AcmeJwsRequest;
import com.czertainly.core.service.scep.message.ScepRequest;
import com.czertainly.core.util.AcmeJsonProcessor;
import com.czertainly.core.util.AcmePublicKeyProcessor;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * <p>5.3.9.  Revocation Request Content</p>
 * <p>
 *    When requesting revocation of a certificate (or several
 *    certificates), the following data structure is used.  The name of the
 *    requester is present in the PKIHeader structure.</p>
 *
 * <pre>
 *     RevReqContent ::= SEQUENCE OF RevDetails
 *
 *     RevDetails ::= SEQUENCE {
 *         certDetails         CertTemplate,
 *         crlEntryDetails     Extensions       OPTIONAL
 *     }
 * </pre>
 *
 * <p>5.3.10.  Revocation Response Content</p>
 * <p>
 *    The revocation response is the response to the above message.  If
 *    produced, this is sent to the requester of the revocation.  (A
 *    separate revocation announcement message MAY be sent to the subject
 *    of the certificate for which revocation was requested.)</p>
 * <pre>
 *      RevRepContent ::= SEQUENCE {
 *          status        SEQUENCE SIZE (1..MAX) OF PKIStatusInfo,
 *          revCerts  [0] SEQUENCE SIZE (1..MAX) OF CertId OPTIONAL,
 *          crls      [1] SEQUENCE SIZE (1..MAX) OF CertificateList
 *                        OPTIONAL
 *      }
 * </pre>
 */
public class RevocationMessageHandler implements MessageHandler {
    @Override
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws Exception {
        if(PKIBody.TYPE_REVOCATION_REQ!=request.getBody().getType()) {
            throw new CmpException(
                    PKIFailureInfo.systemFailure,
                    "revocation (rr) message cannot be handled - unsupported body rawType="+request.getBody().getType()+", type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()) +"; only type=cerfConf is supported");
        }
        RevReqContent revBody = (RevReqContent) request.getBody().getContent();
        RevDetails[] revocations = revBody.toRevDetailsArray();

        PKIMessage response = MockCaImpl
                .handleRevocationRequest(request, configuration);

        if(response != null) { return response; }
        throw new CmpException(
                PKIFailureInfo.systemFailure,
                "general problem while handling message, type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()));
    }

    // --
    // -- ACME, how to revoke certificate, inspiration
    // --
//    public ResponseEntity<?> revokeCertificate(String acmeProfileName, String requestJson, URI requestUri, boolean isRaProfileBased) throws AcmeProblemDocumentException, ConnectorException, CertificateException {
//        if (requestJson.isEmpty()) {
//            logger.error("Update Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
//            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
//        }
//
//        // Parse and check the JWS request
//        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);
//        validateRequest(jwsRequest, acmeProfileName, requestUri, isRaProfileBased);
//
//        CertificateRevocationRequest request = AcmeJsonProcessor.getPayloadAsRequestObject(jwsRequest.getJwsObject(), CertificateRevocationRequest.class);
//        logger.debug("Certificate revocation is triggered with the payload: {}", request.toString());
//
//        String base64UrlCertificate = request.getCertificate();
//        X509Certificate x509Certificate = CertificateUtil.getX509CertificateFromBase64Url(base64UrlCertificate);
//        String base64Certificate = CertificateUtil.getBase64FromX509Certificate(x509Certificate);
//
//        ClientCertificateRevocationDto revokeRequest = new ClientCertificateRevocationDto();
//
//        Certificate cert = certificateService.getCertificateEntityByContent(base64Certificate);
//        if (cert.getState().equals(CertificateState.REVOKED)) {
//            logger.error("Certificate is already revoked. Serial number: {}, Fingerprint: {}", cert.getSerialNumber(), cert.getFingerprint());
//            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ALREADY_REVOKED);
//        }
//
//        if (jwsRequest.isJwkPresent()) {
//            PublicKey certPublicKey = x509Certificate.getPublicKey();
//            PublicKey jwsPublicKey = jwsRequest.getPublicKey();
//
//            String pemPubKeyCert = AcmePublicKeyProcessor.publicKeyPemStringFromObject(certPublicKey);
//            String pemPubKeyJws = AcmePublicKeyProcessor.publicKeyPemStringFromObject(jwsPublicKey);
//            if (!pemPubKeyCert.equals(pemPubKeyJws)) { // check that the public key of the certificate matches the public key of the JWS
//                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
//            }
//        }
//
//        // if the revocation reason is null, set it to UNSPECIFIED, otherwise get the code from the request
//        final CertificateRevocationReason reason = request.getReason() == null ? CertificateRevocationReason.UNSPECIFIED : CertificateRevocationReason.fromReasonCode(request.getReason());
//        // when the reason is null, it means, that is not in the list
//        if (reason == null) {
//            final String details = "Allowed revocation reason codes are: " + Arrays.toString(Arrays.stream(CertificateRevocationReason.values()).map(CertificateRevocationReason::getCode).toArray());
//            throw new AcmeProblemDocumentException(HttpStatus.FORBIDDEN, Problem.BAD_REVOCATION_REASON, details);
//        }
//
//        revokeRequest.setReason(reason);
//        // TODO: acme account should be identified from certificate, now empty revocation attributes are always used
//        revokeRequest.setAttributes(getClientOperationAttributes(true, null, isRaProfileBased));
//
//        try {
//            clientOperationService.revokeCertificate(SecuredParentUUID.fromUUID(cert.getRaProfile().getAuthorityInstanceReferenceUuid()), cert.getRaProfile().getSecuredUuid(), cert.getUuid().toString(), revokeRequest);
//            return ResponseEntity
//                    .ok()
//                    .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
//                    .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName, isRaProfileBased))
//                    .build();
//        } catch (NotFoundException | AttributeException e) {
//            return ResponseEntity
//                    .badRequest()
//                    .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
//                    .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName, isRaProfileBased))
//                    .build();
//        }
//    }


        // --
        // -- SCEP inspirace pro renewal
        //
//    private void renewalValidation(ScepRequest scepRequest) throws ScepException {
//        JcaPKCS10CertificationRequest pkcs10Request = scepRequest.getPkcs10Request();
//        Certificate extCertificate;
//        try {
//            extCertificate = certificateService.getCertificateEntityByFingerprint(CertificateUtil.getThumbprint(scepRequest.getSignerCertificate()));
//        } catch (NotFoundException e) {
//            // Certificate is not found with the fingerprint. Meaning its not a renewal request. So do nothing
//            return;
//        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
//            throw new ScepException("Unable to parse the signer certificate");
//        }
//        if (!(new X500Name(extCertificate.getSubjectDn())).equals(pkcs10Request.getSubject())) {
//            throw new ScepException("Subject DN for the renewal request does not match the original certificate");
//        }
//        try {
//            if (!scepRequest.verifySignature(scepRequest.getSignerCertificate().getPublicKey())) {
//                throw new ScepException("SCEP Request signature verification failed");
//            }
//        } catch (OperatorCreationException | CMSException e) {
//            throw new ScepException("Exception when verifying signature." + e.getMessage());
//        }
//        // No need to verify the same key pair used in request since it is already handled by the rekey method in client operations
//        checkRenewalTimeframe(extCertificate);
//    }
//
//    private void checkRenewalTimeframe(Certificate certificate) throws ScepException {
//        // Empty renewal threshold or the value 0 will be considered as null value and the half life of the certificate will be assumed
//        if (scepProfile.getRenewalThreshold() == null || scepProfile.getRenewalThreshold() == 0) {
//            // If the renewal timeframe is not given, we consider that renewal is possible only after the certificate
//            // crosses its half lime time
//            if (certificate.getValidity() / 2 < certificate.getExpiryInDays()) {
//                throw new ScepException("Cannot renew certificate. Validity exceeds the half life time of certificate", FailInfo.BAD_REQUEST);
//            }
//        } else if (certificate.getValidationStatus().equals(CertificateValidationStatus.EXPIRED) || certificate.getState().equals(CertificateState.REVOKED)) {
//            throw new ScepException("Cannot renew certificate. Certificate is already in expired or revoked state", FailInfo.BAD_REQUEST);
//        } else {
//            if (certificate.getExpiryInDays() > scepProfile.getRenewalThreshold()) {
//                throw new ScepException("Cannot renew certificate. Validity exceeds the configured value in SCEP profile", FailInfo.BAD_REQUEST);
//            }
//        }
//    }
}
