package com.czertainly.core.api;

import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.auth.EndpointsListener;
import com.czertainly.core.model.auth.SyncRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthSyncController {

    @Autowired
    EndpointsListener endpointsListener;

    @GetMapping(path = "/v1/auth/sync", produces = {"application/json"})
    public List<SyncRequestDto> getSyncEndpoints() {
        return endpointsListener.getEndpoints();
    }
}
