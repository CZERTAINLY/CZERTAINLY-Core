package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.core.admin.AdminDto;

import java.security.cert.CertificateException;

public interface LocalAdminService {

    AdminDto addAdmin(AddAdminRequestDto request)
            throws CertificateException, AlreadyExistException, ValidationException, NotFoundException;
}
