package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.core.model.compliance.ComplianceResultDto;

public interface ComplianceSubject extends UniquelyIdentifiedObject {

    IPlatformEnum getType();

    IPlatformEnum getFormat();

    String getContentData();

    ComplianceStatus getComplianceStatus();

    void setComplianceStatus(ComplianceStatus complianceStatus);

    ComplianceResultDto getComplianceResult();

    void setComplianceResult(ComplianceResultDto complianceResultDto);
}
