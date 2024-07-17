package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.core.service.GroupService;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class StatisticsServiceImpl implements StatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsServiceImpl.class);

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RaProfileService raProfileService;


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.STATISTICS, operation = OperationType.REQUEST)
    public StatisticsDto getStatistics() {
        logger.info("Gathering the statistics information from database");
        StatisticsDto dto = new StatisticsDto();

        dto.setTotalCertificates(certificateService.statisticsCertificateCount(SecurityFilter.create()));
        logger.debug("Stats:statisticsCertificateCount");
        try {
            dto.setTotalDiscoveries(discoveryService.statisticsDiscoveryCount(SecurityFilter.create()));
            logger.debug("Stats:statisticsDiscoveryCount");
        } catch (AccessDeniedException e) {
            dto.setTotalDiscoveries(0L);
        }
        try {
            dto.setTotalGroups(groupService.statisticsGroupCount(SecurityFilter.create()));
            logger.debug("Stats:statisticsGroupCount");
        } catch (AccessDeniedException e) {
            dto.setTotalGroups(0L);
        }
        try {
            dto.setTotalRaProfiles(raProfileService.statisticsRaProfilesCount(SecurityFilter.create()));
            logger.debug("Stats:statisticsRaProfilesCount");
        } catch (AccessDeniedException e){
            dto.setTotalRaProfiles(0L);
        }
        var resultStats = certificateService.addCertificateStatistics(SecurityFilter.create(), dto);
        logger.debug("Stats:addCertificateStatistics");
        return resultStats;
    }
}
