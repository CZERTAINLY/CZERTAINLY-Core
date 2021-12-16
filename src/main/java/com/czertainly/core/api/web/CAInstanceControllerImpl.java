package com.czertainly.core.api.web;

import java.net.URI;
import java.util.List;

import com.czertainly.api.core.modal.UuidDto;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.ca.CAInstanceRequestDto;
import com.czertainly.api.model.connector.ForceDeleteMessageDto;
import com.czertainly.core.service.CAInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.czertainly.api.core.interfaces.web.CAInstanceController;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.AttributeCallback;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.ca.CAInstanceDto;
import com.czertainly.api.model.NameAndIdDto;

@RestController
public class CAInstanceControllerImpl implements CAInstanceController{

    @Autowired
    private CAInstanceService caInstanceService;

    @Override
    public List<CAInstanceDto> listCAInstances() {
        return caInstanceService.listCAInstances();
    }

    @Override
    public CAInstanceDto getCAInstance(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return caInstanceService.getCAInstance(uuid);
    }

    @Override
    public ResponseEntity<?> createCAInstance(@RequestBody CAInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException {
        CAInstanceDto caInstance = caInstanceService.createCAInstance(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(caInstance.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(caInstance.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public CAInstanceDto updateCAInstance(@PathVariable String uuid, @RequestBody CAInstanceRequestDto request) throws NotFoundException, ConnectorException {
        return caInstanceService.updateCAInstance(uuid, request);
    }

    @Override
    public void removeCAInstance(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        caInstanceService.removeCAInstance(uuid);
    }

    @Override
    public List<NameAndIdDto> listEntityProfiles(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return caInstanceService.listEndEntityProfiles(uuid);
    }

    @Override
    public List<NameAndIdDto> listCertificateProfiles(@PathVariable String uuid, @PathVariable Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        return caInstanceService.listCertificateProfiles(uuid, endEntityProfileId);
    }

    @Override
    public List<NameAndIdDto> listCAsInProfile(@PathVariable String uuid, @PathVariable Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        return caInstanceService.listCAsInProfile(uuid, endEntityProfileId);
    }

    @Override
    public List<AttributeDefinition> listRAProfileAttributes(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return caInstanceService.listRAProfileAttributes(uuid);
    }

    @Override
    public Boolean validateRAProfileAttributes(@PathVariable String uuid, @RequestBody List<AttributeDefinition> attributes) throws NotFoundException, ConnectorException {
        return caInstanceService.validateRAProfileAttributes(uuid, attributes);
    }

    @Override
    public List<ForceDeleteMessageDto> bulkRemoveCaInstance(List<String> uuids) throws NotFoundException, ConnectorException, ValidationException {
        return caInstanceService.bulkRemoveCaInstance(uuids);
    }

    @Override
    public void bulkForceRemoveCaInstance(List<String> uuids) throws NotFoundException, ValidationException {
        caInstanceService.bulkForceRemoveCaInstance(uuids);
    }
}
