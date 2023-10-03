package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CertificateController;
import com.czertainly.api.model.client.approval.ApprovalResponseDto;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.v2.ClientCertificateRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalService;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.converter.CertificateFormatConverter;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.StringWriter;
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
	public CertificateChainResponseDto getCertificateChain(String uuid, boolean withEndCertificate) throws NotFoundException {
		Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(uuid));
		CertificateChainResponseDto certificateChainResponseDto = certificateService.getCertificateChain(certificate);
		if (withEndCertificate) {
			List<CertificateDetailDto> certificateChain = certificateChainResponseDto.getCertificates();
			certificateChain.add(0, certificate.mapToDto());
		}
		return certificateChainResponseDto;
	}

	@Override
	public CertificateChainDownloadResponseDto downloadCertificateChain(String uuid, CertificateFormat certificateFormat, boolean withEndCertificate) throws NotFoundException, CertificateException {
		CertificateChainResponseDto certificateChainResponseDto = getCertificateChain(uuid, withEndCertificate);
		List<CertificateDetailDto> certificateChain = certificateChainResponseDto.getCertificates();
		CertificateChainDownloadResponseDto certificateChainDownloadResponseDto = new CertificateChainDownloadResponseDto();
		certificateChainDownloadResponseDto.setCompleteChain(certificateChainResponseDto.isCompleteChain());
		certificateChainDownloadResponseDto.setFormat(certificateFormat);
		StringWriter certificateChainEncoded = new StringWriter();
		JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(certificateChainEncoded);
		List<X509Certificate> caCertificateChain = new ArrayList<>();
			for (CertificateDto certificateDto : certificateChain) {
				Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateDto.getUuid()));
				X509Certificate x509Certificate = CertificateUtil.getX509Certificate(certificate.getCertificateContent().getContent());
				if (certificateFormat == CertificateFormat.PEM) {
					try {
						jcaPEMWriter.writeObject(x509Certificate);
						jcaPEMWriter.flush();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				} else {
					caCertificateChain.add(x509Certificate);
				}
			}

			if (certificateFormat == CertificateFormat.PKCS7) {
				certificateChainEncoded.append("-----BEGIN PKCS7-----\n");
				CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
				byte[] encoded;
				try {
					generator.addCertificates(new JcaCertStore(caCertificateChain));
					encoded = generator.generate(new CMSProcessableByteArray(new byte[0])).getEncoded();
				} catch (IOException | CMSException e) {
					throw new RuntimeException(e);
				}
				certificateChainEncoded.append(Base64.getEncoder().encodeToString(encoded));
				certificateChainEncoded.append("-----END PKCS7-----");
			}
		certificateChainDownloadResponseDto.setContent(certificateChainEncoded.toString());
		return certificateChainDownloadResponseDto;
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