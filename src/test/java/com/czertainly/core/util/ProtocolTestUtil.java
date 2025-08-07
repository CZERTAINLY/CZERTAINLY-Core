package com.czertainly.core.util;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.ProtocolCertificateAssociation;
import com.czertainly.core.service.AttributeService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class ProtocolTestUtil {

    @NotNull
    public static ProtocolCertificateAssociation getProtocolCertificateAssociation(UUID ownerUuid, List<UUID> groupUuids, AttributeService attributeService) throws AlreadyExistException, AttributeException {
        ProtocolCertificateAssociation protocolCertificateAssociation = new ProtocolCertificateAssociation();
        protocolCertificateAssociation.setOwnerUuid(ownerUuid);
        protocolCertificateAssociation.setGroupUuids(groupUuids);
        CustomAttributeCreateRequestDto customAttributeRequest = new CustomAttributeCreateRequestDto();
        customAttributeRequest.setName("name");
        customAttributeRequest.setLabel("name");
        customAttributeRequest.setResources(List.of(Resource.CERTIFICATE));
        customAttributeRequest.setContentType(AttributeContentType.STRING);
        String attributeUuid = attributeService.createCustomAttribute(customAttributeRequest).getUuid();
        RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
        requestAttributeDto.setUuid(attributeUuid);
        requestAttributeDto.setName(customAttributeRequest.getName());
        requestAttributeDto.setContentType(customAttributeRequest.getContentType());
        requestAttributeDto.setContent(List.of(new StringAttributeContent("ref", "data")));
        protocolCertificateAssociation.setCustomAttributes(List.of(requestAttributeDto));
        return protocolCertificateAssociation;
    }
}
