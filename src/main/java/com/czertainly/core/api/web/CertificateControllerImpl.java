package com.czertainly.core.api.web;

import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.List;

import com.czertainly.api.core.modal.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.czertainly.api.core.interfaces.web.CertificateController;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.discovery.CertificateDto;
import com.czertainly.api.model.discovery.CertificateStatus;

@RestController
public class CertificateControllerImpl implements CertificateController {

	@Autowired
	private CertificateService certificateService;

	@Autowired
	private CertValidationService certValidationService;

	@Override
	public List<CertificateDto> listCertificate(@RequestParam(required = false) Integer start,@RequestParam(required = false) Integer end) {
		return certificateService.listCertificates(start, end);
	}

	@Override
	public CertificateDto getCertificate(@PathVariable String uuid)
			throws NotFoundException, CertificateException, IOException {
		Certificate crt = certificateService.getCertificateEntity(uuid);
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
	public void updateRaProfile(@PathVariable String uuid, @RequestBody UuidDto request)
			throws NotFoundException {
		certificateService.updateRaProfile(uuid, request);
	}

	@Override
	public void updateCertificateGroup(@PathVariable String uuid,
			@RequestBody UuidDto request) throws NotFoundException {
		certificateService.updateCertificateGroup(uuid, request);
	}

	@Override
	public void updateEntity(@PathVariable String uuid, @RequestBody UuidDto request)
			throws NotFoundException {
		certificateService.updateEntity(uuid, request);
	}

	@Override
	public void updateOwner(@PathVariable String uuid, @RequestBody CertificateOwnerRequestDto request)
			throws NotFoundException {
		certificateService.updateOwner(uuid, request);
	}

	@Override
	public ResponseEntity<String> check(@PathVariable String uuid)
			throws CertificateException, IOException, NotFoundException {
		Certificate crt = certificateService.getCertificateEntity(uuid);
		certValidationService.validate(crt);
		return new ResponseEntity<String>("Updated", HttpStatus.OK);
	}

	// -------------------- BulkUpdate APIs -------------------
	@Override
	public void bulkUpdateRaProfile(@RequestBody IdAndCertificateIdDto request)
			throws NotFoundException {
		certificateService.bulkUpdateRaProfile(request);
	}

	@Override
	public void bulkUpdateCertificateGroup(@RequestBody IdAndCertificateIdDto request)
			throws NotFoundException {
		certificateService.bulkUpdateCertificateGroup(request);
	}

	@Override
	public void bulkUpdateEntity(@RequestBody IdAndCertificateIdDto request)
			throws NotFoundException {
		certificateService.bulkUpdateEntity(request);
	}

	@Override
	public void bulkUpdateOwner(@RequestBody CertificateOwnerBulkUpdateDto request)
			throws NotFoundException {
		certificateService.bulkUpdateOwner(request);
	}
	// ------------------- /Bulk Update API -----------------------

	@Override
	public ResponseEntity<?> upload(@RequestBody UploadCertificateRequestDto request)
			throws AlreadyExistException, CertificateException {
		CertificateDto dto = certificateService.upload(request);
		URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(dto.getUuid())
                .toUri();

        return ResponseEntity.created(location).build();
	}

	@Override
	public void bulkRemoveCertificate(@RequestBody RemoveCertificateDto request) throws NotFoundException {
		certificateService.bulkRemoveCertificate(request);
	}

	@Override
	public void validateAllCertificate() {
		certValidationService.validateAllCertificates();
	}

}
