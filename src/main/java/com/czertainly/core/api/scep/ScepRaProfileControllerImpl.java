package com.czertainly.core.api.scep;

import com.czertainly.api.exception.ScepException;
import com.czertainly.api.interfaces.core.scep.ScepRaProfileController;
import com.czertainly.core.service.scep.ScepService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScepRaProfileControllerImpl implements ScepRaProfileController {

    private ScepService scepService;

    @Autowired
    public void setScepService(ScepService scepService) {
        this.scepService = scepService;
    }

    @Override
    public ResponseEntity<Object> doGet(String raProfileName, String operation, String message) throws ScepException {
        return scepService.handleGet(raProfileName, operation, message);
    }

    @Override
    public ResponseEntity<Object> doPost(String raProfileName, String operation, byte[] request) throws ScepException {
        return scepService.handlePost(raProfileName, operation, request);
    }
}
