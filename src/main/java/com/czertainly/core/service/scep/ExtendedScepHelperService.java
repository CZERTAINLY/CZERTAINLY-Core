package com.czertainly.core.service.scep;

import com.czertainly.core.dao.entity.RaProfile;
import org.springframework.http.ResponseEntity;

public interface ExtendedScepHelperService {

    /**
     * Handles the get request from SCEP clients
     * @param profileName SCEP Profile Name or RA Profile Name
     * @param operation SCEP operation
     * @param message SCEP message
     * @return SCEP response to be sent
     */
    ResponseEntity<Object> handleGet(String profileName, String operation, String message);

    /**
     * Handles the get request from SCEP clients
     * @param profileName SCEP Profile Name or RA Profile Name
     * @param operation SCEP operation
     * @param message SCEP message
     * @return SCEP response to be sent
     */
    ResponseEntity<Object> handlePost(String profileName, String operation, byte[] message);
}
