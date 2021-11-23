package com.czertainly.core.service;

import com.czertainly.api.core.modal.AddAdminRequestDto;
import com.czertainly.api.core.modal.AdminDto;
import com.czertainly.api.core.modal.EditAdminRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;

import java.security.cert.CertificateException;
import java.util.List;

public interface AdminService {
    List<AdminDto> listAdmins();

    AdminDto addAdmin(AddAdminRequestDto request)
            throws CertificateException, AlreadyExistException, ValidationException, NotFoundException;

    AdminDto getAdminBySerialNumber(String serialNumber) throws NotFoundException;

    AdminDto getAdminByUuid(String uuid) throws NotFoundException;

    AdminDto getAdminByUsername(String username) throws NotFoundException;

    AdminDto editAdmin(String uuid, EditAdminRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException;

    void removeAdmin(String uuid) throws NotFoundException;

    void enableAdmin(String uuid) throws NotFoundException, CertificateException;

    void disableAdmin(String uuid) throws NotFoundException;

    void bulkRemoveAdmin(List<String> adminUuids);

    void bulkDisableAdmin(List<String> adminUuids);

    void bulkEnableAdmin(List<String> adminUuids);
}
