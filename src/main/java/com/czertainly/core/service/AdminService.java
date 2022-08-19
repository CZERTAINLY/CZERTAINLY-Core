package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.client.admin.EditAdminRequestDto;
import com.czertainly.api.model.core.admin.AdminDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.security.cert.CertificateException;
import java.util.List;

public interface AdminService {
    List<AdminDto> listAdmins(SecurityFilter filter);

    AdminDto addAdmin(AddAdminRequestDto request)
            throws CertificateException, AlreadyExistException, ValidationException, NotFoundException;

    AdminDto getAdminByUuid(SecuredUUID uuid) throws NotFoundException;

    AdminDto editAdmin(SecuredUUID uuid, EditAdminRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException;

    void removeAdmin(SecuredUUID uuid) throws NotFoundException;

    void enableAdmin(SecuredUUID uuid) throws NotFoundException, CertificateException;

    void deleteAdmin(SecuredUUID uuid) throws NotFoundException;

    void bulkRemoveAdmin(List<SecuredUUID> adminUuids);

    void bulkDisableAdmin(List<SecuredUUID> adminUuids);

    void bulkDeleteAdmin(List<SecuredUUID> adminUuids);

    void bulkDisableAdmin(List<String> adminUuids);

    void bulkEnableAdmin(List<String> adminUuids);
}
