package com.czertainly.core.api.cmp;

import com.czertainly.api.interfaces.core.cmp.CmpRaProfileController;
import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.api.interfaces.core.cmp.error.ImplFailureInfo;
import com.czertainly.core.service.cmp.CmpService;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CmpRaProfileControllerImpl implements CmpRaProfileController {

    private CmpService cmpService;
    @Autowired
    public void setCmpService(CmpService cmpService) {
        this.cmpService = cmpService;
    }

    /**
     * Handling of GET is not allowed - http 500 is returned
     *
     * @param raProfileName name of the RA profile
     * @param request binary {@link org.bouncycastle.asn1.cmp.PKIMessage}
     * @return http status 500 because of http get is not allowed
     * @throws CmpBaseException - http get is not allowed
     */
    @Override
    public ResponseEntity<byte[]> doGet(String raProfileName, byte[] request) throws CmpBaseException {
        throw new CmpProcessingException(PKIFailureInfo.badRequest, ImplFailureInfo.CMPCNTR001);
    }

    /**
     * Handling POST pki request/response flow
     *
     * @param raProfileName name of the RA profile
     * @param request binary {@link org.bouncycastle.asn1.cmp.PKIMessage}
     * @return response for given <code>request</code>
     * @throws CmpBaseException if any error has been raised
     */
    @Override
    public ResponseEntity<byte[]> doPost(String raProfileName, byte[] request) throws CmpBaseException {
        return cmpService.handlePost(raProfileName, request);
    }
}
