package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;

public interface ComplianceService {
    ComplianceGroup getComplianceGroupEntity(String uuid, Connector connector, String kind) throws NotFoundException;

    Boolean complianceGroupExists(String uuid, Connector connector, String kind);

    ComplianceRule getComplianceRuleEntity(String uuid, Connector connector, String kind) throws NotFoundException;

    Boolean complianceRuleExists(String uuid, Connector connector, String kind);

    void addComplianceGroup(ComplianceGroup complianceGroup);

    void addComplianceRule(ComplianceRule complianceRule);

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
     */
    void complianceCheckForRaProfile(String uuid) throws NotFoundException, ConnectorException;

    /**
     * Initiate the Compliance check for all the certificates discovered through any discovery
     * @param uuid Uuid of the certificate discovery
     * @throws NotFoundException Thrown when the RA Profile is not found
     */
    void complianceCheckForDiscovery(String uuid) throws NotFoundException;

    /**
     * Initiate the Compliance check for all the certificates associated with the compliance profile
     * @param uuid Uuid of the compliance profile
     * @throws NotFoundException Thrown when the Compliance Profile is not found
     */
    void complianceCheckForComplianceProfile(String uuid) throws ConnectorException;

}
