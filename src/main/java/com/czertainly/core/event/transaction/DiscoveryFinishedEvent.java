package com.czertainly.core.event.transaction;

import java.util.UUID;

public record DiscoveryFinishedEvent(UUID discoveryUuid, UUID loggedUserUuid) {
}
