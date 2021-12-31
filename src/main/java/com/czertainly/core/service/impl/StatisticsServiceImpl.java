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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
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
        return groupRepository.count();
    }

    private long getCertificateEntityCount() {
        return entityRepository.count();
    }

    private long getRaProfileCount() {
        return raProfileRepository.count();
    }

    private Map<String, Long> getGroupStatByCertificateCount(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (CertificateGroup certificateGroup : groupRepository.findAll()) {
            Integer statSize = certificateRepository.findDistinctByGroupId(certificateGroup.getId()).size();
            totalStatCount += statSize;
            stat.put(certificateGroup.getName(), (long) statSize);
        }
        stat.put("Unassigned", dto.getTotalCertificates() - totalStatCount);
        return stat;
    }

    private Map<String, Long> getEntityStatByCertificateCount(StatisticsDto dto) {
        Map<String, Long> stat = new HashMap<>();
        long totalStatCount = 0;
        for (CertificateEntity certificateEntity : entityRepository.findAll()) {
            Integer statSize = certificateRepository.findDistinctByEntityId(certificateEntity.getId()).size();
            totalStatCount += statSize;
            stat.put(certificateEntity.getName(), (long) statSize);
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
