package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.util.BouncyCastleUtil;
import com.czertainly.core.api.cmp.message.util.CertUtils;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.cmp.*;

import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;


/*
 * 5.1.3.3.  Signature
 *
 * In this case, the sender possesses a signature key pair and simply
 * signs the PKI message.  PKIProtection will contain the signature
 * value and the protectionAlg will be an AlgorithmIdentifier for a
 * digital signature (e.g., md5WithRSAEncryption or dsaWithSha-1).
 *
 * @see https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.3
 */
public class ProtectionSignatureBasedValidator implements Validator<PKIMessage, Void> {

    @Override
    public Void validate(PKIMessage message) throws CmpException {
        CMPCertificate[] extraCerts = message.getExtraCerts();
        if (extraCerts == null || extraCerts.length == 0 || extraCerts[0] == null) {
            throw new CmpException(PKIFailureInfo.addInfoNotAvailable,
                    ImplFailureInfo.CRYPTOSIG521);
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
                        ImplFailureInfo.CRYPTOSIG522);
            }
        } catch(CmpException ex) {
            throw ex;
        }  catch (final KeyException | NoSuchAlgorithmException ex) {
            throw new CmpException(PKIFailureInfo.badAlg,
                    ImplFailureInfo.CRYPTOSIG523);
        } catch(Exception ex) {
            throw new CmpException(
                    PKIFailureInfo.notAuthorized,
                    ex.getClass().getSimpleName() + ":" + ex.getLocalizedMessage());
        }
        return null;
    }
}
