package com.czertainly.core.api.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.api.cmp.message.validator.Validator;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cmp.*;

/**
 * Validator dispatches concrete instance of validator, dispatch per
 * field {@link PKIBody#getType()}.
 */
public class BodyValidator implements Validator<PKIMessage, Void> {

    private final ConfigurationContext configuration;

    public BodyValidator(ConfigurationContext configuration) {this.configuration = configuration;}

    @Override
    public Void validate(PKIMessage message) throws CmpException {
        try {
            switch (message.getBody().getType()) {
                case PKIBody.TYPE_INIT_REQ:
                case PKIBody.TYPE_CERT_REQ:
                case PKIBody.TYPE_KEY_UPDATE_REQ:
                    new BodyCertReqResValidator(configuration).validateIn(message); break;// -- TODO [tocecz] predelat na bean-u/injectovat a jenom prevolavat
                case PKIBody.TYPE_CERT_REP:
                case PKIBody.TYPE_INIT_REP:
                case PKIBody.TYPE_KEY_UPDATE_REP:
                    new BodyCertReqResValidator(configuration).validateOut(message); break;// -- TODO [tocecz] predelat na bean-u/injectovat a jenom prevolavat
                case PKIBody.TYPE_REVOCATION_REQ:
                    new BodyRevocationValidator(configuration).validateIn(message); break;// -- TODO [tocecz] predelat na bean-u/injectovat a jenom prevolavat
                case PKIBody.TYPE_REVOCATION_REP:
                    new BodyRevocationValidator(configuration).validateOut(message); break;// -- TODO [tocecz] predelat na bean-u/injectovat a jenom prevolavat
                case PKIBody.TYPE_CERT_CONFIRM:
                    new BodyCertConfValidator(configuration).validate(message); break;// -- TODO [tocecz] predelat na bean-u/injectovat a jenom prevolavat
                case PKIBody.TYPE_CONFIRM:
                    new BodyPkiConfirmValidator(configuration).validate(message); break;// -- TODO [tocecz] predelat na bean-u/injectovat a jenom prevolavat
                case PKIBody.TYPE_ERROR:
                    new BodyErrorMessageValidator(configuration).validate(message); break;
                case PKIBody.TYPE_P10_CERT_REQ:
                case PKIBody.TYPE_POLL_REQ:
                case PKIBody.TYPE_POLL_REP:
                case PKIBody.TYPE_GEN_MSG:
                case PKIBody.TYPE_GEN_REP:
                case PKIBody.TYPE_NESTED:
                    throw new CmpProcessingException(PKIFailureInfo.badDataFormat,
                            "body validator: "+PkiMessageDumper.msgTypeAsString(message.getBody()) + " is not implemented");
                default:
                    throw new CmpProcessingException(PKIFailureInfo.badDataFormat,
                            "body validator: "+PkiMessageDumper.msgTypeAsString(message.getBody()) + " is not supported");
            }
        } catch (CmpProcessingException ex) {
            throw ex;
        } catch (Throwable thr) {
            throw new CmpProcessingException(PKIFailureInfo.systemFailure,
                    "body validator: internal error - " + thr.getLocalizedMessage());
        }
        return null;
    }
}
