package com.czertainly.core.service.impl;

import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.common.enums.PlatformEnum;
import com.czertainly.api.model.core.enums.EnumItemDto;
import com.czertainly.core.service.EnumService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Transactional
public class EnumServiceImpl implements EnumService {

    private static final Logger logger = LoggerFactory.getLogger(EnumServiceImpl.class);

    @Override
    public Map<PlatformEnum, Map<String, EnumItemDto>> getPlatformEnums() {
        Map<PlatformEnum, Map<String, EnumItemDto>> enumsMap = new HashMap<>();

        for (PlatformEnum platformEnum: PlatformEnum.values()) {
            Map<String, EnumItemDto> enumItemsMap = new HashMap<>();

            IPlatformEnum[] enumConstants = platformEnum.getEnumClass().getEnumConstants();
            for (IPlatformEnum enumConstant : enumConstants) {
                EnumItemDto enumItem = new EnumItemDto();
                enumItem.setCode(enumConstant.getCode());
                enumItem.setLabel(enumConstant.getLabel());
                enumItem.setDescription(enumConstant.getDescription());
                enumItemsMap.put(enumConstant.getCode(), enumItem);
            }
            enumsMap.put(platformEnum, enumItemsMap);
        }

        return enumsMap;
    }
}
