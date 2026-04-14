package com.czertainly.core.api.tsp.parser;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.core.service.tsa.messages.TspRequest;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.tsp.TimeStampRequest;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public class TspRequestParser {

    public static TspRequest parse(byte[] body) throws TspRequestParsingException {
        TimeStampRequest bcRequest = getTimeStampRequest(body);

        Extensions requestExtensions = null;
        if (bcRequest.hasExtensions()) {
            requestExtensions = getExtensions(bcRequest);
        }

        var algorithmOid = bcRequest.getMessageImprintAlgOID().getId();
        DigestAlgorithm digestAlgorithm;
        try {
            digestAlgorithm = DigestAlgorithm.findByOid(algorithmOid);
        } catch (ValidationException e) {
            throw new TspRequestParsingException(TspFailureInfo.BAD_ALG,
                    "Unsupported hash algorithm OID: " + algorithmOid, "Unsupported hash algorithm");
        }

        var hashedMessage = bcRequest.getMessageImprintDigest();
        if (hashedMessage.length != digestAlgorithm.getDigestSizeBytes()) {
            throw new TspRequestParsingException(TspFailureInfo.BAD_DATA_FORMAT,
                    "Hash length %d does not match expected %d for %s".formatted(hashedMessage.length, digestAlgorithm.getDigestSizeBytes(), digestAlgorithm.getCode()),
                    "Invalid hash length");
        }

        Optional<String> policy = resolvePolicy(bcRequest);

        Optional<BigInteger> nonce = bcRequest.getNonce() != null ? Optional.of(bcRequest.getNonce()) : Optional.empty();
        boolean certReq = bcRequest.getCertReq();

        return new TspRequest(digestAlgorithm, hashedMessage, policy, nonce, certReq, requestExtensions);
    }

    private static Extensions getExtensions(TimeStampRequest bcRequest) {

        @SuppressWarnings("unchecked")
        List<ASN1ObjectIdentifier> extensionOids = bcRequest.getExtensionOIDs();

        var extensions = new Extension[extensionOids.size()];
        for (int i = 0; i < extensionOids.size(); i++) {
            extensions[i] = bcRequest.getExtension(extensionOids.get(i));
        }
        return new Extensions(extensions);
    }

    @NonNull
    private static TimeStampRequest getTimeStampRequest(byte[] body) throws TspRequestParsingException {
        TimeStampRequest bcRequest;
        try {
            bcRequest = new TimeStampRequest(body);
        } catch (IOException e) {

            throw new TspRequestParsingException(TspFailureInfo.BAD_REQUEST, "Malformed request: " + e.getMessage(),
                    "Malformed request");
        }
        return bcRequest;
    }

    private static Optional<String> resolvePolicy(TimeStampRequest bcRequest) {
        var reqPolicyOid = bcRequest.getReqPolicy();
        return reqPolicyOid != null ? Optional.of(reqPolicyOid.getId()) : Optional.empty();
    }
}
