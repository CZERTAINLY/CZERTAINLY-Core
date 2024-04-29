package com.czertainly.core.api.cmp;

import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.service.cmp.CmpService;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO [toce] add CmpException into Czertainly-interfaces,
 *      next to rest-api as throws definition
 *      next add into doGet (is not allow at all)
 */
@RestController
public class CmpControllerImpl implements CmpController {

    private CmpService cmpService;
    @Autowired
    public void setCmpService(CmpService cmpService) {
        this.cmpService = cmpService;
    }

    @Override
    public ResponseEntity<Object> doGet(String cmpProfileName, byte[] request) throws CmpBaseException {
        throw new CmpProcessingException(PKIFailureInfo.badRequest, ImplFailureInfo.CMPCNTR001);
    }

    @Override
    public ResponseEntity<Object> doPost(String cmpProfileName, byte[] request) throws CmpBaseException {
        return cmpService.handlePost(cmpProfileName, request);
    }
}
