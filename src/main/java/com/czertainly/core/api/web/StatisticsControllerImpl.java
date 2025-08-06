package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.StatisticsController;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsControllerImpl implements StatisticsController {

	private StatisticsService statisticsService;

	@Autowired
	public void setStatisticsService(StatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}
	
	@Override
	@AuditLogged(module = Module.CORE, resource = Resource.DASHBOARD, operation = Operation.STATISTICS)
	public StatisticsDto getStatistics(boolean includeArchived) {
		return statisticsService.getStatistics(includeArchived);
	}
}
