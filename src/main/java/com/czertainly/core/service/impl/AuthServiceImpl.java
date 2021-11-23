package com.czertainly.core.service.impl;

import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.repository.AdminRepository;
import com.czertainly.core.service.AuthService;
import com.czertainly.api.core.modal.AuthProfileDto;
import com.czertainly.api.core.modal.EditAuthProfileDto;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AdminRepository adminRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACCESS, operation = OperationType.REQUEST)
    public AuthProfileDto getAuthProfile() throws NotFoundException {
        String username = getCurrentUsername();
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(Admin.class, username));
        AuthProfileDto dto = new AuthProfileDto();
        dto.setEmail(admin.getEmail());
        dto.setName(admin.getName());
        dto.setRole(admin.getRole());
        dto.setSurname(admin.getSurname());
        dto.setUsername(admin.getUsername());
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACCESS, operation = OperationType.CHANGE)
    public void editAuthProfile(EditAuthProfileDto authProfileDTO) throws NotFoundException {
        String username = getCurrentUsername();
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(Admin.class, username));

        if (StringUtils.isNotBlank(authProfileDTO.getName())) {
            admin.setName(authProfileDTO.getName());
        }
        if (StringUtils.isNotBlank(authProfileDTO.getSurname())) {
            admin.setSurname(authProfileDTO.getSurname());
        }
        if (StringUtils.isNotBlank(authProfileDTO.getEmail())) {
            admin.setEmail(authProfileDTO.getEmail());
        }

        adminRepository.save(admin);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUsername();
        } else {
            throw new IllegalStateException("Unexpected type of principal.");
        }
    }
}
