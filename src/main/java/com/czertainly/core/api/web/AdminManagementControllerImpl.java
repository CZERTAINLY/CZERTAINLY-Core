package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.AdminManagementController;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.client.admin.EditAdminRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.admin.AdminDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.cert.CertificateException;
import java.util.List;

@RestController
public class AdminManagementControllerImpl implements AdminManagementController {

	@Autowired
	private AdminService adminService;

	@Override
	public List<AdminDto> listAdmins() {
		return adminService.listAdmins(SecurityFilter.create());
	}

	@Override
	public ResponseEntity<?> createAdmin(@RequestBody AddAdminRequestDto request)
			throws CertificateException, AlreadyExistException, ValidationException, NotFoundException {
		AdminDto adminDTO = adminService.addAdmin(request);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
				.buildAndExpand(adminDTO.getUuid()).toUri();

		UuidDto dto = new UuidDto();
		dto.setUuid(adminDTO.getUuid());
		return ResponseEntity.created(location).body(dto);
	}

	@Override
	public AdminDto getAdmin(@PathVariable String uuid) throws NotFoundException {
		return adminService.getAdminByUuid(SecuredUUID.fromString(uuid));
	}

	@Override
	public AdminDto editAdmin(@PathVariable String uuid, @RequestBody EditAdminRequestDto request)
			throws CertificateException, NotFoundException, AlreadyExistException {
		return adminService.editAdmin(SecuredUUID.fromString(uuid), request);
	}

	@Override
	public void bulkDeleteAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkDeleteAdmin(SecuredUUID.fromList(adminUuids));
	}

	@Override
	public void deleteAdmin(@PathVariable String uuid) throws NotFoundException {
		adminService.deleteAdmin(SecuredUUID.fromString(uuid));
	}

	@Override
	public void disableAdmin(@PathVariable String uuid) throws NotFoundException {
		adminService.disableAdmin(SecuredUUID.fromString(uuid));
	}

	@Override
	public void enableAdmin(@PathVariable String uuid) throws NotFoundException, CertificateException {
		adminService.enableAdmin(SecuredUUID.fromString(uuid));
	}

	@Override
	public void bulkDisableAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkDisableAdmin(SecuredUUID.fromList(adminUuids));
	}

	@Override
	public void bulkEnableAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkEnableAdmin(SecuredUUID.fromList(adminUuids));
	}
}
