package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.EnumController;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.common.enums.PlatformEnum;
import com.czertainly.api.model.core.enums.EnumItemDto;
import com.czertainly.core.service.EnumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EnumControllerImpl implements EnumController {

    private EnumService enumService;

    @Autowired
    public void setEnumService(EnumService enumService) {
        this.enumService = enumService;
    }


    @Override
    public Map<PlatformEnum, Map<String, EnumItemDto>> getPlatformEnums() {
        return enumService.getPlatformEnums();
    }
}
