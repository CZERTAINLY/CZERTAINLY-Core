package com.czertainly.core.dao.entity.signing;

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

    @Column(name = "accuracy")
    private Duration accuracy;

    @Column(name = "ntp_servers")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> ntpServers = new ArrayList<>();

    @Column(name = "ntp_check_interval")
    private Duration ntpCheckInterval;

    @Column(name = "ntp_samples_per_server")
    private Integer ntpSamplesPerServer;

    @Column(name = "ntp_check_timeout")
    private Duration ntpCheckTimeout;

    @Column(name = "ntp_servers_min_reachable")
    private Integer ntpServersMinReachable;

    @Column(name = "max_clock_drift")
    private Duration maxClockDrift;

    @Column(name = "leap_second_guard")
    private Boolean leapSecondGuard;
}
