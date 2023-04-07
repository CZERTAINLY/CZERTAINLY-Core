package com.czertainly.core.service.scep.message;

import com.czertainly.api.model.core.scep.FailInfo;
import com.czertainly.api.model.core.scep.MessageType;
import com.czertainly.core.service.scep.exception.ScepException;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.Provider;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

public class ScepRequest {

    private static final Logger logger = LoggerFactory.getLogger(ScepRequest.class);

    private final byte[] message;
    private String digestAlgorithmOid;
    private SignedData signedData;
    private byte[] requestKeyInfo;
    private MessageType messageType;
    private String transactionId;
    private String senderNonce;
    private ContentInfo encapsulatedContent;
    private EnvelopedData envelopedData;
    private JcaPKCS10CertificationRequest pkcs10Request;

    public ScepRequest(byte[] message) throws ScepException {
        this.message = message;
        readMessage();
    }

    public String getDigestAlgorithmOid() {
        return digestAlgorithmOid;
    }

    public byte[] getRequestKeyInfo() {
        return requestKeyInfo;
    }

    public MessageType getMessageType() {
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
            readRequestKeyInfo();
        } catch (IOException e) {
            String errorMessage = "Exception trying to read request key info (certificate)";
            logger.error(errorMessage + ": ", e);
            throw new ScepException(errorMessage, e, FailInfo.BAD_REQUEST);
        }

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
                if (attributeOid.equals(ScepConstants.id_messageType)) {
                    Enumeration<?> attributeValues = attribute.getAttrValues().getObjects();
                    ASN1PrintableString asn1PrintableString = ASN1PrintableString.getInstance(attributeValues.nextElement());
                    // TODO: messageType should be enum according to RFC 8894 (https://www.rfc-editor.org/rfc/rfc8894.html#name-messagetype)
                    messageType = MessageType.resolve(Integer.parseInt(asn1PrintableString.getString()));
                }

                if (attributeOid.equals(ScepConstants.id_transactionId)) {
                    Enumeration<?> attributeValues = attribute.getAttrValues().getObjects();
                    ASN1PrintableString asn1PrintableString = ASN1PrintableString.getInstance(attributeValues.nextElement());
                    transactionId = asn1PrintableString.getString();
                }

                if (attributeOid.equals(ScepConstants.id_senderNonce)) {
                    Enumeration<?> attributeValues = attribute.getAttrValues().getObjects();
                    ASN1OctetString asn1OctetString = ASN1OctetString.getInstance(attributeValues.nextElement());
                    senderNonce = Base64.getEncoder().encodeToString(asn1OctetString.getOctets());
                }

            });
        });
    }

    private void readRequestKeyInfo() throws IOException {
        ASN1Set certificates = signedData.getCertificates();
        if (certificates.size() > 0) { // there should be one certificate
            ASN1Encodable asn1Certificate = certificates.getObjectAt(0);
            if (asn1Certificate != null) {
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                final ASN1OutputStream asn1OutputStream = ASN1OutputStream.create(byteArrayOutputStream, ASN1Encoding.DER);
                asn1OutputStream.writeObject(asn1Certificate);
                if (byteArrayOutputStream.size() > 0) {
                    requestKeyInfo = byteArrayOutputStream.toByteArray();
                }
            }
        }
    }

    private void readRequest() throws ScepException, IOException {
        if (! (messageType.equals(MessageType.PKCS_REQ)) ) {
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

    private void decryptData(PrivateKey privateKey, Provider provider) throws IOException, CMSException {
        // TODO: privateKey and provider cannot be null

        CMSEnvelopedData cmsEnvelopedData = new CMSEnvelopedData(encapsulatedContent.getEncoded());
        RecipientInformationStore recipientInfos = cmsEnvelopedData.getRecipientInfos();
        Collection<RecipientInformation> recipients = recipientInfos.getRecipients();
        Iterator<RecipientInformation> recipientInformationIterator = recipients.iterator();
        byte[] decryptedData = null;

        if (recipientInformationIterator.hasNext()) {
            RecipientInformation recipient = recipientInformationIterator.next();
            JceKeyTransEnvelopedRecipient jceKeyTransEnvelopedRecipient = new JceKeyTransEnvelopedRecipient(privateKey);
            jceKeyTransEnvelopedRecipient.setProvider(provider);
            jceKeyTransEnvelopedRecipient.setContentProvider(BouncyCastleProvider.PROVIDER_NAME);
            jceKeyTransEnvelopedRecipient.setMustProduceEncodableUnwrappedKey(true);
            decryptedData = recipient.getContent(jceKeyTransEnvelopedRecipient);
        }

        assert decryptedData != null;
        pkcs10Request = new JcaPKCS10CertificationRequest(decryptedData);
    }

}
