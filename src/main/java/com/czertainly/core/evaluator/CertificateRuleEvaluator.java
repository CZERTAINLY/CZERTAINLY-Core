package com.czertainly.core.evaluator;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleActionType;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RuleAction;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("certificates")
public class CertificateRuleEvaluator extends RuleEvaluator<Certificate> {

    private CertificateService certificateService;

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Override
    public void performAction(RuleAction action, Certificate object, Resource resource) throws NotFoundException, AttributeException, CertificateOperationException, RuleException {
        if (action.getActionType() == RuleActionType.SET_FIELD & action.getFieldSource() == FilterFieldSource.PROPERTY) {
            SecuredUUID certificateUuid = object.getSecuredUuid();
            SecuredUUID propertyUuid = SecuredUUID.fromString(action.getActionData().toString());
            switch (action.getFieldIdentifier()) {
                case "raProfile" -> certificateService.switchRaProfile(certificateUuid, propertyUuid);
                case "group" -> certificateService.updateCertificateGroup(object.getSecuredUuid(), propertyUuid);
                case "owner" -> certificateService.updateOwner(certificateUuid, String.valueOf(propertyUuid), null);
            }
        }
        else {
            super.performAction(action, object, Resource.CERTIFICATE);
        }
    }


}

