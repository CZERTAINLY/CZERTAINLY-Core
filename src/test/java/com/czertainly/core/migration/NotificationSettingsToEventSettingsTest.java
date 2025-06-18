package com.czertainly.core.migration;

import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.notifications.NotificationInstanceReference;
import com.czertainly.core.dao.entity.notifications.NotificationProfile;
import com.czertainly.core.dao.entity.workflows.*;
import com.czertainly.core.dao.repository.notifications.NotificationInstanceReferenceRepository;
import com.czertainly.core.dao.repository.notifications.NotificationProfileRepository;
import com.czertainly.core.dao.repository.notifications.NotificationProfileVersionRepository;
import com.czertainly.core.dao.repository.workflows.*;
import com.czertainly.core.util.BaseSpringBootTest;
import db.migration.V202506131400__NotificationSettingsToEventSettings;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsToEventSettingsTest extends BaseSpringBootTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;
    @Autowired
    NotificationProfileRepository notificationProfileRepository;
    @Autowired
    NotificationProfileVersionRepository notificationProfileVersionRepository;
    @Autowired
    ExecutionItemRepository executionItemRepository;
    @Autowired
    ExecutionRepository executionRepository;
    @Autowired
    ActionRepository actionRepository;
    @Autowired
    TriggerRepository triggerRepository;
    @Autowired
    TriggerAssociationRepository triggerAssociationRepository;

    List<ExecutionItem> executionItems;
    List<Execution> executions;
    List<Action> actions;
    List<Trigger> triggers;


    @Test
    void testMigrate() throws Exception {
        NotificationInstanceReference instanceReference1 = new NotificationInstanceReference();
        instanceReference1.setName("instance1");
        instanceReference1.setUuid(UUID.fromString("5a439cf9-9e02-4e26-aae8-cf35d5935b5e"));
        NotificationInstanceReference instanceReference2 = new NotificationInstanceReference();
        instanceReference2.setName("instance2");
        instanceReference2.setUuid(UUID.fromString("40ad94ba-c0be-4aca-9a85-f97d561168ce"));
        notificationInstanceReferenceRepository.save(instanceReference1);
        notificationInstanceReferenceRepository.save(instanceReference2);

        Context context = Mockito.mock(Context.class);
        when(context.getConnection()).thenReturn(dataSource.getConnection());

        context.getConnection().createStatement().execute("""
                INSERT INTO setting("uuid","i_author","i_cre","i_upd","section","category","name","value")
                VALUES
                (E'099f1ba9-2b6c-430f-8867-c5fa4ecd53db',E'czertainly-admin',E'2024-08-15 06:47:15.709929',E'2025-04-28 15:13:57.634206',E'NOTIFICATIONS',NULL,E'notificationsMapping',
                E'{"approval_closed":"5a439cf9-9e02-4e26-aae8-cf35d5935b5e","approval_requested":"5a439cf9-9e02-4e26-aae8-cf35d5935b5e","scheduled_job_completed":"5a439cf9-9e02-4e26-aae8-cf35d5935b5e","other":"5a439cf9-9e02-4e26-aae8-cf35d5935b5e","certificate_status_changed":"40ad94ba-c0be-4aca-9a85-f97d561168ce","certificate_action_performed":"5a439cf9-9e02-4e26-aae8-cf35d5935b5e"}');
                """);


        V202506131400__NotificationSettingsToEventSettings migration = new V202506131400__NotificationSettingsToEventSettings();
        Assertions.assertDoesNotThrow(() -> migration.migrate(context));
        executionItems = executionItemRepository.findAll();
        executions = executionRepository.findAllWithItemsBy();
        actions = actionRepository.findAll();
        triggers = triggerRepository.findAll();
        assertNotificationInstanceMigrated(instanceReference1);
        assertNotificationInstanceMigrated(instanceReference2);
    }

    private void assertNotificationInstanceMigrated(NotificationInstanceReference notificationInstanceReference) {
        String name = notificationInstanceReference.getName();
        NotificationProfile notificationProfile = notificationProfileRepository.findByName(name +"Profile").orElse(null);
        Assertions.assertNotNull(notificationProfile);

        Assertions.assertTrue(notificationProfileVersionRepository.findByNotificationProfileUuidAndVersion(notificationProfile.getUuid(), 1).isPresent());

        ExecutionItem executionItem = executionItems.stream().filter(item -> item.getNotificationProfileUuid().equals(notificationProfile.getUuid())).findFirst().orElse(null);
        Assertions.assertNotNull(executionItem);

        Execution execution = executions.stream().filter(execution1 -> execution1.getName().equals(name + "Execution")).findFirst().orElse(null);
        Assertions.assertNotNull(execution);
        Assertions.assertTrue(execution.getItems().contains(executionItem));

        Action action = actions.stream().filter(action1 -> action1.getName().equals(name + "Action")).findFirst().orElse(null);
        Assertions.assertNotNull(action);
        action = actionRepository.findWithExecutionsByUuid(action.getUuid()).get();
        Assertions.assertTrue(action.getExecutions().contains(execution));

        action = actionRepository.findWithTriggersByUuid(action.getUuid()).get();
        Trigger triggerCert = triggers.stream().filter(trigger1 -> trigger1.getName().equals(name + "TriggerCERTIFICATE")).findFirst().orElse(null);
        Assertions.assertNotNull(triggerCert);
        List<TriggerAssociation> triggerAssociationsCert = triggerAssociationRepository.findByTriggerUuid(triggerCert.getUuid());

        if (name.contains("1")) {
            Assertions.assertTrue(triggerAssociationsCert.stream().anyMatch(triggerAssociation -> triggerAssociation.getEvent().equals(ResourceEvent.CERTIFICATE_ACTION_PERFORMED)));

            Trigger trigger1Job = triggers.stream().filter(trigger1 -> trigger1.getName().equals(name + "TriggerSCHEDULED_JOB")).findFirst().orElse(null);
            Assertions.assertNotNull(trigger1Job);
            Trigger trigger1Approval = triggers.stream().filter(trigger1 -> trigger1.getName().equals(name + "TriggerAPPROVAL")).findFirst().orElse(null);
            Assertions.assertNotNull(trigger1Approval);
            Assertions.assertTrue(action.getTriggers().containsAll(List.of(trigger1Job, triggerCert, trigger1Approval)));

            List<TriggerAssociation> triggerAssociationsApproval = triggerAssociationRepository.findByTriggerUuid(trigger1Approval.getUuid());
            Assertions.assertTrue(triggerAssociationsApproval.stream().anyMatch(triggerAssociation -> triggerAssociation.getEvent().equals(ResourceEvent.APPROVAL_CLOSED)));
            Assertions.assertTrue(triggerAssociationsApproval.stream().anyMatch(triggerAssociation -> triggerAssociation.getEvent().equals(ResourceEvent.APPROVAL_REQUESTED)));
            List<TriggerAssociation> triggerAssociationsJob = triggerAssociationRepository.findByTriggerUuid(trigger1Job.getUuid());
            Assertions.assertTrue(triggerAssociationsJob.stream().anyMatch(triggerAssociation -> triggerAssociation.getEvent().equals(ResourceEvent.SCHEDULED_JOB_FINISHED)));
        } else {
            Assertions.assertTrue(triggerAssociationsCert.stream().anyMatch(triggerAssociation -> triggerAssociation.getEvent().equals(ResourceEvent.CERTIFICATE_STATUS_CHANGED)));
            Assertions.assertTrue(action.getTriggers().contains(triggerCert));
        }
    }


}
