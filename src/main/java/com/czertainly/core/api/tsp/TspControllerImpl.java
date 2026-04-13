package com.czertainly.core.api.tsp;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.tsp.TspController;
import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.api.tsp.parser.TspRequestParser;
import com.czertainly.core.service.tsa.TsaService;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.messages.TspResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TspControllerImpl implements TspController {

    private TsaService tsaService;
    private static final Logger LOG = LoggerFactory.getLogger(TspControllerImpl.class);

    @Autowired
    public void setTspService(TsaService tsaService) {
        this.tsaService = tsaService;
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.SIGNING_RECORD, affiliatedResource = Resource.TSP_PROFILE, operation = Operation.SIGN)
    public ResponseEntity<byte[]> timestamp(String tspProfileName, byte[] request) throws TspException {
        byte[] responseBytes;
        try {
            TspRequest parsedRequest = TspRequestParser.parse(request);
            TspResponse response = tsaService.processTspRequestForTspProfile(tspProfileName, parsedRequest);

            responseBytes = TSPResponseBuilder.fromEngineResponse(response);
        } catch (TspException e) {
            responseBytes = TSPResponseBuilder.buildRejection(e.getFailureInfo(), e.getClientMessage());
            LOG.error("TSP error ({}): {}", e.getFailureInfo(), e.getMessage());
            LOG.debug("TSP error ({}): {}", e.getFailureInfo(), e.getMessage(), e);
        } catch (NotFoundException e) {
            responseBytes = TSPResponseBuilder.buildRejection(TspFailureInfo.BAD_REQUEST, e.getMessage());
            LOG.error("TSP error ({}): {}", TspFailureInfo.BAD_REQUEST, e.getMessage());
            LOG.debug("TSP error ({}): {}", TspFailureInfo.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            responseBytes = TSPResponseBuilder.buildRejection(TspFailureInfo.SYSTEM_FAILURE, "An unexpected error occurred during timestamping.");
            LOG.error("TSP error ({}): {}", TspFailureInfo.SYSTEM_FAILURE, e.getMessage());
            LOG.debug("TSP error ({}): {}", TspFailureInfo.SYSTEM_FAILURE, e.getMessage(), e);
        }

        return ResponseEntity.ok(responseBytes);
    }
}
