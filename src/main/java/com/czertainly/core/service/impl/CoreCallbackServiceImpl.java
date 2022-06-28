package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.service.CoreCallbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CoreCallbackServiceImpl implements CoreCallbackService {

    public static final String CREDENTIAL_KIND_PATH_VARIABLE = "credentialKind";

    @Autowired
    private CredentialRepository credentialRepository;

    @Override
    public List<JsonAttributeContent> coreGetCredentials(RequestAttributeCallback callback) throws NotFoundException, ValidationException {
        if (callback.getPathVariables() == null ||
            callback.getPathVariables().get(CREDENTIAL_KIND_PATH_VARIABLE) == null) {
            throw new ValidationException(ValidationError.create("Required path variable credentialKind not found in backhook."));
        }

        String kind = callback.getPathVariables().get(CREDENTIAL_KIND_PATH_VARIABLE).toString();

        List<Credential> credentials = credentialRepository.findByKindAndEnabledTrue(kind);
        if (credentials == null || credentials.isEmpty()) {
            throw new NotFoundException(Credential.class, kind);
        }

        List<NameAndUuidDto> credentialDataList = credentials.stream()
                .map(c -> new NameAndUuidDto(c.getUuid(), c.getName()))
                .collect(Collectors.toList());

        List<JsonAttributeContent> jsonContent = new ArrayList<>();
        for (NameAndUuidDto credentialData : credentialDataList) {
            JsonAttributeContent content = new JsonAttributeContent(credentialData.getName(), credentialData);
            jsonContent.add(content);
        }

        return jsonContent;
    }

}
