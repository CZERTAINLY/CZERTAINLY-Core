package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CertificateController;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.certificate.BulkOperationStatus;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateValidationDto;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class CertificateControllerImpl implements CertificateController {

	@Autowired
	private CertificateService certificateService;

	@Autowired
	private CertValidationService certValidationService;

	@Autowired
	private CertificateEventHistoryService certificateEventHistoryService;

	@Override
	public CertificateResponseDto listCertificates(SearchRequestDto request) throws ValidationException {
		return certificateService.listCertificates(SecurityFilter.create(), request);
	}

	@Override
	public CertificateDto getCertificate(@PathVariable String uuid)
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
		certificateService.bulkUpdateCertificateObjects(request);
	}

	@Override
	public ResponseEntity<UuidDto> upload(@RequestBody UploadCertificateRequestDto request)
			throws AlreadyExistException, CertificateException {
		CertificateDto dto = certificateService.upload(request);
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
		certificateService.bulkDeleteCertificate(request);
		BulkOperationResponse response = new BulkOperationResponse();
		response.setMessage("Initiated bulk delete Certificates. Please refresh after some time");
		response.setStatus(BulkOperationStatus.SUCCESS);
		return response;
	}

	@Override
	public void validateAllCertificate() {
		certValidationService.validateAllCertificates();
	}

	@Override
	public List<SearchFieldDataDto> getSearchableFieldInformation() {
		return certificateService.getSearchableFieldInformation();
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
		Certificate crt = certificateService.getCertificateEntity(SecuredUUID.fromString(uuid));
		certificateService.updateIssuer();
		if (crt.getStatus() != CertificateStatus.EXPIRED || crt.getStatus() != CertificateStatus.REVOKED) {
			certValidationService.validate(crt);
		}
		return certificateService.getCertificateValidationResult(SecuredUUID.fromString(uuid));
	}

}