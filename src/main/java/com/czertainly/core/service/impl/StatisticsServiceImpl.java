package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private RaProfileRepository raProfileRepository;


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.STATISTICS, operation = OperationType.REQUEST)
    public StatisticsDto getStatistics() {
        logger.info("Gathering the statistics information from database");

        StatisticsDto dto = new StatisticsDto();
        dto.setTotalCertificates(getCertificateCount());
        dto.setTotalDiscoveries(getDiscoveryCount());
        dto.setTotalGroups(getCertificateGroupCount());
        dto.setTotalRaProfiles(getRaProfileCount());
        dto.setGroupStatByCertificateCount(getGroupStatByCertificateCount(dto));
        dto.setRaProfileStatByCertificateCount(getRaProfileStatByCertificateCount(dto));
        dto.setCertificateStatByType(getCertificateStatByType(dto));
        dto.setCertificateStatByKeySize(getCertificateStatByKeySize(dto));
        dto.setCertificateStatByBasicConstraints(getCertificateStatByBasicConstraints(dto));
        dto.setCertificateStatByExpiry(getCertificatesByExpiry(dto));
        dto.setCertificateStatByStatus(getCertificateStatByStatus());
        dto.setCertificateStatByComplianceStatus(getCertificateStatByComplianceStatus());

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

    private long getRaProfileCount() {
        return raProfileRepository.count();
    }

    private Map<String, Long> getGroupStatByCertificateCount(StatisticsDto dto) {
        List<String> keys = new ArrayList<>();
        var result = certificateRepository.getCertificatesCountByGroup();
        for (Object[] item : result) keys.add((String) item[0]);
        var labels = certificateRepository.getGroupNamesWithUuids(keys);

        return getStatsMap(result, labels, dto.getTotalCertificates(), "Unassigned");
    }

    private Map<String, Long> getRaProfileStatByCertificateCount(StatisticsDto dto) {
        List<String> keys = new ArrayList<>();
        var result = certificateRepository.getCertificatesCountByRaProfile();
        for (Object[] item : result) keys.add((String) item[0]);
        var labels = certificateRepository.getRaProfileNamesWithUuids(keys);

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

    private Map<String, Long> getCertificateStatByComplianceStatus() {
        var result = certificateRepository.getCertificatesCountByComplianceStatus();

        return getComplianceStatsMap(result);
    }

    private Map<String, Long> getCertificatesByExpiry(StatisticsDto dto) {
        long totalStatsCount = 0;
        Map<String, Long> stats = new HashMap<>();

        LocalDateTime today = LocalDateTime.now();
        LocalDateTime notBeforeTo = today;
        LocalDateTime notBeforeFrom = LocalDateTime.now();
        int[] expiryInDays = {10, 20, 30, 60, 90};
        for (Integer days : expiryInDays) {
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

        if (resultLabels == null) {
            for (Object[] item : resultStats) {
                totalStatsCount += (long) item[1];
                stats.put(item[0].toString(), (long) item[1]);
            }
        } else {
            Map<String, String> labels = new HashMap<>();
            for (Object[] item : resultLabels) labels.put((String) item[0], item[1].toString());
            for (Object[] item : resultStats) {
                totalStatsCount += (long) item[1];
                stats.put(labels.get((String) item[0]), (long) item[1]);
            }
        }

        if (defaultLabel != null) stats.put(defaultLabel, totalCount - totalStatsCount);
        return stats;
    }

    private Map<String, Long> getComplianceStatsMap(List<Object[]> resultStats) {
        Map<String, Long> stats = new HashMap<>();
        Map<String, String> beToUiMapping = new HashMap<>();
        beToUiMapping.put("NA", "Not Checked");
        beToUiMapping.put("NOK", "Non Compliant");
        beToUiMapping.put("OK", "Compliant");
        for (Object[] item : resultStats) {
            if(item[0] == null){
                stats.put(beToUiMapping.get("NA"), (long) item[1]);
                continue;
            }
            stats.put(beToUiMapping.getOrDefault(item[0].toString(), "Unknown"), (long) item[1]);
        }
        return stats;
    }
}
