package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approval.ApprovalDetailDto;
import com.czertainly.api.model.client.approval.ApprovalResponseDto;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.client.approval.UserApprovalDto;
import com.czertainly.api.model.connector.notification.data.NotificationDataApproval;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.ApprovalRecipientRepository;
import com.czertainly.core.dao.repository.ApprovalRepository;
import com.czertainly.core.dao.repository.ApprovalStepRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalService;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.RequestValidatorHelper;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
@Transactional
public class ApprovalServiceImpl implements ApprovalService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalServiceImpl.class);

    private ApprovalRepository approvalRepository;

    private ApprovalRecipientRepository approvalRecipientRepository;

    private ApprovalStepRepository approvalStepRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.APPROVAL, action = ResourceAction.LIST)
    public ApprovalResponseDto listApprovals(final SecurityFilter filter, final PaginationRequestDto paginationRequestDto) {
        return listOfApprovals(filter, null, paginationRequestDto);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.REQUEST)
    public ApprovalResponseDto listApprovalsByObject(final SecurityFilter securityFilter, final Resource resource, final UUID objectUuid, final PaginationRequestDto paginationRequestDto) {
        final BiFunction<Root<Approval>, CriteriaBuilder, Predicate> additionalWhereClause = (root, cb) -> {
            final Predicate resourcePredicate = cb.equal(root.get("resource"), resource);
            final Predicate objectPredicate = cb.equal(root.get("objectUuid"), objectUuid);
            return cb.and(resourcePredicate, objectPredicate);
        };
        return listOfApprovals(securityFilter, additionalWhereClause, paginationRequestDto);
    }

    @Override
    public ApprovalResponseDto listUserApprovals(final SecurityFilter securityFilter, final boolean withHistory, final PaginationRequestDto paginationRequestDto) {
        final UserProfileDto userProfileDto = AuthHelper.getUserProfile();
        final BiFunction<Root<Approval>, CriteriaBuilder, Predicate> additionalWhereClause = (root, cb) -> {
            final Join joinApprovalRecipient = root.join("approvalRecipients", JoinType.LEFT);
            final Predicate statusPredicate = joinApprovalRecipient.get("status").in(prepareApprovalRecipientStatuses(withHistory));
            final Predicate userUuidPredicate = cb.equal(joinApprovalRecipient.get("approvalStep").get("userUuid"), UUID.fromString(userProfileDto.getUser().getUuid()));
            final Predicate roleUuidPredicate = joinApprovalRecipient.get("approvalStep").get("roleUuid").in(userProfileDto.getRoles().stream().map(role -> UUID.fromString(role.getUuid())).collect(Collectors.toList()));
            final Predicate groupUuidPredicate = cb.equal(joinApprovalRecipient.get("approvalStep").get("groupUuid"), UUID.fromString(userProfileDto.getUser().getGroupUuid()));
            return cb.and(statusPredicate, cb.or(userUuidPredicate, roleUuidPredicate, groupUuidPredicate));
        };
        return listOfApprovals(securityFilter, additionalWhereClause, paginationRequestDto);
    }

    private List<ApprovalStatusEnum> prepareApprovalRecipientStatuses(final boolean withHistory) {
        final List<ApprovalStatusEnum> approvalStatusList = new ArrayList<>();
        if (withHistory) {
            approvalStatusList.addAll(Arrays.stream(ApprovalStatusEnum.values()).toList());
        } else {
            approvalStatusList.add(ApprovalStatusEnum.PENDING);
        }
        return approvalStatusList;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.REQUEST)
    public ApprovalDetailDto getApprovalDetail(final String uuid) throws NotFoundException {
        return findApprovalByUuid(uuid).mapToDetailDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.APPROVE)
    @ExternalAuthorization(resource = Resource.APPROVAL, action = ResourceAction.APPROVE)
    public void approveApproval(final String uuid) throws NotFoundException {
        changeApprovalStatus(uuid, ApprovalStatusEnum.APPROVED);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.REJECT)
    @ExternalAuthorization(resource = Resource.APPROVAL, action = ResourceAction.APPROVE)
    public void rejectApproval(final String uuid) throws NotFoundException {
        changeApprovalStatus(uuid, ApprovalStatusEnum.REJECTED);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.APPROVE)
    public void approveApprovalRecipient(final String approvalUuid, final UserApprovalDto userApprovalDto) throws NotFoundException {
        final ApprovalRecipient approvalRecipient = validateAndSetPendingApprovalRecipient(UUID.fromString(approvalUuid), userApprovalDto, ApprovalStatusEnum.APPROVED);
        processApprovalToTheNextStep(approvalUuid, approvalRecipient);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.REJECT)
    public void rejectApprovalRecipient(final String approvalUuid, final UserApprovalDto userApprovalDto) throws NotFoundException {
        validateAndSetPendingApprovalRecipient(UUID.fromString(approvalUuid), userApprovalDto, ApprovalStatusEnum.REJECTED);
        rejectApproval(approvalUuid);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL, operation = OperationType.CREATE)
    public Approval createApproval(final ApprovalProfileVersion approvalProfileVersion, final Resource resource, final ResourceAction resourceAction, final UUID objectUuid, final UUID userUuid, final Object objectData) throws NotFoundException {
        final Approval approvalCheck = approvalRepository.findByResourceAndObjectUuidAndStatus(resource, objectUuid, ApprovalStatusEnum.PENDING);
        if (approvalCheck != null) {
            throw new ValidationException(ValidationError.create("There is already existing approval for resouce " + resource + " and object " + objectUuid));
        }

        logger.info("Creating new Approval for ApprovalProfileVersion {} with resources {}/{}", approvalProfileVersion.getApprovalProfile().getName(), resource.name(), resourceAction.name());
        final Approval approval = new Approval();
        approval.setApprovalProfileVersion(approvalProfileVersion);
        approval.setResource(resource);
        approval.setAction(resourceAction);
        approval.setObjectUuid(objectUuid);
        approval.setCreatorUuid(userUuid);
        approval.setStatus(ApprovalStatusEnum.PENDING);
        approval.setCreatedAt(new Date());
        approvalRepository.save(approval);

        processApprovalToTheNextStep(approval.getUuid().toString(), null);
        return approval;
    }

    private ApprovalRecipient validateAndSetPendingApprovalRecipient(final UUID approvalUuid, final UserApprovalDto userApprovalDto, final ApprovalStatusEnum statusEnum) throws NotFoundException {
        final UserProfileDto userProfileDto = AuthHelper.getUserProfile();

        final Optional<Approval> approvalOptional = approvalRepository.findByUuid(SecuredUUID.fromUUID(approvalUuid));
        if (approvalOptional.isPresent()) {
            final Approval approval = approvalOptional.get();
            if (approval.getCreatorUuid().equals(userProfileDto.getUser().getUuid())) {
                throw new ValidationException("User " + userProfileDto.getUser().getUsername() + " can't approve/reject this action, because he has create this approval.");
            }
        }

        final List<ApprovalRecipient> approvalRecipientsByUser = approvalRecipientRepository.findByApprovalUuidAndUserUuid(approvalUuid, UUID.fromString(userProfileDto.getUser().getUuid()));
        if (approvalRecipientsByUser != null && !approvalRecipientsByUser.isEmpty()) {
            throw new ValidationException("User " + userProfileDto.getUser().getUsername() + " can't approve/reject this action, because he has already made decision in past.");
        }

        final List<ApprovalRecipient> approvalRecipients
                = approvalRecipientRepository.findByResponsiblePersonAndStatusAndApproval(
                UUID.fromString(userProfileDto.getUser().getUuid()),
                userProfileDto.getRoles().stream().map(role -> UUID.fromString(role.getUuid())).collect(Collectors.toList()),
                UUID.fromString(userProfileDto.getUser().getGroupUuid()),
                ApprovalStatusEnum.PENDING,
                approvalUuid);

        if (approvalRecipients == null || approvalRecipients.isEmpty()) {
            throw new NotFoundException("There is NOT expected step for current user " + userProfileDto.getUser().getUsername() + " for approval " + approvalUuid);
        } else if (approvalRecipients.size() > 1) {
            throw new ValidationException("There is more than 1 records for current user " + userProfileDto.getUser().getUsername() + " for approval " + approvalUuid);
        }

        final ApprovalRecipient approvalRecipient = approvalRecipients.get(0);
        approvalRecipient.setStatus(statusEnum);
        approvalRecipient.setClosedAt(new Date());
        approvalRecipient.setUserUuid(UUID.fromString(userProfileDto.getUser().getUuid()));
        approvalRecipient.setComment(userApprovalDto.getComment());
        approvalRecipientRepository.save(approvalRecipient);

        logger.info("User {} {} the ApprovalRecipient {}", userProfileDto.getUser().getUuid(), statusEnum.getCode(), approvalRecipient.getUuid());
        return approvalRecipient;
    }

    private void processApprovalToTheNextStep(final String approvalUuid, final ApprovalRecipient lastProcessedApprovalRecipient) {

        final List<ApprovalRecipient> approvalRecipients = approvalRecipientRepository.findApprovalRecipientsByApprovalUuidAndStatus(UUID.fromString(approvalUuid), ApprovalStatusEnum.PENDING);
        if (approvalRecipients != null && approvalRecipients.size() > 0) {
            throw new ValidationException("There are still exist some pending steps for Approval " + approvalUuid);
        }

        final ApprovalStep nextApprovalStep = approvalStepRepository.findNextApprovalStepForApproval(UUID.fromString(approvalUuid));
        if (nextApprovalStep != null) {
            logger.info("Creating new PENDING ApprovalRecipient for approval {}", approvalUuid);
            final ApprovalRecipient approvalRecipient = new ApprovalRecipient();
            approvalRecipient.setApprovalUuid(UUID.fromString(approvalUuid));
            approvalRecipient.setApprovalStep(nextApprovalStep);
            approvalRecipient.setStatus(ApprovalStatusEnum.PENDING);
            approvalRecipient.setCreatedAt(lastProcessedApprovalRecipient != null ? lastProcessedApprovalRecipient.getCreatedAt() : new Date());
            approvalRecipientRepository.save(approvalRecipient);
        }

        //TODO lukas.rejha - do decision about the steps if there is no more follow ApprovalSteps (it will be done by another task)
        // send notification just in case the version is higher than the previous one
    }

    private void changeApprovalStatus(final String uuid, final ApprovalStatusEnum approvalStatusEnum) throws NotFoundException {
        logger.info("Changing Approval {} status up to {}", uuid, approvalStatusEnum.getCode());
        final Approval approval = findApprovalByUuid(uuid);
        approval.setStatus(approvalStatusEnum);
        approval.setClosedAt(new Date());
        approvalRepository.save(approval);
    }

    private Approval findApprovalByUuid(final String uuid) throws NotFoundException {
        final Optional<Approval> approvalOptional = approvalRepository.findByUuid(SecuredUUID.fromString(uuid));
        if (approvalOptional.isPresent()) {
            return approvalOptional.get();
        }
        throw new NotFoundException("Unable to find approval profile with UUID: {}", uuid);
    }

    private ApprovalResponseDto listOfApprovals(final SecurityFilter securityFilter, final BiFunction<Root<Approval>, CriteriaBuilder, Predicate> additionalWhereClause, final PaginationRequestDto paginationRequestDto) {
        RequestValidatorHelper.revalidatePaginationRequestDto(paginationRequestDto);
        final Pageable pageable = PageRequest.of(paginationRequestDto.getPageNumber() - 1, paginationRequestDto.getItemsPerPage());
        final List<Approval> approvalList = approvalRepository.findUsingSecurityFilter(securityFilter, additionalWhereClause, pageable, null);
        final Long maxItems = approvalRepository.countUsingSecurityFilter(securityFilter, null);
        final ApprovalResponseDto responseDto = new ApprovalResponseDto();
        responseDto.setApprovals(approvalList.stream()
                .map(approval -> approval.mapToDto())
                .collect(Collectors.toList()));
        responseDto.setItemsPerPage(paginationRequestDto.getItemsPerPage());
        responseDto.setPageNumber(paginationRequestDto.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / paginationRequestDto.getItemsPerPage()));
        return responseDto;
    }

    // SETTERs

    @Autowired
    public void setApprovalRepository(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @Autowired
    public void setApprovalRecipientRepository(ApprovalRecipientRepository approvalRecipientRepository) {
        this.approvalRecipientRepository = approvalRecipientRepository;
    }

    @Autowired
    public void setApprovalStepRepository(ApprovalStepRepository approvalStepRepository) {
        this.approvalStepRepository = approvalStepRepository;
    }
}
