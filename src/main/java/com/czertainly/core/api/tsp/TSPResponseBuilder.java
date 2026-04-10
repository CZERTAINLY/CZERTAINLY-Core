package com.czertainly.core.api.tsp;

import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.core.service.tsa.messages.TspResponse;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIFreeText;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.cmp.PKIStatusInfo;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.tsp.TimeStampResp;

import java.io.IOException;

public class TSPResponseBuilder {

    public static byte[] buildGranted(byte[] timestampTokenBytes) {
        try {
            var contentInfo = ContentInfo.getInstance(timestampTokenBytes);
            var statusInfo = new PKIStatusInfo(PKIStatus.granted);
            var resp = new TimeStampResp(statusInfo, contentInfo);
            return resp.getEncoded("DER");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to DER-encode granted response", e);
        }
    }

    public static byte[] buildRejection(TspFailureInfo failureInfo, String statusString) {
        var freeText = new PKIFreeText(statusString);
        var failInfo = new PKIFailureInfo(toBcPkiInfoValue(failureInfo));
        var statusInfo = new PKIStatusInfo(PKIStatus.rejection, freeText, failInfo);
        var resp = new TimeStampResp(statusInfo, null);
        try {
            return resp.getEncoded("DER");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to DER-encode rejection response", e);
        }
    }

    public static byte[] fromEngineResponse(TspResponse response) {
        return switch (response) {
            case TspResponse.Granted granted -> buildGranted(granted.timestampBytes());
            case TspResponse.Rejected rejected -> buildRejection(rejected.failureInfo(), rejected.statusString());
        };
    }

    public static int toBcPkiInfoValue(TspFailureInfo failureInfo) {
        int bitPosition = failureInfo.getBitPosition();
        // Convert RFC 3161 bit position to the integer encoding used by DERBitString(int).
        // DERBitString stores bytes little-endian with bit 0 of the bit string being the MSB
        // of byte 0, so RFC 3161 bit position n maps to integer bit (8*(n/8) + 7 - n%8).
        return 1 << (8 * (bitPosition / 8) + 7 - bitPosition % 8);
    }
}
