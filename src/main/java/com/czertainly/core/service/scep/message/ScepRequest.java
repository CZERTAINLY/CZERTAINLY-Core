package com.czertainly.core.service.scep.message;

import com.czertainly.api.model.core.scep.FailInfo;
import com.czertainly.core.service.scep.exception.ScepException;
import com.czertainly.core.util.ScepCommonHelper;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.cms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;

public class ScepRequest {

    private static final Logger logger = LoggerFactory.getLogger(ScepRequest.class);

    private byte[] message;
    private String digestAlgorithmOid;
    private SignedData signedData;
    private int messageType;
    private String transactionId;
    private String senderNonce;
    private ContentInfo encapsulatedContent;
    private EnvelopedData envelopedData;

    public ScepRequest(byte[] message) throws ScepException {
        this.message = message;
        readMessage();
    }

    public int getMessageType() {
        return messageType;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSenderNonce() {
        return senderNonce;
    }

    public ContentInfo getEncapsulatedContent() {
        return encapsulatedContent;
    }

    public EnvelopedData getEnvelopedData() {
        return envelopedData;
    }

    private void readMessage() throws ScepException {
        try {
            readDigestAlgorithm();
        } catch (CMSException e) {
            String errorMessage = "Exception trying to identify digest algorithm";
            logger.error(errorMessage + ": ", e);
            throw new ScepException(errorMessage, e, FailInfo.BAD_MESSAGE_CHECK);
        }

        try {
            checkContentType();
        } catch (IOException e) {
            String errorMessage = "Exception trying to check content type";
            logger.error(errorMessage + ": ", e);
            throw new ScepException(errorMessage, e, FailInfo.BAD_MESSAGE_CHECK);
        }

        readAttributes();

        try {
            readRequest();
        } catch (IOException e) {
            String errorMessage = "Exception trying to read request content";
            logger.error(errorMessage + ": ", e);
            throw new ScepException(errorMessage, e, FailInfo.BAD_REQUEST);
        }
    }

    private void readDigestAlgorithm() throws CMSException {
        CMSSignedData cmsSignedData = new CMSSignedData(message);
        SignerInformationStore signerInformationStore = cmsSignedData.getSignerInfos();
        Collection<SignerInformation> signerInformation = signerInformationStore.getSigners();
        signerInformation.forEach(signerInformationElement -> {
            digestAlgorithmOid = signerInformationElement.getDigestAlgOID();
            logger.debug("Identified digest algorithm: " + digestAlgorithmOid);
        });
    }

    private void checkContentType() throws IOException, ScepException {
        ContentInfo contentInfo = readContentInfo(new ByteArrayInputStream(message));
        String contentType = contentInfo.getContentType().getId();
        if (!contentType.equals(CMSObjectIdentifiers.signedData.getId())) {
            throw new ScepException("Content type is not signedData", FailInfo.BAD_MESSAGE_CHECK);
        } else {
            signedData = SignedData.getInstance(ASN1Sequence.getInstance(contentInfo.getContent()));
        }
    }

    private void readAttributes() {
        Enumeration<?> signerInfos = signedData.getSignerInfos().getObjects();

        signerInfos.asIterator().forEachRemaining(signerInfoElement -> {
            SignerInfo signerInfo = SignerInfo.getInstance(ASN1Sequence.getInstance(signerInfoElement));
            Enumeration<?> authenticatedAttributes = signerInfo.getAuthenticatedAttributes().getObjects();

            authenticatedAttributes.asIterator().forEachRemaining(attributeElement -> {
                Attribute attribute = Attribute.getInstance(ASN1Sequence.getInstance(attributeElement));
                String attributeOid = attribute.getAttrType().getId();
                logger.debug("SignerInfo Attribute: " + attributeOid);

                // TODO: ifs should be improved
                if (attributeOid.equals(ScepCommonHelper.id_messageType)) {
                    Enumeration<?> attributeValues = attribute.getAttrValues().getObjects();
                    ASN1PrintableString asn1PrintableString = ASN1PrintableString.getInstance(attributeValues.nextElement());
                    // TODO: messageType should be enum according to RFC 8894 (https://www.rfc-editor.org/rfc/rfc8894.html#name-messagetype)
                    messageType = Integer.parseInt(asn1PrintableString.getString());
                }

                if (attributeOid.equals(ScepCommonHelper.id_transId)) {
                    Enumeration<?> attributeValues = attribute.getAttrValues().getObjects();
                    ASN1PrintableString asn1PrintableString = ASN1PrintableString.getInstance(attributeValues.nextElement());
                    transactionId = asn1PrintableString.getString();
                }

                if (attributeOid.equals(ScepCommonHelper.id_senderNonce)) {
                    Enumeration<?> attributeValues = attribute.getAttrValues().getObjects();
                    ASN1OctetString asn1OctetString = ASN1OctetString.getInstance(attributeValues.nextElement());
                    senderNonce = Base64.getEncoder().encodeToString(asn1OctetString.getOctets());
                }

            });
        });
    }

    private void readRequest() throws ScepException, IOException {
        if (! (messageType == ScepCommonHelper.SCEP_TYPE_PKCSREQ) ) {
            throw new ScepException("Wrong type of message: " + messageType, FailInfo.BAD_REQUEST);
        }

        ContentInfo encapsulatedContentInfo = signedData.getEncapContentInfo();
        String contentTypeOid = encapsulatedContentInfo.getContentType().getId();

        if (!contentTypeOid.equals(CMSObjectIdentifiers.data.getId())) {
            throw new ScepException("EncapsulatedContentInfo does not contain data content type", FailInfo.BAD_REQUEST);
        } else {
            final ASN1OctetString asn1EncapsulatedContent = ASN1OctetString.getInstance(encapsulatedContentInfo.getContent());
            encapsulatedContent = readContentInfo(new ByteArrayInputStream(asn1EncapsulatedContent.getOctets()));

            String encapsulatedContentInfoOid = encapsulatedContent.getContentType().getId();

            if (!encapsulatedContentInfoOid.equals(CMSObjectIdentifiers.envelopedData.getId())) {
                throw new ScepException("EncapsulatedContentInfo does not contain PKCS7 envelopedData", FailInfo.BAD_REQUEST);
            } else {
                envelopedData = EnvelopedData.getInstance(ASN1Sequence.getInstance(encapsulatedContent.getContent()));
            }
        }
    }

    private ContentInfo readContentInfo(InputStream inputStream) throws IOException {
        final ASN1InputStream asn1InputStream = new ASN1InputStream(inputStream);
        ASN1Sequence asn1Sequence;
        try {
            asn1Sequence = ASN1Sequence.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        return ContentInfo.getInstance(asn1Sequence);
    }

}
