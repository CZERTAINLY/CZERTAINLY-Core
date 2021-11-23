package com.czertainly.core.api.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.czertainly.core.service.StatisticsService;
import com.czertainly.api.core.interfaces.web.StatisticsController;
import com.czertainly.api.model.discovery.StatisticsDto;

@RestController
public class StatisticsControllerImpl implements StatisticsController{
	
	@Autowired
	private StatisticsService statisticsService;
	
	@Override
	public StatisticsDto getStatistics() {
		return statisticsService.getStatistics();
	}
}
