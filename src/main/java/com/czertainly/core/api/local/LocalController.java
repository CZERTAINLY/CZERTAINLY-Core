package com.czertainly.core.api.local;

import com.czertainly.api.core.modal.AddAdminRequestDto;
import com.czertainly.api.core.modal.AdminDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.service.AdminService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.cert.CertificateException;

@RestController
@RequestMapping("/v1/local")
@Api(tags = {"Local API"}, description = "API only accessible from localhost")
public class LocalController {

    @Autowired
    private AdminService adminService;

    @ApiOperation(value = "Create Administrator")
    @RequestMapping(path = "/admins", method = RequestMethod.POST)
    public ResponseEntity<?> addAdmin(@RequestBody AddAdminRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException {
        AdminDto adminDTO = adminService.addAdmin(request);

        URI location = UriComponentsBuilder
                .fromPath("/v1/admins")
                .path("/{adminId}")
                .buildAndExpand(adminDTO.getId())
                .toUri();

        return ResponseEntity.created(location).build();
    }
}
