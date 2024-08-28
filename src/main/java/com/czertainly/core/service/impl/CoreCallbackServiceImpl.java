package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContent;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CoreCallbackService;
import com.czertainly.core.service.CredentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class CoreCallbackServiceImpl implements CoreCallbackService {

    public static final String CREDENTIAL_KIND_PATH_VARIABLE = "credentialKind";

    @Autowired
    private CredentialService credentialService;

    @Override
    public List<ObjectAttributeContent> coreGetCredentials(RequestAttributeCallback callback) throws ValidationException {
        if (callback.getPathVariable() == null ||
                callback.getPathVariable().get(CREDENTIAL_KIND_PATH_VARIABLE) == null) {
            throw new ValidationException(ValidationError.create("Required path variable credentialKind not found in callback."));
        }

        String kind = callback.getPathVariable().get(CREDENTIAL_KIND_PATH_VARIABLE).toString();
        List<NameAndUuidDto> credentialDataList = credentialService.listCredentialsCallback(SecurityFilter.create(), kind);

        List<ObjectAttributeContent> jsonContent = new ArrayList<>();
        for (NameAndUuidDto credentialData : credentialDataList) {
            ObjectAttributeContent content = new ObjectAttributeContent(credentialData.getName(), credentialData);
            jsonContent.add(content);
        }

        return jsonContent;
    }

}
