package com.czertainly.core.service.scep.impl;

import com.czertainly.core.service.scep.ExtendedScepHelperService;
import com.czertainly.core.service.scep.ScepService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScepServiceImpl implements ScepService {

    private ExtendedScepHelperService extendedScepHelperService;

    @Autowired
    public void setExtendedScepHelperService(ExtendedScepHelperService extendedScepHelperService) {
        this.extendedScepHelperService = extendedScepHelperService;
    }

    @Override
    public ResponseEntity<Object> handleGet(String scepProfileName, String operation, String message) {
        return extendedScepHelperService.handleGet(scepProfileName, operation, message);
    }

    @Override
    public ResponseEntity<Object> handlePost(String scepProfileName, String operation, byte[] message) {
        return extendedScepHelperService.handlePost(scepProfileName, operation, message);
    }
}
