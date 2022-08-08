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
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.Resource;
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
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.LIST, isListingEndPoint = true)
	public CertificateResponseDto listCertificates(SearchRequestDto request) throws ValidationException {
		return certificateService.listCertificates(request);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.DETAIL)
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
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.DELETE)
	public void deleteCertificate(@PathVariable String uuid) throws NotFoundException {
		certificateService.deleteCertificate(uuid);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.UPDATE_RA_PROFILE)
	public void updateRaProfile(@PathVariable String uuid, @RequestBody CertificateUpdateRAProfileDto request)
			throws NotFoundException {
		certificateService.updateRaProfile(uuid, request);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.UPDATE_GROUP)
	public void updateCertificateGroup(@PathVariable String uuid,
			@RequestBody CertificateUpdateGroupDto request) throws NotFoundException {
		certificateService.updateCertificateGroup(uuid, request);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.UPDATE_OWNER)
	public void updateOwner(@PathVariable String uuid, @RequestBody CertificateOwnerRequestDto request)
			throws NotFoundException {
		certificateService.updateOwner(uuid, request);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.VALIDATE)
	public void check(@PathVariable String uuid)
			throws CertificateException, IOException, NotFoundException {
		Certificate crt = certificateService.getCertificateEntity(uuid);
		certValidationService.validate(crt);
	}

	// -------------------- BulkUpdate APIs -------------------
	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.UPDATE_RA_PROFILE)
	public void bulkUpdateRaProfile(@RequestBody MultipleRAProfileUpdateDto request)
			throws NotFoundException {
		certificateService.bulkUpdateRaProfile(request);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.UPDATE_GROUP)
	public void bulkUpdateCertificateGroup(@RequestBody MultipleGroupUpdateDto request)
			throws NotFoundException {
		certificateService.bulkUpdateCertificateGroup(request);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.UPDATE_OWNER)
	public void bulkUpdateOwner(@RequestBody CertificateOwnerBulkUpdateDto request)
			throws NotFoundException {
		certificateService.bulkUpdateOwner(request);
	}
	// ------------------- /Bulk Update API -----------------------

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.UPLOAD)
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
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.DELETE)
	public BulkOperationResponse bulkDeleteCertificate(@RequestBody RemoveCertificateDto request) throws NotFoundException {
		certificateService.bulkDeleteCertificate(request);
		BulkOperationResponse response = new BulkOperationResponse();
		response.setMessage("Initiated bulk delete Certificates. Please refresh after some time");
		response.setStatus(BulkOperationStatus.SUCCESS);
		return response;
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.VALIDATE)
	public void validateAllCertificate() {
		certValidationService.validateAllCertificates();
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.NONE, isListingEndPoint = true)
	public List<SearchFieldDataDto> getSearchableFieldInformation() {
		return certificateService.getSearchableFieldInformation();
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.LIST_EVENT_HISTORY)
	public List<CertificateEventHistoryDto> getCertificateEventHistory(String uuid) throws NotFoundException{
		return certificateEventHistoryService.getCertificateEventHistory(uuid);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.LIST_LOCATION)
	public List<LocationDto> listLocations(String certificateUuid) throws NotFoundException {
		return certificateService.listLocations(certificateUuid);
	}

	@Override
	@AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.CHECK_COMPLIANCE)
	public void checkCompliance(CertificateComplianceCheckDto request) throws NotFoundException {
		certificateService.checkCompliance(request);
	}

}