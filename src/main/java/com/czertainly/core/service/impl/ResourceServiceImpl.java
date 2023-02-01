package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private DiscoveryService discoveryService;
    private EntityInstanceService entityInstanceService;
    private GroupService groupService;
    private RaProfileService raProfileService;
    private CryptographicKeyService keyService;
    private TokenProfileService tokenProfileService;
    private TokenInstanceService tokenInstanceService;
    private UserManagementService userManagementService;
    private RoleManagementService roleManagementService;
    private CertificateService certificateService;
    private AttributeService attributeService;

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
    public void setDiscoveryService(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
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

    @Autowired
    public void setKeyService(CryptographicKeyService keyService) {
        this.keyService = keyService;
    }

    @Autowired
    public void setTokenProfileService(TokenProfileService tokenProfileService) {
        this.tokenProfileService = tokenProfileService;
    }

    @Autowired
    public void setTokenInstanceService(TokenInstanceService tokenInstanceService) {
        this.tokenInstanceService = tokenInstanceService;
    }

    @Autowired
    public void setUserManagementService(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Autowired
    public void setRoleManagementService(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
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
            case GROUP:
                return groupService.listResourceObjects(SecurityFilter.create());
            case LOCATION:
                return locationService.listResourceObjects(SecurityFilter.create());
            case RA_PROFILE:
                return raProfileService.listResourceObjects(SecurityFilter.create());
            case TOKEN_PROFILE:
                return tokenProfileService.listResourceObjects(SecurityFilter.create());
            case TOKEN:
                return tokenInstanceService.listResourceObjects(SecurityFilter.create());
            default:
                throw new NotFoundException("Cannot list objects for requested resource: " + resourceName.getCode());
        }
    }

    @Override
    public List<ResponseAttributeDto> updateAttributeContentForObject(Resource resourceName, SecuredUUID objectUuid, UUID attributeUuid, List<BaseAttributeContent> request) throws NotFoundException {
        logger.info("Updating the attribute {} for resource {} ith value {}", attributeUuid, resourceName, attributeUuid);
        switch (resourceName) {
            case ACME_PROFILE:
                acmeProfileService.evaluatePermissionChain(objectUuid);
                break;
            case AUTHORITY:
                authorityInstanceService.evaluatePermissionChain(objectUuid);
                break;
            case COMPLIANCE_PROFILE:
                complianceProfileService.evaluatePermissionChain(objectUuid);
                break;
            case CONNECTOR:
                connectorService.evaluatePermissionChain(objectUuid);
                break;
            case CREDENTIAL:
                credentialService.evaluatePermissionChain(objectUuid);
                break;
            case DISCOVERY:
                discoveryService.evaluatePermissionChain(objectUuid);
                break;
            case ENTITY:
                entityInstanceService.evaluatePermissionChain(objectUuid);
                break;
            case GROUP:
                groupService.evaluatePermissionChain(objectUuid);
                break;
            case LOCATION:
                locationService.evaluatePermissionChain(objectUuid);
                break;
            case RA_PROFILE:
                raProfileService.evaluatePermissionChain(objectUuid);
                break;
            case TOKEN_PROFILE:
                tokenProfileService.evaluatePermissionChain(objectUuid);
                break;
            case TOKEN:
                tokenInstanceService.evaluatePermissionChain(objectUuid);
                break;
            case USER:
                userManagementService.evaluatePermissionChain(objectUuid);
                break;
            case ROLE:
                roleManagementService.evaluatePermissionChain(objectUuid);
                break;
            case CRYPTOGRAPHIC_KEY:
                keyService.evaluatePermissionChain(objectUuid);
                break;
            case CERTIFICATE:
                certificateService.evaluatePermissionChain(objectUuid);
                break;
            default:
                throw new NotFoundException("Cannot update custom attribute for requested resource: " + resourceName.getCode());
        }
        if (attributeService.getResourceAttributes(resourceName)
                .stream()
                .filter(
                        e -> e.getUuid()
                                .equals(attributeUuid.toString())
                ).collect(Collectors.toList()).isEmpty()) {
            throw new NotFoundException(
                    "Given Attribute is not available for the resource or is not enabled"
            );
        }
        attributeService.updateAttributeContent(
                objectUuid.getValue(),
                attributeUuid,
                request,
                resourceName
        );
        return attributeService.getCustomAttributesWithValues(
                objectUuid.getValue(),
                resourceName
        );
    }
}
