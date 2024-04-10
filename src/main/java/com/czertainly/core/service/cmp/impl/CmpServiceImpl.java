package com.czertainly.core.service.cmp.impl;

import com.czertainly.core.api.cmp.CmpRuntimeException;
import com.czertainly.core.api.cmp.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.api.cmp.message.PkiMessageError;
import com.czertainly.core.api.cmp.message.handler.InitialRequestHandler;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.cmp.CmpService;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

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

        boolean verbose = false;//konfiguracni polozka cmp.verbose=true/false

        PKIMessage pkiRequest = null;
        try { pkiRequest = PKIMessage.getInstance(request); }
        catch (IllegalArgumentException e) {
            LOG.error("cmp profile="+profileName+" | request message cannot be parsed", e);
            return build(HttpStatus.BAD_REQUEST, PkiMessageError.unprotectedMessage(
                    PKIFailureInfo.badRequest,
                    ImplFailureInfo.CMPSRVR001));
        }
        ASN1OctetString tid = pkiRequest.getHeader().getTransactionID();
        LOG.info("cmp TID={} profile={} | request message for processing: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiRequest));

        // -- (response) part
        try {
            PKIMessage pkiResponse = null;
            //see https://www.rfc-editor.org/rfc/rfc4210#section-5.1.2
            switch (pkiRequest.getBody().getType()) {//TODO [toce] tady vymyslet (via handler-api/strategy designe pattern), jak to zprocesovat
                case PKIBody.TYPE_INIT_REQ:       // ( 1)       ir,     Initial Request; CertReqMessages
                    pkiResponse = new InitialRequestHandler().handle(pkiRequest); break;
                case PKIBody.TYPE_CERT_REQ:       // ( 2)       cr,   Certification Req; CertReqMessages
                case PKIBody.TYPE_KEY_UPDATE_REQ: // ( 7)      kur,  Key Update Request; CertReqMessages
                case PKIBody.TYPE_REVOCATION_REQ: // (11)       rr,  Revocation Request; RevReqContent
                case PKIBody.TYPE_CONFIRM:        // (19)  pkiconf,        Confirmation; PKIConfirmContent
                case PKIBody.TYPE_CERT_CONFIRM:   // (24) certConf, Certificate confirm; CertConfirmContent
                    throw new CmpRuntimeException(PKIFailureInfo.badRequest,
                            ImplFailureInfo.CMPVALR001);
                default:
                    LOG.error("cmp TID={}, profile={} | unknown message type, value: {}", tid, profileName, pkiRequest.getBody().getType());
                    throw new CmpRuntimeException(PKIFailureInfo.badRequest,
                            ImplFailureInfo.CMPVALR002);
            }
            LOG.info("cmp TID={}, profile={} | successfully processed, response: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiResponse));
            return buildEncoded(HttpStatus.OK, pkiResponse);
        } catch (CmpRuntimeException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(e.toPKIBody());
            LOG.error("cmp TID="+tid+", profile="+profileName+" | processing failed, response: "+PkiMessageDumper.dumpPkiMessage(pkiResponse), verbose ? e : null);
            return build(HttpStatus.BAD_REQUEST, pkiResponse);
        } catch (IOException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(
                    PKIFailureInfo.badDataFormat,
                    ImplFailureInfo.CMPSRVR002);
            LOG.error("cmp TID="+tid+", profile="+profileName+" | parsing failed, response: "+PkiMessageDumper.dumpPkiMessage(pkiResponse), verbose ? e : null);
            return build(HttpStatus.BAD_REQUEST, pkiResponse);
        } catch (Exception e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(
                    PKIFailureInfo.badDataFormat,
                    ImplFailureInfo.CMPSRVR003);
            LOG.error("cmp TID="+tid+", profile="+profileName+" | handling failed, response: "+PkiMessageDumper.dumpPkiMessage(pkiResponse), verbose ? e : null);
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
