package com.czertainly.core.evaluator;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleActionType;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RuleAction;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

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

            SearchableFields searchableField;
            try {
                searchableField = Enum.valueOf(SearchableFields.class, action.getFieldIdentifier());
            } catch (IllegalArgumentException e) {
                throw new RuleException("Field identifier '" + action.getFieldIdentifier() + "' is not supported.");
            }

            UUID propertyUuid = null;
            List<UUID> propertyUuids = null;
            boolean removeValue = action.getActionData() == null;
            if (!removeValue) {
                if (action.getActionData() instanceof ArrayList<?>) {
                    try {
                        propertyUuids = new ArrayList<>();
                        for (Object actionDataItem : (ArrayList<?>) action.getActionData()) {
                            propertyUuids.add(UUID.fromString(actionDataItem.toString()));
                        }
                    } catch (IllegalArgumentException ex) {
                        // TODO: handle illegal argument
                        propertyUuids = null;
                    }
                } else {
                    String propertyUuidStr = action.getActionData().toString();
                    try {
                        propertyUuid = UUID.fromString(propertyUuidStr);
                    } catch (IllegalArgumentException e) {
                        // TODO: handle illegal argument
                    }
                }
            }
            removeValue = removeValue || (propertyUuids != null && propertyUuids.isEmpty());

            if (!removeValue && propertyUuid == null && propertyUuids == null) {
                throw new RuleException(String.format("Wrong action data for set field %s %s of %s %s: %s", action.getFieldSource().getLabel(), action.getFieldIdentifier(), resource.getLabel(), object.getUuid().toString(), action.getActionData().toString()));
            }

            SecuredUUID newPropertyUuid = removeValue ? null : SecuredUUID.fromUUID(propertyUuid != null ? propertyUuid : propertyUuids.get(0));
            switch (searchableField) {
                case RA_PROFILE_NAME ->
                        certificateService.switchRaProfile(certificateUuid, newPropertyUuid);
                case GROUP_NAME ->
                        certificateService.updateCertificateGroups(object.getSecuredUuid(), removeValue ? Set.of() : (propertyUuids == null ? Set.of(newPropertyUuid.getValue()) : new HashSet<>(propertyUuids)));
                case OWNER ->
                        certificateService.updateOwner(certificateUuid, newPropertyUuid == null ? null : newPropertyUuid.toString());
            }
        } else {
            super.performAction(action, object, Resource.CERTIFICATE);
        }
    }
}