package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CertificateController;
import com.czertainly.api.model.client.approval.ApprovalResponseDto;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.v2.ClientCertificateRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalService;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.impl.CertificateServiceImpl;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.converter.AttributeContentTypeConverter;
import com.czertainly.core.util.converter.CertificateFormatConverter;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.cert.X509Certificate;
import java.util.*;


@RestController
public class CertificateControllerImpl implements CertificateController {

	private CertificateService certificateService;

	private CertValidationService certValidationService;

	private CertificateEventHistoryService certificateEventHistoryService;

	private ClientOperationService clientOperationService;

	private ApprovalService approvalService;

	@InitBinder
	public void initBinder(final WebDataBinder webdataBinder) {
		webdataBinder.registerCustomEditor(CertificateFormat.class, new CertificateFormatConverter());
	}

	@Override
	public CertificateResponseDto listCertificates(SearchRequestDto request) throws ValidationException {
		return certificateService.listCertificates(SecurityFilter.create(), request);
	}

	@Override
	public CertificateDetailDto getCertificate(@PathVariable String uuid)
			throws NotFoundException, CertificateException, IOException {
		return certificateService.getCertificate(SecuredUUID.fromString(uuid));
	}

	@Override
	public void deleteCertificate(@PathVariable String uuid) throws NotFoundException {
		certificateService.deleteCertificate(SecuredUUID.fromString(uuid));
	}

	@Override
	public void updateCertificateObjects(String uuid, CertificateUpdateObjectsDto request) throws NotFoundException {
		certificateService.updateCertificateObjects(SecuredUUID.fromString(uuid), request);
	}

	@Override
	public void check(@PathVariable String uuid)
			throws CertificateException, IOException, NotFoundException {
		Certificate crt = certificateService.getCertificateEntity(SecuredUUID.fromString(uuid));
		certValidationService.validate(crt);
	}

	@Override
	public void bulkUpdateCertificateObjects(MultipleCertificateObjectUpdateDto request) throws NotFoundException {
		certificateService.bulkUpdateCertificateObjects(SecurityFilter.create(), request);
	}

	@Override
	public ResponseEntity<UuidDto> upload(@RequestBody UploadCertificateRequestDto request)
			throws AlreadyExistException, CertificateException, NoSuchAlgorithmException {
		CertificateDetailDto dto = certificateService.upload(request);
		URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(dto.getUuid())
                .toUri();
		UuidDto responseDto = new UuidDto();
		responseDto.setUuid(dto.getUuid());
        return ResponseEntity.created(location).body(responseDto);
	}

	@Override
	public BulkOperationResponse bulkDeleteCertificate(@RequestBody RemoveCertificateDto request) throws NotFoundException {
		certificateService.bulkDeleteCertificate(SecurityFilter.create(), request);
		BulkOperationResponse response = new BulkOperationResponse();
		response.setMessage("Initiated bulk delete Certificates. Please refresh after some time");
		response.setStatus(BulkOperationStatus.SUCCESS);
		return response;
	}

	@Override
	public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
		return certificateService.getSearchableFieldInformationByGroup();
	}


	@Override
	public List<CertificateEventHistoryDto> getCertificateEventHistory(String uuid) throws NotFoundException{
		return certificateEventHistoryService.getCertificateEventHistory(UUID.fromString(uuid));
	}

	@Override
	public List<LocationDto> listLocations(String certificateUuid) throws NotFoundException {
		return certificateService.listLocations(SecuredUUID.fromString(certificateUuid));
	}

	@Override
	public void checkCompliance(CertificateComplianceCheckDto request) throws NotFoundException {
		certificateService.checkCompliance(request);
	}

	@Override
	public Map<String, CertificateValidationDto> getCertificateValidationResult(String uuid) throws NotFoundException, CertificateException, IOException {
		return certificateService.getCertificateValidationResult(SecuredUUID.fromString(uuid));
	}

	@Override
	public List<BaseAttribute> getCsrGenerationAttributes() {
		return certificateService.getCsrGenerationAttributes();
	}

	@Override
	public List<CertificateContentDto> getCertificateContent(List<String> uuids) {
		return certificateService.getCertificateContent(uuids);
	}

	@Override
	public CertificateDetailDto submitCertificateRequest(ClientCertificateRequestDto request) throws ValidationException, NotFoundException, CertificateException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
		return clientOperationService.submitCertificateRequest(request);
	}

	@Override
	public List<CertificateDto> getCertificateChain(String uuid, boolean withEndCertificate, boolean onlyCompleteChain) throws NotFoundException {
		Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(uuid));
		List<CertificateDto> certificateChain = new ArrayList<>();
		if (withEndCertificate) certificateChain.add(certificate.mapToDto());
		certificateChain.addAll(certificateService.getCertificateChain(certificate));
		return certificateChain;
	}

	@Override
	public String downloadCertificateChain(String uuid, CertificateFormat certificateFormat, boolean withEndCertificate, boolean onlyCompleteChain) throws NotFoundException, CertificateException, IOException {
		List<CertificateDto> certificateChain = getCertificateChain(uuid, withEndCertificate, onlyCompleteChain);
		StringBuilder certificateChainEncoded = new StringBuilder();
		for (CertificateDto certificateDto: certificateChain) {
			Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateDto.getUuid()));
			X509Certificate certificateX509 = CertificateUtil.getX509Certificate(certificate.getCertificateContent().getContent());
			if (Objects.equals(certificateFormat, CertificateFormat.PEM)){
				certificateChainEncoded.append(CertificateUtil.getBase64EncodedPEM(certificateX509));
			} else if (Objects.equals(certificateFormat, CertificateFormat.PKCS7)) {
				certificateChainEncoded.append(CertificateUtil.getBase64EncodedPKCS7(certificateX509));
			}
		}
		return certificateChainEncoded.toString();
	}

	@Override
	public ApprovalResponseDto listCertificateApprovals(final String uuid, final PaginationRequestDto paginationRequestDto) {
		return approvalService.listApprovalsByObject(SecurityFilter.create(), Resource.CERTIFICATE, UUID.fromString(uuid), paginationRequestDto);
	}

	// SETTERs

	@Autowired
	public void setCertificateService(CertificateService certificateService) {
		this.certificateService = certificateService;
	}

	@Autowired
	public void setCertValidationService(CertValidationService certValidationService) {
		this.certValidationService = certValidationService;
	}

	@Autowired
	public void setCertificateEventHistoryService(CertificateEventHistoryService certificateEventHistoryService) {
		this.certificateEventHistoryService = certificateEventHistoryService;
	}

	@Autowired
	public void setClientOperationService(ClientOperationService clientOperationService) {
		this.clientOperationService = clientOperationService;
	}

	@Autowired
	public void setApprovalService(ApprovalService approvalService) {
		this.approvalService = approvalService;
	}
}