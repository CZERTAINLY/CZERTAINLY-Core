package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.core.settings.NotificationSettingsDto;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.X500NameStyleCustom;
import com.czertainly.core.util.converter.SettingsSectionCodeConverter;
import org.bouncycastle.asn1.x500.X500Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

@RestController
public class SettingControllerImpl implements SettingController {

    private SettingService settingService;
    @Autowired
    private CertificateRepository certificateRepository;
    private static final Logger logger = LoggerFactory.getLogger(SettingControllerImpl.class);

    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(SettingsSection.class, new SettingsSectionCodeConverter());
    }

    @Override
    public PlatformSettingsDto getPlatformSettings() throws CertificateException {
        testCustomDnTest();
        return settingService.getPlatformSettings();
    }

    @Override
    public void updatePlatformSettings(PlatformSettingsDto request) {
        settingService.updatePlatformSettings(request);
    }

    @Override
    public NotificationSettingsDto getNotificationsSettings() {
        return settingService.getNotificationSettings();
    }

    @Override
    public void updateNotificationsSettings(NotificationSettingsDto notificationSettingsDto) {
        settingService.updateNotificationSettings(notificationSettingsDto);
    }

    private void testCustomDnTest(){
        List<Certificate> certificateList = certificateRepository.findAll();
        for (Certificate certificate : certificateList) {
            String content;
            try {
            content = certificate.getCertificateContent().getContent();}
            catch (Exception e) {
                continue;
            }
            String issuerDnInDb = certificate.getIssuerDn();
            String subjectDnInDb = certificate.getSubjectDn();
            X509Certificate x509Certificate;
            try {
                x509Certificate = CertificateUtil.getX509Certificate(content);
            } catch (CertificateException e) {
                continue;
            }
            String issuerDnFromStyle = X500Name.getInstance(X500NameStyleCustom.INSTANCE, x509Certificate.getIssuerX500Principal().getEncoded()).toString();
            String subjectDnFromStyle = X500Name.getInstance(X500NameStyleCustom.INSTANCE, x509Certificate.getSubjectX500Principal().getEncoded()).toString();
            if ((!Objects.equals(issuerDnFromStyle, issuerDnInDb)) || (!Objects.equals(subjectDnFromStyle, subjectDnInDb))) {
                logger.info("Style: " + issuerDnFromStyle + "  Db: " + issuerDnInDb + "\n Style: " + subjectDnFromStyle + " Db:" + subjectDnInDb);
            }

        }

    }
}
