package com.czertainly.core.service;

import com.czertainly.api.model.client.dashboard.StatisticsDto;

public interface StatisticsService {
	StatisticsDto getStatistics(boolean includeArchived);
}
