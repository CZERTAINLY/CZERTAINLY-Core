package com.otilm.core.mapper.discovery;

import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.client.discovery.DiscoveryListDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryMessageDto;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryMessage;
import com.otilm.core.dao.entity.workflows.Trigger;
import java.util.List;

/** Maps a discovery run and its message log onto the shapes the API publishes. */
public class DiscoveryDtoMapper {

    private DiscoveryDtoMapper() {
    }

    /**
     * @param runMessageCount how many kinds of problem the run collected, which the entity cannot reach — the log is a
     * table of its own, and the field is REQUIRED on the wire, so callers read it rather than assume it
     */
    public static DiscoveryDetailDto toDetailDto(Discovery discovery, long runMessageCount) {
        DiscoveryDetailDto dto = new DiscoveryDetailDto();
        dto.setUuid(discovery.getUuid().toString());
        dto.setName(discovery.getName());
        dto.setEndTime(discovery.getEndTime());
        dto.setStartTime(discovery.getStartTime());
        dto.setTotalCertificatesDiscovered(discovery.getTotalCertificatesDiscovered());
        dto.setStatus(discovery.getStatus());
        dto.setConnectorUuid(discovery.getConnectorUuid().toString());
        dto.setKind(discovery.getKind());
        dto.setMessage(discovery.getMessage());
        dto.setConnectorName(discovery.getConnectorName());
        dto.setTriggers(discovery.getTriggers().stream().map(Trigger::mapToDto).toList());
        dto.setConnectorStatus(discovery.getConnectorStatus());
        dto.setConnectorTotalCertificatesDiscovered(discovery.getConnectorTotalCertificatesDiscovered());
        // Counted rather than carried: a client polls this detail while a run is live, and the log is read from
        // its own endpoint. Kinds of problem, not occurrences -- the count is what the listing would return.
        dto.setRunMessageCount(runMessageCount);
        // Both are REQUIRED on the wire, and a v1 run stores neither: it targets certificates by definition and
        // cannot be stopped at all, so the synthesis below is exact rather than a default.
        dto
                .setResources(discovery.getResources() == null || discovery.getResources().isEmpty()
                        ? List.of(Resource.CERTIFICATE)
                        : List.copyOf(discovery.getResources()));
        dto.setStoppable(Boolean.TRUE.equals(discovery.getStoppable()));
        // Omitted rather than defaulted when absent: null is what a v1 run, and a connector that reports no
        // progress at all, are meant to publish.
        dto.setProgress(discovery.getProgress());
        return dto;
    }

    public static DiscoveryListDto toListDto(Discovery discovery) {
        DiscoveryListDto dto = new DiscoveryListDto();
        dto.setUuid(discovery.getUuid().toString());
        dto.setName(discovery.getName());
        dto.setEndTime(discovery.getEndTime());
        dto.setStartTime(discovery.getStartTime());
        dto.setTotalCertificatesDiscovered(discovery.getTotalCertificatesDiscovered());
        dto.setStatus(discovery.getStatus());
        dto.setConnectorUuid(discovery.getConnectorUuid().toString());
        dto.setKind(discovery.getKind());
        dto.setConnectorName(discovery.getConnectorName());
        return dto;
    }

    /** The identity column stays behind: it orders the log, it is not published. */
    public static DiscoveryMessageDto toMessageDto(DiscoveryMessage message) {
        return new DiscoveryMessageDto(message.getSeverity(), message.getCode(), message.getMessage(),
                message.getOccurrences(), message.getFirstSeenAt(), message.getLastSeenAt());
    }
}
