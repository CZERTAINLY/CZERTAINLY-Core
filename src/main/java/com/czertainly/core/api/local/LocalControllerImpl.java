package com.czertainly.core.api.local;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.local.LocalController;
import com.czertainly.api.model.core.auth.AddUserRequestDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.core.service.UserManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.cert.CertificateException;

@RestController
public class LocalControllerImpl implements LocalController {

    private static final String AUTH_SUPER_ADMIN_ROLE_UUID = "d34f960b-75c9-4184-ba97-665d30a9ee8a";
    @Autowired
    private UserManagementService userManagementService;

    @Override
    public ResponseEntity<UserDetailDto> addAdmin(@RequestBody AddUserRequestDto request) throws NotFoundException, CertificateException {
        UserDetailDto userDto = userManagementService.createUser(request);
        userManagementService.updateRole(userDto.getUuid(), AUTH_SUPER_ADMIN_ROLE_UUID);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(userDto.getUuid())
                .toUri();
        return ResponseEntity.created(location).body(userDto);
    }
}
