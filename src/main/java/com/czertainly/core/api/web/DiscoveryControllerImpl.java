package com.czertainly.core.api.web;

import java.security.cert.CertificateException;
import java.util.List;

import com.czertainly.api.exception.ConnectorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.api.core.interfaces.web.DiscoveryController;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.discovery.DiscoveryDto;
import com.czertainly.api.model.discovery.DiscoveryHistoryDto;

@RestController
public class DiscoveryControllerImpl implements DiscoveryController {

	@Autowired
	private DiscoveryService discoveryService;

	@Override
	public List<DiscoveryHistoryDto> listDiscovery() {
		return discoveryService.listDiscovery();
	}

	@Override
	public DiscoveryHistoryDto getDiscovery(@PathVariable String uuid) throws NotFoundException {
		return discoveryService.getDiscovery(uuid);
	}

	@Override
	public ResponseEntity<?> createDiscovery(@RequestBody DiscoveryDto request)
            throws NotFoundException, ConnectorException, AlreadyExistException {
		DiscoveryHistory modal = discoveryService.createDiscoveryModal(request);
		discoveryService.createDiscovery(request, modal);
		return new ResponseEntity<String>("", HttpStatus.CREATED);
	}

	@Override
	public void removeDiscovery(@PathVariable String uuid) throws NotFoundException {
		discoveryService.removeDiscovery(uuid);
	}

	@Override
	public void bulkRemoveDiscovery(List<String> discoveryUuids) throws NotFoundException {
		discoveryService.bulkRemoveDiscovery(discoveryUuids);
	}
}
