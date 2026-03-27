package com.czertainly.core.dao.entity.signing;

import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationDto;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationListDto;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "time_quality_configuration")
public class TimeQualityConfiguration extends UniquelyIdentifiedAndAudited {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ntp_servers")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> ntpServers = new ArrayList<>();

    @Column(name = "ntp_check_interval")
    private Duration ntpCheckInterval;

    @Column(name = "ntp_samples_per_server")
    private Integer ntpSamplesPerServer;

    @Column(name = "ntp_check_timeout")
    private Duration ntpCheckTimeout;

    @Column(name = "min_reachable")
    private Integer minReachable;

    @Column(name = "max_drift")
    private Duration maxDrift;

    @Column(name = "leap_second_guard")
    private Boolean leapSecondGuard;

    public TimeQualityConfigurationDto mapToDto(List<ResponseAttribute> customAttributes) {
        TimeQualityConfigurationDto dto = new TimeQualityConfigurationDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setNtpServers(ntpServers);
        dto.setNtpCheckInterval(ntpCheckInterval);
        dto.setNtpSamplesPerServer(ntpSamplesPerServer);
        dto.setNtpCheckTimeout(ntpCheckTimeout);
        dto.setMinReachable(minReachable);
        dto.setMaxDrift(maxDrift);
        dto.setLeapSecondGuard(leapSecondGuard);
        dto.setCustomAttributes(customAttributes);
        return dto;
    }

    public TimeQualityConfigurationListDto mapToListDto() {
        TimeQualityConfigurationListDto dto = new TimeQualityConfigurationListDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setNtpServers(ntpServers);
        return dto;
    }
}
