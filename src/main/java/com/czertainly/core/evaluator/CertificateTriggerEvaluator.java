package com.czertainly.core.evaluator;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@Transactional
public class CertificateTriggerEvaluator extends TriggerEvaluator<Certificate> {

    private CertificateService certificateService;

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Override
    protected void performSetFieldPropertyExecution(String fieldIdentifier, Object actionData, Certificate object) throws RuleException, CertificateOperationException, NotFoundException, AttributeException {
        SecuredUUID certificateUuid = object.getSecuredUuid();
        FilterField searchableField;
        try {
            searchableField = Enum.valueOf(FilterField.class, fieldIdentifier);
        } catch (IllegalArgumentException e) {
            throw new RuleException("Field identifier '" + fieldIdentifier + "' is not supported.");
        }

        UUID propertyUuid = null;
        List<UUID> propertyUuids = null;
        boolean removeValue = actionData == null;
        if (!removeValue) {
            if (actionData instanceof Iterable<?> actionDataItems) {
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
                String propertyUuidStr = actionData.toString();
                try {
                    propertyUuid = UUID.fromString(propertyUuidStr);
                } catch (IllegalArgumentException e) {
                    // TODO: handle illegal argument
                }
            }
        }
        removeValue = removeValue || (propertyUuids != null && propertyUuids.isEmpty());

        if (!removeValue && propertyUuid == null && propertyUuids == null) {
            throw new RuleException(String.format("Wrong action data for set field %s %s of %s %s: %s", FilterFieldSource.PROPERTY.getLabel(), fieldIdentifier, Resource.CERTIFICATE.getLabel(), object.getUuid().toString(), actionData.toString()));
        }

        SecuredUUID newPropertyUuid = removeValue ? null : SecuredUUID.fromUUID(propertyUuid != null ? propertyUuid : propertyUuids.getFirst());
        switch (searchableField) {
            case RA_PROFILE_NAME -> certificateService.switchRaProfile(certificateUuid, newPropertyUuid);
            case GROUP_NAME ->
                    certificateService.updateCertificateGroups(object.getSecuredUuid(), removeValue ? Set.of() : (propertyUuids == null ? Set.of(newPropertyUuid.getValue()) : new HashSet<>(propertyUuids)));
            case OWNER ->
                    certificateService.updateOwner(certificateUuid, newPropertyUuid == null ? null : newPropertyUuid.toString());
            default -> throw new RuleException("Field identifier '%s' is not supported field to set for certificate.".formatted(fieldIdentifier));
        }
    }
}