package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.AddUserRequestDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserRequestDto;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public interface LocalAdminService {
    UserDetailDto createUser(AddUserRequestDto request) throws NotFoundException, CertificateException, NoSuchAlgorithmException;
}
