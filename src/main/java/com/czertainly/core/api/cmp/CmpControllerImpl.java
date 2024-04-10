package com.czertainly.core.api.cmp;

import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.CmpService;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO [toce] add CmpException into Czertainly-interfaces,
 *      next to rest-api as throws definition
 *      next add into doGet (is not allow at all)
 */
@RestController
public class CmpControllerImpl implements CmpController { // TODO[toce] swagger api

    private static final Logger LOG = LoggerFactory.getLogger(CmpControllerImpl.class.getName());

    private CmpService cmpService;
    @Autowired
    public void setCmpService(CmpService cmpService) {
        this.cmpService = cmpService;
    }

    @Override
    public ResponseEntity<Object> doGet(String cmpProfileName, byte[] request) throws CmpRuntimeException  {
        throw new CmpRuntimeException(PKIFailureInfo.badRequest, ImplFailureInfo.CMPCNTR001);
    }

    @Override
    public ResponseEntity<Object> doPost(String cmpProfileName, byte[] request) /*throws CmpException*/ {
        return cmpService.handlePost(cmpProfileName, request);//"application/pkixcmp"
    }
}
