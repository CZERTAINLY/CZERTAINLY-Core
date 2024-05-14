package com.czertainly.core.service.cmp.configurations;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.api.interfaces.core.cmp.error.CmpConfigurationException;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.x509.GeneralName;

import java.util.List;

public interface ConfigurationContext {

    CmpProfile getProfile();

    GeneralName getRecipient();

    ASN1OctetString getSenderKID();

    /**
     * scope: protection (mac-based)
     * @return shared secret for mac-base protection
     */
    byte[] getSharedSecret();

    /**
     * It allows to client define specified validation of CRMF request messages
     * @param request of crmf based message
     */
    void validateOnCrmfRequest(PKIMessage request)
            throws CmpProcessingException;

    /**
     * It allows to client define specified validation of response messages
     * @param response of message
     */
    void validateOnCrmfResponse(PKIMessage response)
            throws CmpProcessingException;

    /**
     * @return get protection method (how REQUEST message has to be protected)
     */
    ProtectionMethod getProtectionMethod() throws CmpConfigurationException;

    /**
     * @return get protection strategy (how RESPONSE message has to be protected)
     */
    ProtectionStrategy getProtectionStrategy() throws CmpBaseException;

    List<RequestAttributeDto> getClientOperationAttributes(boolean isRevoke);

    //if I want to scan extra cert field
    boolean dumpSinging();
}
