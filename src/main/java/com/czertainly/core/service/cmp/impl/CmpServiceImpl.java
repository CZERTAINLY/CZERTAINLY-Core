package com.czertainly.core.service.cmp.impl;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.api.cmp.message.builder.PkiMessageError;
import com.czertainly.core.api.cmp.message.handler.CertConfirmMessageHandler;
import com.czertainly.core.api.cmp.message.handler.CrmfMessageHandler;
import com.czertainly.core.api.cmp.message.handler.RevocationMessageHandler;
import com.czertainly.core.api.cmp.message.validator.impl.BodyValidator;
import com.czertainly.core.api.cmp.message.validator.impl.HeaderValidator;
import com.czertainly.core.api.cmp.message.validator.impl.ProtectionPBMac1Validator;
import com.czertainly.core.api.cmp.message.validator.impl.ProtectionValidator;
import com.czertainly.core.api.cmp.mock.MockCaImpl;
import com.czertainly.core.api.cmp.profiles.Mobile3gppProfileContext;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
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
        try { MockCaImpl.init();} catch (Exception e) {
            throw new IllegalStateException("mock of CA cannot start", e);
        }

        final PKIMessage pkiRequest;
        try { pkiRequest = PKIMessage.getInstance(request); }
        catch (IllegalArgumentException e) {
            LOG.error("profile={} | request message cannot be parsed", profileName, e);
            return build(PkiMessageError.unprotectedMessage(
                    PKIFailureInfo.badRequest,
                    ImplFailureInfo.CMPSRVR001));
        }
        ASN1OctetString tid = pkiRequest.getHeader().getTransactionID();
        String typeAsName = PkiMessageDumper.msgTypeAsString(pkiRequest.getBody());
        LOG.info("({}) TID={} profile={} | request message for processing: {}", typeAsName, tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiRequest));
        //LOG.info("({}) TID={} profile={} | {}", typeAsName, Base64.getEncoder().encodeToString(request));

        // -- (processing) part
        CmpProfile profile = new CmpProfile();//najdi v databasi
        profile.setName("toceTest");
        ConfigurationContext config3gppProfile = new Mobile3gppProfileContext(profile, pkiRequest);
        try {
            PKIMessage pkiResponse;

            new HeaderValidator(config3gppProfile).validate(pkiRequest);
            new BodyValidator(config3gppProfile).validate(pkiRequest);
            new ProtectionValidator(config3gppProfile)
                    .validate(pkiRequest);

            //see https://www.rfc-editor.org/rfc/rfc4210#section-5.1.2, PKI Message Body
            switch (pkiRequest.getBody().getType()) {
                case PKIBody.TYPE_INIT_REQ:       // ( 1)       ir,     Initial Request; CertReqMessages
                case PKIBody.TYPE_CERT_REQ:       // ( 2)       cr,   Certification Req; CertReqMessages
                case PKIBody.TYPE_KEY_UPDATE_REQ: // ( 7)      kur,  Key Update Request; CertReqMessages
                    pkiResponse = new CrmfMessageHandler().handle(pkiRequest, config3gppProfile); break;
                case PKIBody.TYPE_REVOCATION_REQ: // (11)       rr,  Revocation Request; RevReqContent
                    pkiResponse = new RevocationMessageHandler().handle(pkiRequest, config3gppProfile); break;
                case PKIBody.TYPE_CERT_CONFIRM:   // (24) certConf, Certificate confirm; CertConfirmContent
                    pkiResponse = new CertConfirmMessageHandler().handle(pkiRequest, config3gppProfile); break;
                default:
                    LOG.error("TID={}, profile={} | unknown message type, value: {}", tid, profileName, pkiRequest.getBody().getType());
                    throw new CmpException(PKIFailureInfo.badRequest,
                            ImplFailureInfo.CMPVALR002);
                    //throw new CmpException(PKIFailureInfo.badRequest, ImplFailureInfo.CMPVALR001);
            }

            if(pkiResponse == null) {
                throw new CmpException(
                        PKIFailureInfo.systemFailure,
                        "general problem while handling PKIMessage, type=" + PkiMessageDumper.msgTypeAsString(pkiRequest.getBody().getType()));
            }
            LOG.info("TID={}, profile={} | response processed: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiResponse));

            new HeaderValidator(config3gppProfile).validate(pkiResponse);
            new BodyValidator(config3gppProfile).validate(pkiResponse);
            new ProtectionValidator(config3gppProfile)
                    .validate(pkiResponse);

            return buildEncoded(pkiResponse);
        } catch (CmpException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(pkiRequest.getHeader(), e.toPKIBody());
            LOG.error("TID={}, profile={} | processing failed, response: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return build(pkiResponse);
        } catch (IOException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(
                    pkiRequest.getHeader(),
                    PKIFailureInfo.badDataFormat,
                    ImplFailureInfo.CMPSRVR002);
            LOG.error("cmp TID={}, profile={} | parsing failed, response: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return build(pkiResponse);
        } catch (Exception e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(pkiRequest.getHeader(), e);
            LOG.error("cmp TID={}, profile={} | handling failed, response: {}", tid, profileName, PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return build(pkiResponse);
        }
    }

    private ResponseEntity build(PKIMessage pkiMessage) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", HTTP_HEADER_CONTENT_TYPE)
                .body(PkiMessageError.encode(pkiMessage));
    }
    private ResponseEntity buildEncoded(PKIMessage pkiMessage) throws IOException {
        return ResponseEntity
                .status(HttpStatus.OK)
                .header("Content-Type", HTTP_HEADER_CONTENT_TYPE)
                .body(pkiMessage.getEncoded());
    }
}
