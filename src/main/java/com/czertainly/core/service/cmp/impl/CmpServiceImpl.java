package com.czertainly.core.service.cmp.impl;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.api.cmp.message.PkiMessageError;
import com.czertainly.core.api.cmp.message.handler.InitialRequestHandler;
import com.czertainly.core.api.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.api.cmp.message.protection.SingatureBaseProtectionStrategy;
import com.czertainly.core.api.cmp.message.util.CMSSignedHelper;
import com.czertainly.core.api.cmp.mock.MockCaImpl;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.cmp.CmpService;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

@Service
@Transactional
public class CmpServiceImpl implements CmpService {

    private static final Logger LOG = LoggerFactory.getLogger(CmpServiceImpl.class.getName());

    private RaProfileRepository raProfileRepository;
    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) { this.raProfileRepository = raProfileRepository; }

    private CertificateService certificateService;
    @Autowired
    public void setCertificateService(CertificateService certificateService) { this.certificateService = certificateService; }

    private static final String HTTP_HEADER_CONTENT_TYPE="application/pkixcmp";

    @Override
    public ResponseEntity<Object> handlePost(String profileName, byte[] request) {

        boolean verbose = true;//konfiguracni polozka cmp.verbose=true/false

        try { MockCaImpl.init();} catch (Exception e) {
            throw new IllegalStateException("mock of CA cannot start", e);
        }

        final PKIMessage pkiRequest;
        try { pkiRequest = PKIMessage.getInstance(request); }
        catch (IllegalArgumentException e) {
            LOG.error("cmp profile="+profileName+" | request message cannot be parsed", e);
            return build(HttpStatus.BAD_REQUEST, PkiMessageError.unprotectedMessage(
                    PKIFailureInfo.badRequest,
                    ImplFailureInfo.CMPSRVR001));
        }
        ASN1OctetString tid = pkiRequest.getHeader().getTransactionID();
        LOG.info("cmp TID={} profile={} | request message for processing: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiRequest));

        // -- (processing) part
        ConfigurationContext configuration = new ConfigurationContext() {
            @Override
            public PrivateKey getPrivateKeyForSigning() {
                return MockCaImpl.getPrivateKeyForSigning();
            }

            @Override
            public List<X509Certificate> getCertificateChain() {
                return MockCaImpl.getChainOfIssuerCerts();
            }

            @Override
            public String getSignatureAlgorithmName() {
                //new DefaultSignatureAlgorithmIdentifierFinder().find(pkiRequest.getHeader().getProtectionAlg().getAlgorithm());
                //return pkiRequest.getHeader().getProtectionAlg().getAlgorithm();
                //ASN1Primitive x = pkiRequest.getHeader().getProtectionAlg().toASN1Primitive();
                return "SHA256withECDSA";//Header/ProtectionAlg/Algorithm: X9.ecdsa_with_SHA256 (1.2.840.10045.4.3.2)
            }

            @Override
            public GeneralName getRecipient() {
                return pkiRequest.getHeader().getRecipient();
            }

            @Override
            public ProtectionStrategy getProtectionStrategy() {
                return new SingatureBaseProtectionStrategy(this);
            }
        };
        try {
            PKIMessage pkiResponse = null;
            //see https://www.rfc-editor.org/rfc/rfc4210#section-5.1.2
            switch (pkiRequest.getBody().getType()) {//TODO [toce] tady vymyslet (via handler-api/strategy designe pattern), jak to zprocesovat
                case PKIBody.TYPE_INIT_REQ:       // ( 1)       ir,     Initial Request; CertReqMessages
                    pkiResponse = new InitialRequestHandler().handle(pkiRequest, configuration); break;
                case PKIBody.TYPE_CERT_REQ:       // ( 2)       cr,   Certification Req; CertReqMessages
                case PKIBody.TYPE_KEY_UPDATE_REQ: // ( 7)      kur,  Key Update Request; CertReqMessages
                case PKIBody.TYPE_REVOCATION_REQ: // (11)       rr,  Revocation Request; RevReqContent
                case PKIBody.TYPE_CONFIRM:        // (19)  pkiconf,        Confirmation; PKIConfirmContent
                case PKIBody.TYPE_CERT_CONFIRM:   // (24) certConf, Certificate confirm; CertConfirmContent
                    throw new CmpException(PKIFailureInfo.badRequest,
                            ImplFailureInfo.CMPVALR001);
                default:
                    LOG.error("cmp TID={}, profile={} | unknown message type, value: {}", tid, profileName, pkiRequest.getBody().getType());
                    throw new CmpException(PKIFailureInfo.badRequest,
                            ImplFailureInfo.CMPVALR002);
            }
            LOG.info("cmp TID={}, profile={} | successfully processed, response: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiResponse));
            return buildEncoded(HttpStatus.OK, pkiResponse);
        } catch (CmpException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(pkiRequest.getHeader(), e.toPKIBody());
            LOG.error("cmp TID="+tid+", profile="+profileName+" | processing failed, response: "+PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return build(HttpStatus.BAD_REQUEST, pkiResponse);
        } catch (IOException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(
                    pkiRequest.getHeader(),
                    PKIFailureInfo.badDataFormat,
                    ImplFailureInfo.CMPSRVR002);
            LOG.error("cmp TID="+tid+", profile="+profileName+" | parsing failed, response: "+PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return build(HttpStatus.BAD_REQUEST, pkiResponse);
        } catch (Exception e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(pkiRequest.getHeader(), e);
            LOG.error("cmp TID="+tid+", profile="+profileName+" | handling failed, response: "+PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return build(HttpStatus.BAD_REQUEST, pkiResponse);
        }
    }

    private ResponseEntity build(HttpStatus status, PKIMessage pkiMessage) {
        return ResponseEntity
                .status(status)
                .header("Content-Type", HTTP_HEADER_CONTENT_TYPE)
                .body(PkiMessageError.encode(pkiMessage));
    }
    private ResponseEntity buildEncoded(HttpStatus status, PKIMessage pkiMessage) throws IOException {
        return ResponseEntity
                .status(status)
                .header("Content-Type", HTTP_HEADER_CONTENT_TYPE)
                .body(pkiMessage.getEncoded());
    }
}
