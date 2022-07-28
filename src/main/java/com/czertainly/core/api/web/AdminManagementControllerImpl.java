package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.AdminManagementController;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.client.admin.EditAdminRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.admin.AdminDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
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
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.LIST, isListingEndPoint = true)
	public List<AdminDto> listAdmins() {
		return adminService.listAdmins();
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.CREATE)
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
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.DETAIL)
	public AdminDto getAdmin(@PathVariable String uuid) throws NotFoundException {
		return adminService.getAdminByUuid(uuid);
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.UPDATE)
	public AdminDto editAdmin(@PathVariable String uuid, @RequestBody EditAdminRequestDto request)
			throws CertificateException, NotFoundException, AlreadyExistException {
		return adminService.editAdmin(uuid, request);
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.DELETE)
	public void bulkDeleteAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkDeleteAdmin(adminUuids);
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.DELETE)
	public void deleteAdmin(@PathVariable String uuid) throws NotFoundException {
		adminService.deleteAdmin(uuid);
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.DISABLE)
	public void disableAdmin(@PathVariable String uuid) throws NotFoundException {
		adminService.disableAdmin(uuid);
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.ENABLE)
	public void enableAdmin(@PathVariable String uuid) throws NotFoundException, CertificateException {
		adminService.enableAdmin(uuid);
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.DISABLE)
	public void bulkDisableAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkDisableAdmin(adminUuids);
	}

	@Override
	@AuthEndpoint(resourceName = ResourceName.ADMIN, actionName = ActionName.ENABLE)
	public void bulkEnableAdmin(List<String> adminUuids) throws NotFoundException {
		adminService.bulkEnableAdmin(adminUuids);
	}
}
