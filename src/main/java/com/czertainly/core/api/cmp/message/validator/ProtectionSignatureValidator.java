package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.api.cmp.util.BouncyCastleUtil;
import com.czertainly.core.api.cmp.util.CertUtils;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.cmp.*;

import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;


/**
 * <h6>Signature validator</h6>
 * <p>
 * In this case, the sender possesses a signature key pair and simply
 * signs the PKI message.  <code>PKIProtection</code> will contain the signature
 * value and the <code>protectionAlg</code> will be an <code>AlgorithmIdentifier</code> for a
 * digital signature (e.g., md5WithRSAEncryption or dsaWithSha-1).
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3">PKI Message Protection</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.3">Signature</a>
 */
class ProtectionSignatureValidator implements Validator<PKIMessage, Void> {

    /**
     * Signature-base protection implementation assumes:
     *  <ul>
     *      <ol>that incoming message has <code>extraCert</code> field NOT EMPTY.</ol>
     *      <ol>try to find first certificate from <code>extraCert</code></ol>
     *      <ol>try to get public key of first certificate from <code>extraCert</code></ol>
     *      <ol>given public key is used for signature verification</ol>
     *      <ol>subject of signature {@link ProtectedPart} is count from {@link PKIHeader} and {@link PKIBody} values</ol>
     *      <ol>given counted {@link ProtectedPart} is verify against value in {@link PKIMessage#getProtection()}</ol>
     *  </ul>
     *
     * @param message is subject of signature-based validation
     * @return null, if validation is ok
     *
     * @throws CmpException if validation has failed
     */
    @Override
    public Void validate(PKIMessage message) throws CmpException {
        CMPCertificate[] extraCerts = message.getExtraCerts();
        if (extraCerts == null || extraCerts.length == 0 || extraCerts[0] == null) {
            throw new CmpException(PKIFailureInfo.addInfoNotAvailable,
                    ImplFailureInfo.CRYPTOSIG541);
        }

        try {
            List<X509Certificate> extraCertsAsX509 = CertUtils.toX509Certificates(extraCerts);
            PKIHeader header = message.getHeader();
            byte[] protectedBytes = new ProtectedPart(header, message.getBody()).getEncoded(ASN1Encoding.DER);
            byte[] protectionBytes = message.getProtection().getBytes();
            Signature signature = Signature.getInstance(header.getProtectionAlg().getAlgorithm().getId(), BouncyCastleUtil.getBouncyCastleProvider());
            signature.initVerify(extraCertsAsX509.get(0).getPublicKey());
            signature.update(protectedBytes);
            if (!signature.verify(protectionBytes, 0, protectionBytes.length)) {
                throw new CmpException(PKIFailureInfo.wrongIntegrity,
                        ImplFailureInfo.CRYPTOSIG542);
            }
        } catch(CmpException ex) {
            throw ex;
        }  catch (final KeyException | NoSuchAlgorithmException ex) {
            throw new CmpException(PKIFailureInfo.badAlg,
                    ImplFailureInfo.CRYPTOSIG543);
        } catch(Exception ex) {
            throw new CmpException(
                    PKIFailureInfo.notAuthorized,
                    ex.getClass().getSimpleName() + ":" + ex.getLocalizedMessage());
        }
        return null;
    }
}
