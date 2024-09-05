package com.czertainly.core.event.transaction;

import java.util.UUID;

public record DiscoveryProgressEvent(UUID discoveryUuid, int totalCount, boolean downloading) {
}
