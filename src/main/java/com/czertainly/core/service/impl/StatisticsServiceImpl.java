package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEntity;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.service.StatisticsService;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class StatisticsServiceImpl implements StatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsServiceImpl.class);

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private DiscoveryRepository discoveryRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.STATISTICS, operation = OperationType.REQUEST)
    public StatisticsDto getStatistics() {
        logger.info("Gathering the statistics information from database");

        StatisticsDto dto = new StatisticsDto();
        dto.setTotalCertificates(getCertificateCount());
        dto.setTotalDiscoveries(getDiscoveryCount());
        dto.setTotalGroups(getCertificateGroupCount());
        dto.setTotalEntities(getCertificateEntityCount());
        dto.setTotalRaProfiles(getRaProfileCount());
        dto.setEntityStatByCertificateCount(getEntityStatByCertificateCount(dto));
        dto.setGroupStatByCertificateCount(getGroupStatByCertificateCount(dto));
        dto.setRaProfileStatByCertificateCount(getRaProfileStatByCertificateCount(dto));
        dto.setCertificateStatByType(getCertificateStatByType(dto));
        dto.setCertificateStatByKeySize(getCertificateStatByKeySize(dto));
        dto.setCertificateStatByBasicConstraints(getCertificateStatByBasicConstraints(dto));
        dto.setCertificateStatByExpiry(getCertificatesByExpiry(dto));
        dto.setCertificateStatByStatus(getCertificateStatByStatus());

        return dto;
    }

    private long getCertificateCount() {
        return certificateRepository.count();
    }

    private long getDiscoveryCount() {
        return discoveryRepository.count();
    }

    private long getCertificateGroupCount() {
        return groupRepository.count();
    }

    private long getCertificateEntityCount() {
        return entityRepository.count();
    }

    private long getRaProfileCount() {
        return raProfileRepository.count();
    }

    private Map<String, Long> getGroupStatByCertificateCount(StatisticsDto dto) {
        List<Long> keys = new ArrayList<Long>();
        var result = certificateRepository.getCertificatesCountByGroup();
        for (Object[] item : result) keys.add((long)item[0]);
        var labels = certificateRepository.getGroupNamesWithIds(keys);

        return getStatsMap(result, labels, dto.getTotalCertificates(), "Unassigned");
    }

    private Map<String, Long> getEntityStatByCertificateCount(StatisticsDto dto) {
        List<Long> keys = new ArrayList<Long>();
        var result = certificateRepository.getCertificatesCountByEntity();
        for (Object[] item : result) keys.add((long)item[0]);
        var labels = certificateRepository.getEntityNamesWithIds(keys);

        return getStatsMap(result, labels, dto.getTotalCertificates(), "Unassigned");
    }

    private Map<String, Long> getRaProfileStatByCertificateCount(StatisticsDto dto) {
        List<Long> keys = new ArrayList<Long>();
        var result = certificateRepository.getCertificatesCountByRaProfile();
        for (Object[] item : result) keys.add((long)item[0]);
        var labels = certificateRepository.getRaProfileNamesWithIds(keys);

        return getStatsMap(result, labels, dto.getTotalCertificates(), "Unassigned");
    }

    private Map<String, Long> getCertificateStatByType(StatisticsDto dto) {
        var result = certificateRepository.getCertificatesCountByType();

        return getStatsMap(result, null, dto.getTotalCertificates(), "Unknown");
    }

    private Map<String, Long> getCertificateStatByKeySize(StatisticsDto dto) {
        var result = certificateRepository.getCertificatesCountByKeySize();

        return getStatsMap(result, null, dto.getTotalCertificates(), "Unknown");
    }

    private Map<String, Long> getCertificateStatByBasicConstraints(StatisticsDto dto) {
        var result = certificateRepository.getCertificatesCountByBasicConstraints();

        return getStatsMap(result, null, dto.getTotalCertificates(), "Unknown");
    }
    
    private Map<String, Long> getCertificateStatByStatus() {
        var result = certificateRepository.getCertificatesCountByStatus();

        return getStatsMap(result, null, 0, null);
    }

    private Map<String, Long> getCertificatesByExpiry(StatisticsDto dto) {
        long totalStatsCount = 0;
        Map<String, Long> stats = new HashMap<>();

        LocalDateTime today =  LocalDateTime.now();
        LocalDateTime notBeforeTo = today;
        LocalDateTime notBeforeFrom = LocalDateTime.now();
        int[] expiryInDays = { 10, 20, 30, 60, 90 };
        for (Integer days: expiryInDays) {
            notBeforeTo = today.plusDays(days);
            var result = certificateRepository.getCertificatesCountByExpiryDate(java.sql.Timestamp.valueOf(notBeforeFrom), java.sql.Timestamp.valueOf(notBeforeTo));
            totalStatsCount += (long) result.get(0)[0];
            stats.put(days.toString(), (Long) result.get(0)[0]);
            notBeforeFrom = notBeforeTo;
        }
        stats.put("More", dto.getTotalCertificates() - totalStatsCount);

        return stats;
    }

    private Map<String, Long> getStatsMap(List<Object[]> resultStats, List<Object[]> resultLabels, long totalCount, String defaultLabel) {
        long totalStatsCount = 0;
        Map<String, Long> stats = new HashMap<>();

        if(resultLabels == null) {
            for (Object[] item : resultStats) {
                totalStatsCount += (long) item[1];
                stats.put(item[0].toString(), (long) item[1]);
            }
        }
        else {
            Map<Long, String> labels = new HashMap<>();
            for (Object[] item : resultLabels) labels.put((long) item[0], item[1].toString());
            for (Object[] item : resultStats) {
                totalStatsCount += (long) item[1];
                stats.put(labels.get((long) item[0]), (long) item[1]);
            }
        }

        if(defaultLabel != null) stats.put(defaultLabel, totalCount - totalStatsCount);
        return stats;
    }
}
