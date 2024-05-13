package com.czertainly.core.service.cmp;

import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import org.springframework.http.ResponseEntity;

public interface CmpService {

    ResponseEntity<Object> handlePost(String cmpProfileName, byte[] request) throws CmpBaseException;

}
