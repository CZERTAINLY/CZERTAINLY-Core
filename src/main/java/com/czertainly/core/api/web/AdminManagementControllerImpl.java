package com.czertainly.core.api.web;

import java.net.URI;
import java.security.cert.CertificateException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.czertainly.core.service.AdminService;
import com.czertainly.api.core.interfaces.web.AdminManagementController;
import com.czertainly.api.core.modal.AddAdminRequestDto;
import com.czertainly.api.core.modal.AdminDto;
import com.czertainly.api.core.modal.EditAdminRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;

@RestController
public class AdminManagementControllerImpl implements AdminManagementController {

	@Autowired
	private AdminService adminService;

	@Override
	public List<AdminDto> listAdmins() {
		return adminService.listAdmins();
	}

	@Override
	public ResponseEntity<?> addAdmin(@RequestBody AddAdminRequestDto request)
			throws CertificateException, AlreadyExistException, ValidationException, NotFoundException {
		AdminDto adminDTO = adminService.addAdmin(request);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
				.buildAndExpand(adminDTO.getUuid()).toUri();

		return ResponseEntity.created(location).build();
	}

	@Override
	public AdminDto getAdmin(@PathVariable String uuid) throws NotFoundException {
		return adminService.getAdminByUuid(uuid);
	}

	@Override
	public AdminDto editAdmin(@PathVariable String uuid, @RequestBody EditAdminRequestDto request)
			throws CertificateException, NotFoundException, AlreadyExistException {
		return adminService.editAdmin(uuid, request);
	}

	@Override
	public void bulkRemoveAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkRemoveAdmin(adminUuids);
	}

	@Override
	public void removeAdmin(@PathVariable String uuid) throws NotFoundException {
		adminService.removeAdmin(uuid);
	}

	@Override
	public void disableAdmin(@PathVariable String uuid) throws NotFoundException {
		adminService.disableAdmin(uuid);
	}

	@Override
	public void enableAdmin(@PathVariable String uuid) throws NotFoundException, CertificateException {
		adminService.enableAdmin(uuid);
	}

	@Override
	public void bulkDisableAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkDisableAdmin(adminUuids);
	}

	@Override
	public void bulkEnableAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkEnableAdmin(adminUuids);
	}
}
