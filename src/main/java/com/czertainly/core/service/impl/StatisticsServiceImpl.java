package com.czertainly.core.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import com.czertainly.core.aop.AuditLogged;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEntity;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateEntityRepository;
import com.czertainly.core.dao.repository.CertificateGroupRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.StatisticsService;
import com.czertainly.api.model.discovery.CertificateStatus;
import com.czertainly.api.model.discovery.CertificateType;
import com.czertainly.api.model.discovery.StatisticsDto;

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
    private CertificateGroupRepository certificateGroupRepository;

    @Autowired
    private CertificateEntityRepository certificateEntityRepository;

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
        dto.setCertificateStatByExpiry(getCertificatesByExpiry());
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
        return certificateGroupRepository.count();
    }

    private long getCertificateEntityCount() {
        return certificateEntityRepository.count();
    }

    private long getRaProfileCount() {
        return raProfileRepository.count();
    }

    private Map<String, Long> getGroupStatByCertificateCount(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (CertificateGroup group : certificateGroupRepository.findAll()) {
            Integer statSize = certificateRepository.findDistinctByGroupId(group.getId()).size();
            totalStatCount += statSize;
            stat.put(group.getName(), (long) statSize);
        }
        stat.put("Unassigned", dto.getTotalCertificates() - totalStatCount);
        return stat;
    }

    private Map<String, Long> getEntityStatByCertificateCount(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (CertificateEntity entity : certificateEntityRepository.findAll()) {
            Integer statSize = certificateRepository.findDistinctByEntityId(entity.getId()).size();
            totalStatCount += statSize;
            stat.put(entity.getName(), (long) statSize);
        }
        stat.put("Unassigned", dto.getTotalCertificates() - totalStatCount);
        return stat;
    }

    private Map<String, Long> getRaProfileStatByCertificateCount(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (RaProfile profile : raProfileRepository.findAll()) {
            Integer statSize = certificateRepository.findDistinctByRaProfileId(profile.getId()).size();
            totalStatCount += statSize;
            stat.put(profile.getName(), (long) statSize);
        }
        stat.put("Unassigned", dto.getTotalCertificates() - totalStatCount);
        return stat;
    }

    private Map<String, Long> getCertificateStatByType(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (CertificateType certificateType : certificateRepository.findDistinctCertificateType()) {
            Integer statSize = certificateRepository.findByCertificateType(certificateType).size();
            totalStatCount += statSize;
            stat.put(certificateType.getCode(), (long) statSize);
        }
        stat.put("Unknown", dto.getTotalCertificates() - totalStatCount);
        return stat;
    }

    private Map<String, Long> getCertificateStatByKeySize(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (Integer keySize : certificateRepository.findDistinctKeySize()) {
            Integer statSize = certificateRepository.findByKeySize(keySize).size();
            totalStatCount += statSize;
            stat.put(keySize.toString(), (long) statSize);
        }
        stat.put("Unknown", dto.getTotalCertificates() - totalStatCount);
        return stat;
    }

    private Map<String, Long> getCertificateStatByBasicConstraints(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (String bc : certificateRepository.findDistinctBasicConstraints()) {
            Integer statSize = certificateRepository.findByBasicConstraints(bc).size();
            totalStatCount += statSize;
            stat.put(bc, (long) statSize);
        }
        stat.put("Unknown", dto.getTotalCertificates() - totalStatCount);
        return stat;
    }
    
    private Map<String, Long> getCertificateStatByStatus() {
        Map<String, Long> stat = new HashMap<>();
        for (CertificateStatus bc : certificateRepository.findDistinctStatus()) {
            Integer statSize = certificateRepository.findDistinctByStatus(bc).size();
            stat.put(bc.toString(), (long) statSize);
        }
        return stat;
    }

    private Map<String, Long> getCertificatesByExpiry() {
        Map<String, Long> stat = new HashMap<>();
        stat.put("10", getCertificateExpiryCount(10));
        stat.put("20", getCertificateExpiryCount(20));
        stat.put("30", getCertificateExpiryCount(30));
        stat.put("60", getCertificateExpiryCount(60));
        stat.put("90", getCertificateExpiryCount(90));
        return stat;
    }

    private Long getCertificateExpiryCount(Integer days) {
        Date expDays = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(expDays);
        cal.add(Calendar.DATE, days); //minus number would decrement the days
        List<Certificate> certs = certificateRepository.findByNotAfterLessThan(cal.getTime());
        return (long) certs.size();
    }
}
