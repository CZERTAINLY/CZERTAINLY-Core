package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.AuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserManagementApiClient userManagementApiClient;

    @Autowired
    private ResourceApiClient resourceApiClient;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACCESS, operation = OperationType.REQUEST)
    public UserDto getAuthProfile() throws NotFoundException {
        UserProfileDto userProfileDto;
        try {
            CzertainlyUserDetails userDetails = (CzertainlyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            ObjectMapper objectMapper = new ObjectMapper();
            userProfileDto = objectMapper.readValue(userDetails.getRawData(), UserProfileDto.class);
        } catch (Exception e) {
            throw new ValidationException(ValidationError.create("Cannot retrieve profile information for Unknown/Anonymous user"));
        }
        return userManagementApiClient.getUserDetail(userProfileDto.getUser().getUuid());
    }

    @Override
    public List<ResourceDetailDto> getAllResources() {
        return resourceApiClient.getAllResources();
    }

}
