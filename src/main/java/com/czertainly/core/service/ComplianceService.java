package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;

import java.util.List;

public interface ComplianceService {
    /**
     * Get the Compliance Group Entity
     * @param uuid Uuid of the compliance group
     * @param connector Connector Entity
     * @param kind Kind of the Connector
     * @return Compliance Group
     * @throws NotFoundException when the entity is not found for the given parameters
     */
    ComplianceGroup getComplianceGroupEntity(String uuid, Connector connector, String kind) throws NotFoundException;

    /**
     * Get the Compliance Group Entity
     * @param uuid Uuid of the compliance group
     * @param connector Connector Entity
     * @param kind Kind of the Connector
     * @return true of the compliance group exists or false
     */
    Boolean complianceGroupExists(String uuid, Connector connector, String kind);

    /**
     * Get the Compliance Rule Entity
     * @param uuid Uuid of the compliance rule
     * @param connector Connector Entity
     * @param kind Kind of the Connector
     * @return Compliance Group
     * @throws NotFoundException when the entity is not found for the given parameters
     */
    ComplianceRule getComplianceRuleEntity(String uuid, Connector connector, String kind) throws NotFoundException;

    /**
     * Get the Compliance Rule Entity
     * @param uuid Uuid of the compliance rule
     * @param connector Connector Entity
     * @param kind Kind of the Connector
     * @return true of the compliance group exists or false
     */
    Boolean complianceRuleExists(String uuid, Connector connector, String kind);

    /**
     * Add a new / update a compliance group entity in the database
     * @param complianceGroup Compliance Group entity
     */
    void saveComplianceGroup(ComplianceGroup complianceGroup);

    /**
     * Add a new / update a compliance rule entity in the database
     * @param complianceRule Compliance Rule entity
     */
    void saveComplianceRule(ComplianceRule complianceRule);

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
    void complianceCheckForRaProfile(String uuid) throws NotFoundException, ConnectorException;

    /**
     * Initiate the Compliance check for all the certificates associated with the compliance profile
     * @param uuid Uuid of the compliance profile
     * @throws NotFoundException Thrown when the Compliance Profile is not found
     */
    void complianceCheckForComplianceProfile(String uuid) throws ConnectorException;

    /**
     * Get the Compliance Rule Entity by Id
     * @param id Id of the object in the database
     * @return ComplianceRule Entity
     */
    ComplianceRule getComplianceRuleEntity(Long id);

    /**
     * Get the list of rules based on the containing ids
     * @param ids Ids of the entity
     * @return List of compliance rule entity
     */
    List<ComplianceRule> getComplianceRuleEntityForIds(List<Long> ids);

    /**
     * Get the list of compliance profile rules based on the Ids
     * @param ids Ids of the compliance rules
     * @return List of compliance profile rule entity
     */
    List<ComplianceProfileRule> getComplianceProfileRuleEntityForIds(List<Long> ids);

    /**
     * Fetch the list of groups and rules from the compliance provider and add them into the database
     * @param connector Connector Entity which implements CREDENTIAL_PROVIDER functional group
     * @throws ConnectorException Raises when there are issues with communicating with the connector
     */
    void addFetchGroupsAndRules(Connector connector) throws ConnectorException;

    /**
     * Fetch the list of groups and rules from the compliance provider and update them into the database
     * @param connector Connector Entity which implements CREDENTIAL_PROVIDER functional group
     * @throws ConnectorException Raises when there are issues with communicating with the connector
     */
    void updateGroupsAndRules(Connector connector) throws ConnectorException;
}
