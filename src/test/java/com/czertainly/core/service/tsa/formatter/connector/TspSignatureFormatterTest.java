package com.czertainly.core.service.tsa.formatter.connector;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatDtbsRequestDto;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatResponseRequestDto;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TspSignatureFormatterTest {

    private final TspSignatureFormatter formatter = new TspSignatureFormatter();

    @Test
    void formatDtbs_rejectsMalformedCertificateChain() {
        // given — base64 that decodes to bytes that are not a valid DER certificate
        var garbledCert = Base64.getEncoder().encode("not-a-certificate".getBytes());
        var request = new TimestampingFormatDtbsRequestDto();
        request.setCertificateChain(List.of(garbledCert));

        // when / then — invalid cert bytes must surface as BAD_DATA_FORMAT
        var ex = assertThrows(TspException.class, () -> formatter.formatDtbs(request));
        assertEquals(TspFailureInfo.BAD_DATA_FORMAT, ex.getFailureInfo());
    }

    @Test
    void formatSigningResponse_rejectsMalformedCertificateChain() {
        // given — base64 that decodes to bytes that are not a valid DER certificate
        var garbledCert = Base64.getEncoder().encode("not-a-certificate".getBytes());
        var request = new TimestampingFormatResponseRequestDto();
        request.setCertificateChain(List.of(garbledCert));

        // when / then — invalid cert bytes must surface as BAD_DATA_FORMAT
        var ex = assertThrows(TspException.class, () -> formatter.formatSigningResponse(request));
        assertEquals(TspFailureInfo.BAD_DATA_FORMAT, ex.getFailureInfo());
    }


}
