package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.StatisticsController;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsControllerImpl implements StatisticsController{
	
	@Autowired
	private StatisticsService statisticsService;
	
	@Override
	@AuthEndpoint(resourceName = Resource.DASHBOARD, actionName = ResourceAction.DETAIL)
	public StatisticsDto getStatistics() {
		return statisticsService.getStatistics();
	}
}
