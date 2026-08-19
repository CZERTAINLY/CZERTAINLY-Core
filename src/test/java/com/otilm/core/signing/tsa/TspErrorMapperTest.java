package com.otilm.core.signing.tsa;

import com.otilm.api.interfaces.core.tsp.error.TspException;
import com.otilm.api.interfaces.core.tsp.error.TspFailureInfo;
import com.otilm.core.signing.engine.error.SigningEngineException;
import com.otilm.core.signing.engine.error.SigningEngineFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TspErrorMapperTest {

    @ParameterizedTest
    @CsvSource({
            "INVALID_INPUT,BAD_REQUEST",
            "MALFORMED_INPUT,BAD_DATA_FORMAT",
            "MISCONFIGURED,SYSTEM_FAILURE",
            "CONNECTOR_FAULT,SYSTEM_FAILURE",
            "SIGNER_FAULT,SYSTEM_FAILURE",
            "STEP_FAILED,SYSTEM_FAILURE"})
    void mapsEveryFailureToATspFailureInfo(SigningEngineFailure failure, TspFailureInfo expected) {
        // when / then
        assertThat(TspErrorMapper.toFailureInfo(failure)).isEqualTo(expected);
    }

    @Test
    void preservesTheClientMessageAndChainsTheCause() {
        // given
        SigningEngineException engineException = SigningEngineException
                .stepFailed(SigningEngineFailure.CONNECTOR_FAULT, "formatDtbs",
                        "formatting connector at https://fmt.internal returned 503", new IllegalStateException("503"),
                        "Internal error during DTBS formatting");

        // when
        TspException mapped = TspErrorMapper.toTspException(engineException);

        // then
        assertThat(mapped.getFailureInfo()).isEqualTo(TspFailureInfo.SYSTEM_FAILURE);
        assertThat(mapped.getClientMessage()).isEqualTo("Internal error during DTBS formatting");
        assertThat(mapped.getMessage()).isEqualTo(engineException.operatorMessage());
        assertThat(mapped.getCause()).isSameAs(engineException);
    }
}
