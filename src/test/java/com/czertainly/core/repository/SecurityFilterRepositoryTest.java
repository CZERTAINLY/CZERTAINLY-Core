package com.czertainly.core.repository;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.security.authz.SecurityResourceFilter;
import com.czertainly.core.service.ResourceObjectAssociationService;
import com.czertainly.core.util.BaseSpringBootTest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.function.TriFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

@SpringBootTest
class SecurityFilterRepositoryTest extends BaseSpringBootTest {

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private ResourceObjectAssociationService resourceObjectAssociationService;

    private Group group;
    private RaProfile raProfile;
    private RaProfile raProfile2;

    private Certificate certificateGroup;
    private Certificate certificateRaProfile;
    private Certificate certificateRaProfile2;

    @BeforeEach
    public void setUp() throws NotFoundException {
        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        group = new Group();
        group.setName("TestGroup");
        groupRepository.save(group);

        raProfile = new RaProfile();
        raProfile.setName("Test RA profile");
        raProfile = raProfileRepository.save(raProfile);

        raProfile2 = new RaProfile();
        raProfile2.setName("Test RA profile2");
        raProfile2 = raProfileRepository.save(raProfile2);

        certificateGroup = new Certificate();
        certificateGroup.setSubjectDn("CN=testCertificateGroup");
        certificateGroup.setIssuerDn("CN=testCercertificateGroupIssuer");
        certificateGroup.setSerialNumber("123456");
        certificateGroup.setState(CertificateState.ISSUED);
        certificateGroup.setValidationStatus(CertificateValidationStatus.VALID);
        certificateGroup.setCertificateContent(certificateContent);
        certificateGroup.setCertificateContentId(certificateContent.getId());
        certificateGroup = certificateRepository.save(certificateGroup);
        resourceObjectAssociationService.addGroup(Resource.CERTIFICATE, certificateGroup.getUuid(), group.getUuid());

        certificateRaProfile = new Certificate();
        certificateRaProfile.setSubjectDn("CN=testCertificateRA");
        certificateRaProfile.setIssuerDn("CN=testCertificateRAIssuer");
        certificateRaProfile.setSerialNumber("123456");
        certificateRaProfile.setState(CertificateState.ISSUED);
        certificateRaProfile.setValidationStatus(CertificateValidationStatus.VALID);
        certificateRaProfile.setCertificateContent(certificateContent);
        certificateRaProfile.setCertificateContentId(certificateContent.getId());
        certificateRaProfile.setRaProfile(raProfile);
        certificateRaProfile = certificateRepository.save(certificateRaProfile);

        certificateRaProfile2 = new Certificate();
        certificateRaProfile2.setSubjectDn("CN=testCertificateRA2");
        certificateRaProfile2.setIssuerDn("CN=testCertificateRAIssuer2");
        certificateRaProfile2.setSerialNumber("1234567");
        certificateRaProfile2.setState(CertificateState.ISSUED);
        certificateRaProfile2.setValidationStatus(CertificateValidationStatus.VALID);
        certificateRaProfile2.setCertificateContent(certificateContent);
        certificateRaProfile2.setCertificateContentId(certificateContent.getId());
        certificateRaProfile2.setRaProfile(raProfile2);
        certificateRaProfile2 = certificateRepository.save(certificateRaProfile2);
    }

    @Test
    void testSecurityFilter() {
        SecurityFilter filter = SecurityFilter.create();
        SecurityResourceFilter resourceFilter = SecurityResourceFilter.create();
        resourceFilter.setResource(Resource.CERTIFICATE);
        resourceFilter.setResourceAction(ResourceAction.LIST);
        SecurityResourceFilter parentResourceFilter = SecurityResourceFilter.create();
        parentResourceFilter.setResource(Resource.RA_PROFILE);
        parentResourceFilter.setResourceAction(ResourceAction.MEMBERS);
        parentResourceFilter.setAreOnlySpecificObjectsAllowed(true);
        parentResourceFilter.addAllowedObjects(List.of(raProfile.getUuid().toString(), raProfile2.getUuid().toString()));

        filter.setResourceFilter(resourceFilter);
        filter.setParentResourceFilter(parentResourceFilter);
        filter.setParentRefProperty(Certificate_.raProfileUuid.getName());

        // test permissions for all RA profiles with additional where clause
        final TriFunction<Root<Certificate>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause = (root, cb, cr) -> cb.equal(root.get(Certificate_.serialNumber), certificateRaProfile.getSerialNumber());
        List<Certificate> certificates = certificateRepository.findUsingSecurityFilter(filter, List.of(Certificate_.raProfile.getName()), additionalWhereClause);
        Assertions.assertEquals(1, certificates.size());
        Assertions.assertEquals(certificateRaProfile.getUuid().toString(), certificates.getFirst().getUuid().toString());

        // test permissions for single RA Profile and group membership
        parentResourceFilter.getAllowedObjects().clear();
        parentResourceFilter.addAllowedObjects(List.of(raProfile2.getUuid().toString()));
        SecurityResourceFilter groupResourceFilter = SecurityResourceFilter.create();
        groupResourceFilter.setResource(Resource.GROUP);
        groupResourceFilter.setResourceAction(ResourceAction.MEMBERS);
        groupResourceFilter.setAreOnlySpecificObjectsAllowed(true);
        groupResourceFilter.addAllowedObjects(List.of(group.getUuid().toString()));
        filter.setGroupMembersFilter(groupResourceFilter);

        certificates = certificateRepository.findUsingSecurityFilter(filter, List.of(), null);
        List<UUID> foundUuids = certificates.stream().map(UniquelyIdentifiedAndAudited::getUuid).toList();
        Assertions.assertEquals(2, certificates.size());
        Assertions.assertTrue(foundUuids.contains(certificateRaProfile2.getUuid()));
        Assertions.assertTrue(foundUuids.contains(certificateGroup.getUuid()));
    }
}
