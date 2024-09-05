package com.czertainly.core.evaluator;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.workflows.ExecutionItem;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component("certificates")
@Transactional
public class CertificateRuleEvaluator extends RuleEvaluator<Certificate> {

    private CertificateService certificateService;

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Override
    public void performAction(ExecutionItem executionItem, Certificate object, Resource resource) throws NotFoundException, AttributeException, CertificateOperationException, RuleException {
        if (executionItem.getFieldSource() != FilterFieldSource.PROPERTY) {
            super.performAction(executionItem, object, Resource.CERTIFICATE);
            return;
        }

        SecuredUUID certificateUuid = object.getSecuredUuid();

        FilterField searchableField;
        try {
            searchableField = Enum.valueOf(FilterField.class, executionItem.getFieldIdentifier());
        } catch (IllegalArgumentException e) {
            throw new RuleException("Field identifier '" + executionItem.getFieldIdentifier() + "' is not supported.");
        }

        UUID propertyUuid = null;
        List<UUID> propertyUuids = null;
        boolean removeValue = executionItem.getData() == null;
        if (!removeValue) {
            if (executionItem.getData() instanceof Iterable<?> actionDataItems) {
                try {
                    propertyUuids = new ArrayList<>();
                    for (Object actionDataItem : actionDataItems) {
                        propertyUuids.add(UUID.fromString(actionDataItem.toString()));
                    }
                } catch (IllegalArgumentException ex) {
                    // TODO: handle illegal argument
                    propertyUuids = null;
                }
            } else {
                String propertyUuidStr = executionItem.getData().toString();
                try {
                    propertyUuid = UUID.fromString(propertyUuidStr);
                } catch (IllegalArgumentException e) {
                    // TODO: handle illegal argument
                }
            }
        }
        removeValue = removeValue || (propertyUuids != null && propertyUuids.isEmpty());

        if (!removeValue && propertyUuid == null && propertyUuids == null) {
            throw new RuleException(String.format("Wrong action data for set field %s %s of %s %s: %s", executionItem.getFieldSource().getLabel(), executionItem.getFieldIdentifier(), resource.getLabel(), object.getUuid().toString(), executionItem.getData().toString()));
        }

        SecuredUUID newPropertyUuid = removeValue ? null : SecuredUUID.fromUUID(propertyUuid != null ? propertyUuid : propertyUuids.get(0));
        switch (searchableField) {
            case RA_PROFILE_NAME -> certificateService.switchRaProfile(certificateUuid, newPropertyUuid);
            case GROUP_NAME ->
                    certificateService.updateCertificateGroups(object.getSecuredUuid(), removeValue ? Set.of() : (propertyUuids == null ? Set.of(newPropertyUuid.getValue()) : new HashSet<>(propertyUuids)));
            case OWNER ->
                    certificateService.updateOwner(certificateUuid, newPropertyUuid == null ? null : newPropertyUuid.toString());
        }
    }
}