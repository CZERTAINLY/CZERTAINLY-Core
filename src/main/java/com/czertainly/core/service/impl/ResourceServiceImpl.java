package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class ResourceServiceImpl implements ResourceService {
    private static final Logger logger = LoggerFactory.getLogger(ResourceServiceImpl.class);
    private LocationService locationService;
    private AcmeProfileService acmeProfileService;
    private AuthorityInstanceService authorityInstanceService;
    private ComplianceProfileService complianceProfileService;
    private ConnectorService connectorService;
    private CredentialService credentialService;
    private EntityInstanceService entityInstanceService;
    private GroupService groupService;
    private RaProfileService raProfileService;

    @Autowired
    public void setAcmeProfileService(AcmeProfileService acmeProfileService) {
        this.acmeProfileService = acmeProfileService;
    }

    @Autowired
    public void setAuthorityInstanceService(AuthorityInstanceService authorityInstanceService) {
        this.authorityInstanceService = authorityInstanceService;
    }

    @Autowired
    public void setComplianceProfileService(ComplianceProfileService complianceProfileService) {
        this.complianceProfileService = complianceProfileService;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setCredentialService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Autowired
    public void setEntityInstanceService(EntityInstanceService entityInstanceService) {
        this.entityInstanceService = entityInstanceService;
    }

    @Autowired
    public void setGroupService(GroupService groupService) {
        this.groupService = groupService;
    }

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    @Autowired
    public void setRaProfileService(RaProfileService raProfileService) {
        this.raProfileService = raProfileService;
    }

    @Override
    public List<NameAndUuidDto> getObjectsForResource(Resource resourceName) throws NotFoundException {
        switch (resourceName) {
            case ACME_PROFILE:
                return acmeProfileService.listResourceObjects(SecurityFilter.create());
            case AUTHORITY:
                return authorityInstanceService.listResourceObjects(SecurityFilter.create());
            case COMPLIANCE_PROFILE:
                return complianceProfileService.listResourceObjects(SecurityFilter.create());
            case CONNECTOR:
                return connectorService.listResourceObjects(SecurityFilter.create());
            case CREDENTIAL:
                return credentialService.listResourceObjects(SecurityFilter.create());
            case ENTITY:
                return entityInstanceService.listResourceObjects(SecurityFilter.create());
            case CERTIFICATE_GROUP:
                return groupService.listResourceObjects(SecurityFilter.create());
            case LOCATION:
                return locationService.listResourceObjects(SecurityFilter.create());
            case RA_PROFILE:
                return raProfileService.listResourceObjects(SecurityFilter.create());
            default:
                throw new NotFoundException("Cannot list objects for requested resource: " + resourceName.getCode());
        }
    }
}
