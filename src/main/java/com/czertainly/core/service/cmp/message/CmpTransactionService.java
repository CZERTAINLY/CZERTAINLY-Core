package com.czertainly.core.service.cmp.message;

import com.czertainly.api.model.core.cmp.CmpTransactionState;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.dao.repository.cmp.CmpTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CmpTransactionService {

    private CmpTransactionRepository cmpTransactionRepository;
    @Autowired
    private void setCmpTransactionRepository(CmpTransactionRepository cmpTransactionRepository) { this.cmpTransactionRepository = cmpTransactionRepository; }

    public void save(CmpTransaction transaction) {
        this.cmpTransactionRepository.save(transaction);
    }

    /**
     * List of transactions for given transactionId (even if current version of cmp czertainly
     * works with one request per transactionId).
     *
     * <p>List is prepare for future behaviour of CMP protocol (ir,cr,kur allows to process list of requests). It means
     * the given transactionId can keep relation to many request subjects (certificates).</p>
     *
     * <p>There's a next solution to move m:n relationship to another table (with removing certificate
     * column in {@link CmpTransactionService}) with list of {@link com.czertainly.core.dao.entity.Certificate}</p>
     *
     * @param transactionId
     * @return
     */
    public List<CmpTransaction> findByTransactionId(String transactionId) {
        return cmpTransactionRepository.findByTransactionId(transactionId);
    }

    /**
     * Helper to create {@link CmpTransaction} entity
     * 
     * @param transactionId
     * @param cmpProfile
     * @param certificateUuid
     * @param state
     * @return
     */
    public CmpTransaction createTransactionEntity(String transactionId, CmpProfile cmpProfile,
                                                   String certificateUuid, CmpTransactionState state) {
        CmpTransaction cmpTransaction = new CmpTransaction();
        cmpTransaction.setTransactionId(transactionId);
        cmpTransaction.setCmpProfile(cmpProfile);
        cmpTransaction.setCertificateUuid(UUID.fromString(certificateUuid));
        cmpTransaction.setState(state);
        return cmpTransaction;
    }

    /**
     * Find transaction by transactionId and fingerprint from related certificate
     * @param transactionId unique identifier of transaction
     * @param fingerprint unique identifier of certificate
     * @return a concrete transaction (fingerprints must be unique in given table)
     */
    public Optional<CmpTransaction> findByTransactionIdAndFingerprint(String transactionId, String fingerprint) {
        return cmpTransactionRepository.findByTransactionIdAndFingerprint(transactionId, fingerprint);
    }

    /**
     * Find transaction by transactionId and serial number from related certificate
     * @param transactionId unique identifier of transaction
     * @param serialNumber unique identifier of certificate
     * @return a concrete transaction (serial numbers must be unique in given table)
     */
    public Optional<CmpTransaction> findByTransactionIdAndCertificateSerialNumber(String transactionId, String serialNumber) {
        return cmpTransactionRepository.findByTransactionIdAndSerialNumber(transactionId, serialNumber);
    }
}
