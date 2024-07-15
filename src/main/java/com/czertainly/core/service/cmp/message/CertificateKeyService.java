package com.czertainly.core.service.cmp.message;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;

public interface CertificateKeyService {

    /**
     * @param cmpProfileName name of CMP profile
     * @return provider for given CMP profile
     */
    CzertainlyProvider getProvider(String cmpProfileName);

    /**
     * @param certificate certificate
     * @return private key for given certificate
     */
    CzertainlyPrivateKey getPrivateKey(Certificate certificate);

}
