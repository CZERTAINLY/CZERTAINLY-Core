package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PollFeature {

    private static final Logger LOG = LoggerFactory.getLogger(PollFeature.class.getName());

    @PersistenceContext
    private EntityManager entityManager;

    private CertificateService certificateService;
    @Autowired
    public void setCertificateService(CertificateService certificateService) { this.certificateService = certificateService; }

    /**
     * Convert asynchronous behaviour (manipulation with certificate, e.g. issuing/revoking) to synchronous
     * (cmp client ask for certificate) using polling certificate until certificate.
     *
     * @param tid processing transaction id, see {@link PKIHeader#getTransactionID()}
     * @param serialNumber of given certificate subject of polling
     * @return null if certificate (with state={@link CertificateState#ISSUED}) not found
     * @throws CmpProcessingException if polling of certificate failed
     */
    public Certificate pollCertificate(ASN1OctetString tid, String serialNumber, String uuid, CertificateState expectedState)
            throws CmpProcessingException {
        LOG.debug(">>>>> CERT POLL (begin) >>>>> ");
        SecuredUUID certUUID = SecuredUUID.fromString(uuid);

        Certificate polledCert;
        try{
            LOG.trace("TID={}, SN={} | Polling of certificate with uuid={}", tid, serialNumber, certUUID);
            long startRequest = System.currentTimeMillis();
            long endRequest;
            int timeout = 1000*10;//in millis, TODO vytahnout do konfigurace asi jenom nasobitel(zde *10), tzn. v sekundach!
            int counter = 0;//counter for logging purpose only
            do {
                LOG.debug(">>>>> TID={}, POLL=[{}] SN={} | polling request: certificate with uuid={}", tid, counter, serialNumber, certUUID);
                // -- (2)certification polling (ask for created certificate entity)
                polledCert = certificateService.getCertificateEntity(certUUID);
                LOG.debug("<<<<< TID={}, POLL=[{}] SN={} | polling result: certificate entity in state {}, uuid={}", tid, counter, serialNumber, polledCert.getState(), certUUID);
                endRequest = System.currentTimeMillis();
                counter++;
                if(polledCert != null) entityManager.refresh(polledCert);//get entity from db (instead from hibernate 1lvl cache)
                if(counter > 1) TimeUnit.MILLISECONDS.sleep(1000);
            } while ( endRequest - startRequest < timeout
                    && (polledCert != null && !expectedState.equals(polledCert.getState())));
            LOG.trace("TID={}, SN={} | Polling of certificate with uuid={} is done", tid, serialNumber, certUUID);
        } catch(InterruptedException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "SN="+serialNumber+" | cannot poll certificate - processing thread has been interrupted", e);
        } catch (NotFoundException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "SN="+serialNumber+" | issued certificate from CA cannot be found, uuid="+certUUID);
        } finally {
            LOG.debug("<<<<< CERT polling (  end) <<<<< ");
        }

        if(polledCert == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "SN="+serialNumber+" | result of polling cannot be null or empty result");
        }
        if (!expectedState.equals(polledCert.getState())) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    String.format("SN=%s | polled certificate is not at valid state (expected=%s), retrieved=%s",
                            serialNumber,
                            expectedState,
                            polledCert.getState()));
        }
        return polledCert;
    }

}
