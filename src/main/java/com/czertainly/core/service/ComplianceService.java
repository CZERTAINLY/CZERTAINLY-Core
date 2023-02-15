package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.security.authz.SecuredUUID;

import java.util.List;
import java.util.UUID;

public interface ComplianceService {

    /**
     * Get the Compliance Group Entity
     * @param uuid Uuid of the compliance group
     * @param connector Connector Entity
     * @param kind Kind of the Connector
     * @return true of the compliance group exists or false
     */
    Boolean complianceGroupExists(SecuredUUID uuid, Connector connector, String kind);

    /**
     * Get the Compliance Rule Entity
     * @param uuid Uuid of the compliance rule
     * @param connector Connector Entity
     * @param kind Kind of the Connector
     * @return true of the compliance group exists or false
     */
    Boolean complianceRuleExists(SecuredUUID uuid, Connector connector, String kind);

    /**
     * Check and update the compliance of a certificate based on the associated compliance profile
     * @param certificate Certificate entity
     * @throws ConnectorException Thrown when there are issues regarding the connector calls
     */
    void checkComplianceOfCertificate(Certificate certificate) throws ConnectorException;

    /**
     * Initiate the Compliance check for all the certificates associated with the RA Profile
     * @param uuid Uuid of the RA Profile
     * @throws NotFoundException Thrown when the RA Profile is not found
     * @throws ConnectorException Thrown when there are issues in communicating with the compliance provider
     */
    void complianceCheckForRaProfile(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    /**
     * Initiate the Compliance check for all the certificates associated with the compliance profile
     * @param uuid Uuid of the compliance profile
     * @throws NotFoundException Thrown when the Compliance Profile is not found
     */
    void complianceCheckForComplianceProfile(SecuredUUID uuid) throws ConnectorException;


    /**
     * Get the list of rules based on the containing ids
     * @param ids Ids of the entity
     * @return List of compliance rule entity
     */
    List<ComplianceRule> getComplianceRuleEntityForIds(List<String> ids);

    /**
     * Get the list of compliance profile rules based on the Ids
     * @param ids Ids of the compliance rules
     * @return List of compliance profile rule entity
     */
    List<ComplianceProfileRule> getComplianceProfileRuleEntityForIds(List<String> ids);

    /**
     * Get the list of compliance profile rules based on the Ids
     * @param uuids Ids of the compliance rules
     * @return List of compliance profile rule entity
     */
    List<ComplianceProfileRule> getComplianceProfileRuleEntityForUuids(List<String> uuids);

    /**
     * Fetch the list of groups and rules from the compliance provider and add them into the database
     * @param connector Connector Entity which implements COMPLIANCE_PROVIDER function group
     * @throws ConnectorException Raises when there are issues with communicating with the connector
     */
    void addFetchGroupsAndRules(Connector connector) throws ConnectorException;

    /**
     * Fetch the list of groups and rules from the compliance provider and update them into the database
     * @param connector Connector Entity which implements COMPLIANCE_PROVIDER function group
     * @throws ConnectorException Raises when there are issues with communicating with the connector
     */
    void updateGroupsAndRules(Connector connector) throws ConnectorException;

    /**
     * Update the status of the compliance for the certificate. The method takes the uuid of the compliance rule
     * get all the certificate that has the rule and perform the following operation.
     *
     * The Compliance Update goes through the following protocol
     *
     * 1. Get the list of certificates where the compliance_result column json contains the UUID of the compliance rule
     * 2. Iterate through each certificate and for each certificate
     *      2.1 Get the Compliance Validation result from the certificate
     *      2.2 Extract Compliant, non-compliant and not applicable result
     *      3.3 Check where the rule exists and remove it
     *      3.4 Once the rule is removed, update the compliance status of the certificate based on the following condition
     *      3.5 If the Non-Compliant rules are not empty, leave the status as non-compliant
     *      3.6 If the Non-Compliant rules are empty and the certificate has compliant rules, then the status is compliant
     *      3.7 If the compliant and the non-compliant rules are empty then the status is set to Not Applicable
     * @param ruleUuid UUID of the compliance rule
     */
    void inHouseComplianceStatusUpdate(UUID ruleUuid);
}
