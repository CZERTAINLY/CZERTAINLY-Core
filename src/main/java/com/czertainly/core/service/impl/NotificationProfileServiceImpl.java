package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.notification.*;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.NotificationProfile;
import com.czertainly.core.dao.entity.NotificationProfileVersion;
import com.czertainly.core.dao.repository.NotificationProfileRepository;
import com.czertainly.core.dao.repository.NotificationProfileVersionRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.NotificationProfileService;
import com.czertainly.core.service.ResourceObjectAssociationService;
import com.czertainly.core.util.RequestValidatorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class NotificationProfileServiceImpl implements NotificationProfileService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProfileServiceImpl.class);

    private NotificationProfileRepository notificationProfileRepository;
    private NotificationProfileVersionRepository notificationProfileVersionRepository;

    private ResourceObjectAssociationService resourceObjectAssociationService;

    @Autowired
    public void setNotificationProfileRepository(NotificationProfileRepository notificationProfileRepository) {
        this.notificationProfileRepository = notificationProfileRepository;
    }

    @Autowired
    public void setNotificationProfileVersionRepository(NotificationProfileVersionRepository notificationProfileVersionRepository) {
        this.notificationProfileVersionRepository = notificationProfileVersionRepository;
    }

    @Autowired
    public void setResourceObjectAssociationService(ResourceObjectAssociationService resourceObjectAssociationService) {
        this.resourceObjectAssociationService = resourceObjectAssociationService;
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_PROFILE, action = ResourceAction.LIST)
    public NotificationProfileResponseDto listNotificationProfiles(PaginationRequestDto paginationRequestDto) {
        RequestValidatorHelper.revalidatePaginationRequestDto(paginationRequestDto);

        SecurityFilter filter = SecurityFilter.create();
        final Pageable pageable = PageRequest.of(paginationRequestDto.getPageNumber() - 1, paginationRequestDto.getItemsPerPage());
        final List<NotificationProfile> notificationProfiles = notificationProfileRepository.findUsingSecurityFilter(filter, List.of(), null, pageable, null);
        final Long maxItems = notificationProfileRepository.countUsingSecurityFilter(filter, null);

        final NotificationProfileResponseDto responseDto = new NotificationProfileResponseDto();
        responseDto.setNotificationProfiles(notificationProfiles.stream().map(notificationProfile -> notificationProfile.getCurrentVersion().mapToDto()).toList());
        responseDto.setItemsPerPage(paginationRequestDto.getItemsPerPage());
        responseDto.setPageNumber(paginationRequestDto.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / paginationRequestDto.getItemsPerPage()));

        return responseDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_PROFILE, action = ResourceAction.DETAIL)
    public NotificationProfileDetailDto getNotificationProfile(SecuredUUID uuid, Integer version) throws NotFoundException {
        NotificationProfileVersion notificationProfileVersion;
        if (version == null) {
            notificationProfileVersion = notificationProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(NotificationProfile.class, uuid)).getCurrentVersion();
        } else {
            notificationProfileVersion = notificationProfileVersionRepository.findByNotificationProfileUuidAndVersion(uuid.getValue(), version).orElseThrow(() -> new NotFoundException(NotificationProfileVersion.class, uuid));
        }

        // retrieve recipient info and check for existence of such object
        NameAndUuidDto recipientInfo = resourceObjectAssociationService.getAssociationObjectInfo(notificationProfileVersion.getRecipientType(), notificationProfileVersion.getRecipientUuid());
        return notificationProfileVersion.mapToDetailDto(new RecipientDto(notificationProfileVersion.getRecipientType(), recipientInfo));
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_PROFILE, action = ResourceAction.DELETE)
    public void deleteNotificationProfile(SecuredUUID uuid) throws NotFoundException {
        NotificationProfile notificationProfile = notificationProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(NotificationProfile.class, uuid));

        // TODO: check if there are no pending notifications referencing this notification profile before deleting
        notificationProfileRepository.delete(notificationProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_PROFILE, action = ResourceAction.CREATE)
    public NotificationProfileDetailDto createNotificationProfile(NotificationProfileRequestDto requestDto) throws AlreadyExistException, NotFoundException {
        if (notificationProfileRepository.findByName(requestDto.getName()).isPresent()) {
            throw new AlreadyExistException("Notification profile with name " + requestDto.getName() + " already exists.");
        }

        // retrieve recipient info and check for existence of such object
        NameAndUuidDto recipientInfo = resourceObjectAssociationService.getAssociationObjectInfo(requestDto.getRecipientType(), requestDto.getRecipientUuid());

        NotificationProfile notificationProfile = new NotificationProfile();
        notificationProfile.setName(requestDto.getName());
        notificationProfile.setDescription(requestDto.getDescription());
        notificationProfile = notificationProfileRepository.save(notificationProfile);

        NotificationProfileVersion notificationProfileVersion = new NotificationProfileVersion();
        notificationProfileVersion.setVersion(1);
        notificationProfileVersion.setNotificationProfileUuid(notificationProfile.getUuid());
        notificationProfileVersion.setNotificationProfile(notificationProfile);
        notificationProfileVersion.setRecipientType(requestDto.getRecipientType());
        notificationProfileVersion.setRecipientUuid(requestDto.getRecipientUuid());
        notificationProfileVersion.setNotificationInstanceRefUuid(requestDto.getNotificationInstanceUuid());
        notificationProfileVersion.setInternalNotification(requestDto.isInternalNotification());
        notificationProfileVersion.setFrequency(requestDto.getFrequency());
        notificationProfileVersion.setRepetitions(requestDto.getRepetitions());
        notificationProfile.getVersions().add(notificationProfileVersion);
        notificationProfileVersionRepository.save(notificationProfileVersion);

        return notificationProfileVersion.mapToDetailDto(new RecipientDto(notificationProfileVersion.getRecipientType(), recipientInfo));
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_PROFILE, action = ResourceAction.UPDATE)
    public NotificationProfileDetailDto editNotificationProfile(SecuredUUID uuid, NotificationProfileUpdateRequestDto updateRequestDto) throws NotFoundException {
        NotificationProfile notificationProfile = notificationProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(NotificationProfile.class, uuid));
        NotificationProfileVersion currentVersion = notificationProfile.getCurrentVersion();

        // retrieve recipient info and check for existence of such object
        NameAndUuidDto recipientInfo = resourceObjectAssociationService.getAssociationObjectInfo(updateRequestDto.getRecipientType(), updateRequestDto.getRecipientUuid());

        if (areVersionsEqual(currentVersion, updateRequestDto)) {
            logger.debug("Current version of notification profile {} is same as in request. New version is not created", notificationProfile.getName());
            return currentVersion.mapToDetailDto(new RecipientDto(currentVersion.getRecipientType(), recipientInfo));
        }

        if (!Objects.equals(notificationProfile.getDescription(), updateRequestDto.getDescription())) {
            notificationProfile.setDescription(updateRequestDto.getDescription());
            notificationProfileRepository.save(notificationProfile);
        }

        NotificationProfileVersion notificationProfileVersion = new NotificationProfileVersion();
        notificationProfileVersion.setNotificationProfileUuid(notificationProfile.getUuid());
        notificationProfileVersion.setNotificationProfile(notificationProfile);
        notificationProfileVersion.setVersion(currentVersion.getVersion() + 1);
        notificationProfileVersion.setRecipientType(updateRequestDto.getRecipientType());
        notificationProfileVersion.setRecipientUuid(updateRequestDto.getRecipientUuid());
        notificationProfileVersion.setNotificationInstanceRefUuid(updateRequestDto.getNotificationInstanceUuid());
        notificationProfileVersion.setInternalNotification(updateRequestDto.isInternalNotification());
        notificationProfileVersion.setFrequency(updateRequestDto.getFrequency());
        notificationProfileVersion.setRepetitions(updateRequestDto.getRepetitions());
        notificationProfileVersion = notificationProfileVersionRepository.save(notificationProfileVersion);

        return notificationProfileVersion.mapToDetailDto(new RecipientDto(notificationProfileVersion.getRecipientType(), recipientInfo));
    }

    private boolean areVersionsEqual(NotificationProfileVersion currentVersion, NotificationProfileUpdateRequestDto requestDto) {
        return currentVersion.getRecipientType() == requestDto.getRecipientType()
                && Objects.equals(currentVersion.getRecipientUuid(), requestDto.getRecipientUuid())
                && Objects.equals(currentVersion.getNotificationInstanceRefUuid(), requestDto.getNotificationInstanceUuid())
                && currentVersion.isInternalNotification() == requestDto.isInternalNotification()
                && Objects.equals(currentVersion.getFrequency(), requestDto.getFrequency())
                && Objects.equals(currentVersion.getRepetitions(), requestDto.getRepetitions());
    }
}
