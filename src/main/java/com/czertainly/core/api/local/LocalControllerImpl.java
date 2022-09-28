package com.czertainly.core.api.local;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.local.LocalController;
import com.czertainly.api.model.core.auth.AddUserRequestDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.core.service.LocalAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@RestController
public class LocalControllerImpl implements LocalController {


    @Autowired
    private LocalAdminService localAdminService;

    @Override
    public ResponseEntity<UserDetailDto> addAdmin(@RequestBody AddUserRequestDto request) throws NotFoundException, CertificateException, NoSuchAlgorithmException, AlreadyExistException {
        UserDetailDto userDto = localAdminService.createUser(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(userDto.getUuid())
                .toUri();
        return ResponseEntity.created(location).body(userDto);
    }
}
