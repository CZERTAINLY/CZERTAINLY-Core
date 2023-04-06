package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.DiscoveryController;
import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class DiscoveryControllerImpl implements DiscoveryController {

	@Autowired
	private DiscoveryService discoveryService;

	@Override
	public DiscoveryResponseDto listDiscoveries(final SearchRequestDto request) {
		return discoveryService.listDiscoveries(SecurityFilter.create(), request);
	}

	@Override
	public DiscoveryHistoryDetailDto getDiscovery(@PathVariable String uuid) throws NotFoundException {
		return discoveryService.getDiscovery(SecuredUUID.fromString(uuid));
	}

	@Override
	public DiscoveryCertificateResponseDto getDiscoveryCertificates(
			String uuid,
			Boolean newlyDiscovered,
			int itemsPerPage,
			int pageNumber
	) throws NotFoundException {
		return discoveryService.getDiscoveryCertificates(
				SecuredUUID.fromString(uuid),
				newlyDiscovered,
				itemsPerPage,
				pageNumber
		);
	}

	@Override
	public ResponseEntity<?> createDiscovery(@RequestBody DiscoveryDto request)
            throws NotFoundException, ConnectorException, AlreadyExistException {
		DiscoveryHistory modal = discoveryService.createDiscoveryModal(request);
		discoveryService.createDiscovery(request, modal);
		URI location = ServletUriComponentsBuilder
				.fromCurrentRequest()
				.path("/{uuid}")
				.buildAndExpand(modal.getUuid())
				.toUri();
		UuidDto dto = new UuidDto();
		dto.setUuid(modal.getUuid().toString());
		return ResponseEntity.created(location).body(dto);
	}

	@Override
	public void deleteDiscovery(@PathVariable String uuid) throws NotFoundException {
		discoveryService.deleteDiscovery(SecuredUUID.fromString(uuid));
	}

	@Override
	public void bulkDeleteDiscovery(List<String> discoveryUuids) throws NotFoundException {
		discoveryService.bulkRemoveDiscovery(SecuredUUID.fromList(discoveryUuids));
	}

	@Override
	public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
		return discoveryService.getSearchableFieldInformationByGroup();
	}
}
