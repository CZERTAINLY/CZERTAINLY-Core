package com.czertainly.core.api.scep;

import com.czertainly.api.exception.ScepException;
import com.czertainly.api.interfaces.core.scep.ScepController;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.service.scep.ScepService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ScepControllerImpl implements ScepController {

    private ScepService scepService;

    @Autowired
    public void setScepService(ScepService scepService) {
        this.scepService = scepService;
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CERTIFICATE, affiliatedResource = Resource.SCEP_PROFILE, operation = Operation.UNKNOWN)
    public ResponseEntity<Object> doGet(@LogResource(name = true, affiliated = true) String scepProfileName, String operation, String message) throws ScepException {
        return scepService.handleGet(scepProfileName, operation, message);
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CERTIFICATE, affiliatedResource = Resource.SCEP_PROFILE, operation = Operation.UNKNOWN)
    public ResponseEntity<Object> doPost(@LogResource(name = true, affiliated = true) String scepProfileName, String operation, byte[] request) throws ScepException {
        return scepService.handlePost(scepProfileName, operation, request);
    }
}
