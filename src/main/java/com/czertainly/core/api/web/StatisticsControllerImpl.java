package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.StatisticsController;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
import com.czertainly.core.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsControllerImpl implements StatisticsController{
	
	@Autowired
	private StatisticsService statisticsService;
	
	@Override
	@AuthEndpoint(resourceName = ResourceName.DASHBOARD, actionName = ActionName.DETAIL)
	public StatisticsDto getStatistics() {
		return statisticsService.getStatistics();
	}
}
