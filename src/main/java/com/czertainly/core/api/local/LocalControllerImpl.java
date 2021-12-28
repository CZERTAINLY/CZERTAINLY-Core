package com.czertainly.core.api.local;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.local.LocalController;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.core.admin.AdminDto;
import com.czertainly.core.service.LocalAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.cert.CertificateException;

@RestController
public class LocalControllerImpl implements LocalController {

    @Autowired
    private LocalAdminService localAdminService;

    @Override
    public ResponseEntity<?> addAdmin(@RequestBody AddAdminRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException {
        AdminDto adminDTO = localAdminService.addAdmin(request);

        URI location = UriComponentsBuilder
                .fromPath("/v1/admins")
                .path("/{adminId}")
                .buildAndExpand(adminDTO.getId())
                .toUri();

        return ResponseEntity.created(location).build();
    }
}
