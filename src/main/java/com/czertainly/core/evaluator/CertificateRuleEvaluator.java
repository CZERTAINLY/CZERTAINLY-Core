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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component("certificates")
public class CertificateRuleEvaluator extends RuleEvaluator<Certificate> {

    private CertificateService certificateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Override
    public void performAction(RuleAction action, Certificate object, Resource resource) throws NotFoundException, AttributeException, CertificateOperationException, RuleException {
        if (action.getActionType() == RuleActionType.SET_FIELD & action.getFieldSource() == FilterFieldSource.PROPERTY) {
            SecuredUUID certificateUuid = object.getSecuredUuid();
            String propertyUuidStr = action.getActionData().toString();

            UUID propertyUuid = null;
            Set<UUID> propertyUuids = null;
            try {
                propertyUuid = UUID.fromString(propertyUuidStr);
            } catch (IllegalArgumentException e) {
                // TODO: handle illegal argument
            }

            // try to map it to list of UUIDs
            if (propertyUuid == null) {
                if (!action.getFieldIdentifier().equals("group")) {
                    return;
                }
                try {
                    propertyUuids = new HashSet<>();
                    List<String> propertyUuidItems = objectMapper.readValue(propertyUuidStr, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    for (String propertyUuidItem : propertyUuidItems) {
                        propertyUuids.add(UUID.fromString(propertyUuidItem));
                    }
                } catch (IllegalArgumentException | JsonProcessingException e) {
                    // TODO: handle illegal argument
                    return;
                }
            }

            switch (action.getFieldIdentifier()) {
                case "raProfile":
                    certificateService.switchRaProfile(certificateUuid, SecuredUUID.fromUUID(propertyUuid));
                    break;
                case "group":
                    certificateService.updateCertificateGroups(object.getSecuredUuid(), propertyUuid == null ? propertyUuids : Set.of(propertyUuid));
                    break;
                case "owner":
                    certificateService.updateOwner(certificateUuid, propertyUuid.toString());
                    break;
            }
        } else {
            super.performAction(action, object, Resource.CERTIFICATE);
        }
    }
}