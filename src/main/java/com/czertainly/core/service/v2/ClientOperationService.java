package com.czertainly.core.service.v2;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.v2.*;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    List<BaseAttribute> listIssueCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid) throws ConnectorException;

    boolean validateIssueCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    ClientCertificateDataResponseDto issueCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            ClientCertificateSignRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    ClientCertificateDataResponseDto renewCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRenewRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException;

    ClientCertificateDataResponseDto regenerateCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRegenerationRequestDto request
    ) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException;

    List<BaseAttribute> listRevokeCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid) throws ConnectorException;

    boolean validateRevokeCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    void revokeCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRevocationDto request) throws ConnectorException;
}
