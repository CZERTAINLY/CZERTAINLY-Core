package com.czertainly.core.service.impl;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckResultDto;
import com.czertainly.core.model.compliance.ComplianceResultDto;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ComplianceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ComplianceServiceImpl implements ComplianceService {

    @Override
    public ComplianceCheckResultDto getComplianceCheckResult(ComplianceResultDto complianceResult) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkCompliance(List<SecuredUUID> uuids, Resource resource, String type) {
        // will be implemented in compliance check V2 rewrite
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectCompliance(Resource resource, UUID objectUuid) {
        // will be implemented in compliance check V2 rewrite
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectsCompliance(Resource resource, List<UUID> objectUuids) {
        // will be implemented in compliance check V2 rewrite
    }
}
