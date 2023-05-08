package com.czertainly.core.tasks;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.authority.RevocationReason;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.scep.ScepProfile;
import com.czertainly.core.dao.repository.scep.ScepProfileRepository;
import com.czertainly.core.intune.carequest.CARequestErrorCodes;
import com.czertainly.core.intune.carequest.CARevocationRequest;
import com.czertainly.core.intune.carequest.CARevocationResult;
import com.czertainly.core.intune.scepvalidation.IntuneRevocationClient;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class UpdateIntuneRevocationRequestsTask {

    private static final Logger logger = LoggerFactory.getLogger(UpdateIntuneRevocationRequestsTask.class);

    @Value("${app.version}")
    private String appVersion;

    private static final int MAX_CA_REQUESTS_TO_DOWNLOAD = 500;

    @Autowired
    private ScepProfileRepository scepProfileRepository;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private ClientOperationService clientOperationService;

    // scheduled for every hour, to process revocation requests from Intune enabled SCEP profiles
    @Scheduled(fixedRate = 1000*60*60, initialDelay = 10000)
    public void performTask() {
        logger.info(MarkerFactory.getMarker("scheduleInfo"), "Executing Intune revocation requests update task");
        List<ScepProfile> scepProfiles = scepProfileRepository.findByIntuneEnabled(true);
        for (ScepProfile scepProfile : scepProfiles) {
            logger.info(MarkerFactory.getMarker("scheduleInfo"), "Processing Intune revocation requests for SCEP profile: {}", scepProfile.getName());

            Properties configProperties = new Properties();
            configProperties.put("AAD_APP_ID", scepProfile.getIntuneApplicationId());
            configProperties.put("AAD_APP_KEY", scepProfile.getIntuneApplicationKey());
            configProperties.put("TENANT", scepProfile.getIntuneTenant());
            configProperties.put("PROVIDER_NAME_AND_VERSION", "CZERTAINLY-V" + appVersion);

            IntuneRevocationClient intuneRevocationClient = new IntuneRevocationClient(configProperties);

            List<CARevocationRequest> revocationRequests;
            try {
                revocationRequests = downloadRevocationRequests(intuneRevocationClient);
            } catch (Exception e) {
                logger.error(MarkerFactory.getMarker("scheduleInfo"), "Error downloading CA revocation requests", e);
                return;
            }

            List<CARevocationResult> revocationResults = processRevocationRequests(revocationRequests);

            try {
                uploadRevocationResults(intuneRevocationClient, revocationResults);
            } catch (Exception e) {
                logger.error(MarkerFactory.getMarker("scheduleInfo"), "Error uploading revocation results", e);
            }
        }
    }

    private List<CARevocationRequest> downloadRevocationRequests(IntuneRevocationClient intuneRevocationClient) throws Exception {
        String downloadTransactionId = UUID.randomUUID().toString();

        if (logger.isDebugEnabled()) {
            logger.debug(MarkerFactory.getMarker("scheduleInfo"), "Downloading CA revocation requests: transactionId={}, maxCARequestsToDownload={}, issuerDN={}",
                    downloadTransactionId, MAX_CA_REQUESTS_TO_DOWNLOAD, null);
        }

        List<CARevocationRequest> revocationRequests = intuneRevocationClient.DownloadCARevocationRequests(
                downloadTransactionId,
                MAX_CA_REQUESTS_TO_DOWNLOAD,
                null
        );

        if (logger.isDebugEnabled()) {
            logger.debug(MarkerFactory.getMarker("scheduleInfo"), "Downloaded {} revocation requests", revocationRequests.size());
        }

        return revocationRequests;
    }

    private List<CARevocationResult> processRevocationRequests(List<CARevocationRequest> revocationRequests) {
        List<CARevocationResult> revocationResults = new ArrayList<>();

        for (CARevocationRequest revocationRequest : revocationRequests) {
            try {
                Certificate certificate = certificateService.getCertificateEntityByIssuerDnAndSerialNumber(
                        revocationRequest.issuerName,
                        revocationRequest.serialNumber
                );
                // TODO: Improve handling of certificate status and revocation reason
                // there may be different certificate status we need to handle
                // when the certificate is already revoked, we just need to send the message to Intune
                if (certificate.getStatus().equals(CertificateStatus.REVOKED)) {
                    revocationResults.add(new CARevocationResult(
                                    revocationRequest.requestContext,
                                    true,
                                    CARequestErrorCodes.None,
                                    ""
                            )
                    );
                    continue;
                }
                // this should not happen, but if the certificate is expired, Intune should not try to revoke it
                if (certificate.getStatus().equals(CertificateStatus.EXPIRED)) {
                    revocationResults.add(new CARevocationResult(
                                    revocationRequest.requestContext,
                                    false,
                                    CARequestErrorCodes.NonRetryableServiceException,
                                    "Certificate already expired"
                            )
                    );
                    continue;
                }

                ClientCertificateRevocationDto revocationDto = new ClientCertificateRevocationDto();
                revocationDto.setReason(RevocationReason.UNSPECIFIED);
                revocationDto.setAttributes(new ArrayList<>());

                clientOperationService.revokeCertificate(
                        SecuredParentUUID.fromUUID(certificate.getRaProfile().getAuthorityInstanceReferenceUuid()),
                        SecuredUUID.fromUUID(certificate.getRaProfileUuid()),
                        certificate.getUuid().toString(),
                        revocationDto
                );

                revocationResults.add(new CARevocationResult(
                                revocationRequest.requestContext,
                                true,
                                CARequestErrorCodes.None,
                                ""
                        )
                );

            } catch (NotFoundException e) {
                logger.debug(MarkerFactory.getMarker("scheduleInfo"), "Certificate for Intune revocation not found in inventory: issuerDN={}, serialNumber={}",
                        revocationRequest.issuerName, revocationRequest.serialNumber);
                revocationResults.add(new CARevocationResult(
                                revocationRequest.requestContext,
                                false,
                                CARequestErrorCodes.CertificateNotFoundError,
                                "Certificate not found in inventory"
                        )
                );
            } catch (ConnectorException e) {
                logger.debug(MarkerFactory.getMarker("scheduleInfo"), "Failed to revoke certificate for Intune request: issuerDN={}, serialNumber={}",
                        revocationRequest.issuerName, revocationRequest.serialNumber, e);
                revocationResults.add(new CARevocationResult(
                                revocationRequest.requestContext,
                                false,
                                CARequestErrorCodes.RetryableServiceException,
                                e.getMessage()
                        )
                );
            }
        }

        return revocationResults;
    }

    private void uploadRevocationResults(IntuneRevocationClient intuneRevocationClient, List<CARevocationResult> revocationResults) throws Exception {
        // we upload only when there are some results
        if (revocationResults.size() > 0) {
            String uploadTransactionId = UUID.randomUUID().toString();

            if (logger.isDebugEnabled()) {
                logger.debug(MarkerFactory.getMarker("scheduleInfo"), "Uploading {} revocation results: transactionId={}",
                        revocationResults.size(), uploadTransactionId);
            }

            intuneRevocationClient.UploadRevocationResults(
                    uploadTransactionId,
                    revocationResults
            );
        }
    }

}
