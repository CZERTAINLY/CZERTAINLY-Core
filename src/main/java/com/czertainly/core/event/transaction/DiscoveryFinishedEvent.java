package com.czertainly.core.event.transaction;

import com.czertainly.core.tasks.ScheduledJobInfo;

import java.util.UUID;

public record DiscoveryFinishedEvent(UUID discoveryUuid, UUID loggedUserUuid, ScheduledJobInfo scheduledJobInfo) {
}
