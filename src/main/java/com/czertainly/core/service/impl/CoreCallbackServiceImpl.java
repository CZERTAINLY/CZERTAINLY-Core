package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CoreCallbackService;
import com.czertainly.core.service.CredentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class CoreCallbackServiceImpl implements CoreCallbackService {

    public static final String CREDENTIAL_KIND_PATH_VARIABLE = "credentialKind";

    @Autowired
    private CredentialService credentialService;

    @Override
    public List<JsonAttributeContent> coreGetCredentials(RequestAttributeCallback callback) throws NotFoundException, ValidationException {
        if (callback.getPathVariables() == null ||
                callback.getPathVariables().get(CREDENTIAL_KIND_PATH_VARIABLE) == null) {
            throw new ValidationException(ValidationError.create("Required path variable credentialKind not found in backhook."));
        }

        String kind = callback.getPathVariables().get(CREDENTIAL_KIND_PATH_VARIABLE).toString();
        List<NameAndUuidDto> credentialDataList = credentialService.listCredentialsCallback(SecurityFilter.create(), kind);

        List<JsonAttributeContent> jsonContent = new ArrayList<>();
        for (NameAndUuidDto credentialData : credentialDataList) {
            JsonAttributeContent content = new JsonAttributeContent(credentialData.getName(), credentialData);
            jsonContent.add(content);
        }

        return jsonContent;
    }

}
