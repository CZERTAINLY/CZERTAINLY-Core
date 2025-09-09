package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.ComplianceController;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ComplianceService;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ComplianceControllerImpl implements ComplianceController {

    private ComplianceService complianceService;

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.CHECK_COMPLIANCE)
    public void checkCompliance(List<UUID> uuids, Resource resource, String type) {
        complianceService.checkCompliance(SecuredUUID.fromUuidList(uuids), resource, type);
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.CHECK_COMPLIANCE)
    public void checkResourceObjectsCompliance(@LogResource(resource = true, affiliated = true) Resource resource, @LogResource(uuid = true, affiliated = true) List<UUID> objectUuids) {
        complianceService.checkResourceObjectsCompliance(resource, objectUuids);
    }
}
