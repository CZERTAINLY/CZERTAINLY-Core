package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CertificateController;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerBulkUpdateDto;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.certificate.BulkOperationStatus;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.Certificate;
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

@RestController
public class CertificateControllerImpl implements CertificateController {

	@Autowired
	private CertificateService certificateService;

	@Autowired
	private CertValidationService certValidationService;

	@Autowired
	private CertificateEventHistoryService certificateEventHistoryService;

	@Override
	public CertificateResponseDto listCertificate(SearchRequestDto request) throws ValidationException {
		return certificateService.listCertificates(request);
	}

	@Override
	public CertificateDto getCertificate(@PathVariable String uuid)
			throws NotFoundException, CertificateException, IOException {
		Certificate crt = certificateService.getCertificateEntity(uuid);
		certificateService.updateIssuer();
		if (crt.getStatus() != CertificateStatus.EXPIRED || crt.getStatus() != CertificateStatus.REVOKED) {
			certValidationService.validate(crt);
		}
		return certificateService.getCertificate(uuid);
	}

	@Override
	public void removeCertificate(@PathVariable String uuid) throws NotFoundException {
		certificateService.removeCertificate(uuid);
	}

	@Override
	public void updateRaProfile(@PathVariable String uuid, @RequestBody CertificateUpdateRAProfileDto request)
			throws NotFoundException {
		certificateService.updateRaProfile(uuid, request);
	}

	@Override
	public void updateCertificateGroup(@PathVariable String uuid,
			@RequestBody CertificateUpdateGroupDto request) throws NotFoundException {
		certificateService.updateCertificateGroup(uuid, request);
	}

	@Override
	public void updateOwner(@PathVariable String uuid, @RequestBody CertificateOwnerRequestDto request)
			throws NotFoundException {
		certificateService.updateOwner(uuid, request);
	}

	@Override
	public void check(@PathVariable String uuid)
			throws CertificateException, IOException, NotFoundException {
		Certificate crt = certificateService.getCertificateEntity(uuid);
		certValidationService.validate(crt);
	}

	// -------------------- BulkUpdate APIs -------------------
	@Override
	public void bulkUpdateRaProfile(@RequestBody MultipleRAProfileUpdateDto request)
			throws NotFoundException {
		certificateService.bulkUpdateRaProfile(request);
	}

	@Override
	public void bulkUpdateCertificateGroup(@RequestBody MultipleGroupUpdateDto request)
			throws NotFoundException {
		certificateService.bulkUpdateCertificateGroup(request);
	}

	@Override
	public void bulkUpdateOwner(@RequestBody CertificateOwnerBulkUpdateDto request)
			throws NotFoundException {
		certificateService.bulkUpdateOwner(request);
	}
	// ------------------- /Bulk Update API -----------------------

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
	public BulkOperationResponse bulkRemoveCertificate(@RequestBody RemoveCertificateDto request) throws NotFoundException {
		certificateService.bulkRemoveCertificate(request);
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
		return certificateEventHistoryService.getCertificateEventHistory(uuid);
	}

	@Override
	public List<LocationDto> listLocations(String certificateUuid) throws NotFoundException {
		return certificateService.listLocations(certificateUuid);
	}

	@Override
	public void checkCompliance(CertificateComplianceCheckDto request) throws NotFoundException {
		certificateService.checkCompliance(request);
	}

}