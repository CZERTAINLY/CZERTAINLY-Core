package com.czertainly.core.service.cmp.message;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;

public interface CertificateKeyService {

    /**
     *
     * @param cmpProfileName
     * @return
     */
    CzertainlyProvider getProvider (String cmpProfileName);

    /**
     *
     * @param certificate
     * @return
     */
    CzertainlyPrivateKey getPrivateKey(Certificate certificate);

}
