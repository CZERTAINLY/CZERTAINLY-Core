package com.czertainly.core.service.v2;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;

import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    List<AttributeDefinition> listIssueCertificateAttributes(
            String raProfileName) throws NotFoundException, ConnectorException;

    boolean validateIssueCertificateAttributes(
            String raProfileName,
            List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException;

    ClientCertificateDataResponseDto issueCertificate(
            String raProfileName,
            ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException;

    ClientCertificateDataResponseDto renewCertificate(
            String raProfileName,
            String certificateId,
            ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException;

    List<AttributeDefinition> listRevokeCertificateAttributes(
            String raProfileName) throws NotFoundException, ConnectorException;

    boolean validateRevokeCertificateAttributes(
            String raProfileName,
            List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException;

    void revokeCertificate(
            String raProfileName,
            String certificateId,
            ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException;
}
