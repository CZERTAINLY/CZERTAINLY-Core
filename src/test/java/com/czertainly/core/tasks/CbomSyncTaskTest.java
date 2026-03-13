package com.czertainly.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.CbomService;
import com.czertainly.core.util.BaseSpringBootTest;

class CbomSyncTaskTest extends BaseSpringBootTest {

    @MockitoBean
    private CbomService cbomService;

    @Autowired
    private CbomSyncTask cbomSyncTask;


    @Test
    void testPerformJob_Success() throws Exception {
        ScheduledJobInfo scheduledJobInfo = new ScheduledJobInfo(CbomSyncTask.NAME);
        Object taskData = new Object();
        Mockito.when(cbomService.isCbomRepositoryClientConfigured()).thenReturn(true);

        ScheduledTaskResult result = cbomSyncTask.performJob(scheduledJobInfo, taskData);

        assertEquals(SchedulerJobExecutionStatus.SUCCESS, result.getStatus());
        Mockito.verify(cbomService, Mockito.times(1)).isCbomRepositoryClientConfigured();
        Mockito.verify(cbomService, Mockito.times(1)).sync();
    }

    @Test
    void testPerformJob_Failure() throws Exception {
        ScheduledJobInfo scheduledJobInfo = new ScheduledJobInfo(CbomSyncTask.NAME);
        Mockito.when(cbomService.isCbomRepositoryClientConfigured()).thenReturn(true);
        Mockito.doThrow(new RuntimeException("Sync failed")).when(cbomService).sync();

        ScheduledTaskResult result = cbomSyncTask.performJob(scheduledJobInfo, new Object());

        assertEquals(SchedulerJobExecutionStatus.FAILED, result.getStatus());
        assertTrue(result.getResultMessage().contains("Sync failed"));
        Mockito.verify(cbomService, Mockito.times(1)).isCbomRepositoryClientConfigured();
        Mockito.verify(cbomService, Mockito.times(1)).sync();
    }

    @Test
    void testPerformJob_Skip() throws Exception {
        ScheduledJobInfo scheduledJobInfo = new ScheduledJobInfo(CbomSyncTask.NAME);
        Mockito.when(cbomService.isCbomRepositoryClientConfigured()).thenReturn(false);

        ScheduledTaskResult result = cbomSyncTask.performJob(scheduledJobInfo, new Object());

        assertEquals(SchedulerJobExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("CBOM Sync: SKIPPED", result.getResultMessage());
        Mockito.verify(cbomService, Mockito.times(1)).isCbomRepositoryClientConfigured();
        Mockito.verify(cbomService, Mockito.times(0)).sync();
    }

    @Test
    void testGetDefaultJobName() {
        assertEquals(CbomSyncTask.NAME, cbomSyncTask.getDefaultJobName());
    }

    @Test
    void testGetDefaultCronExpression() {
        assertEquals("0 0 * ? * *", cbomSyncTask.getDefaultCronExpression());
    }

    @Test
    void testIsDefaultOneTimeJob() {
        assertFalse(cbomSyncTask.isDefaultOneTimeJob());
    }

    @Test
    void testGetJobClassName() {
        assertEquals(CbomSyncTask.class.getName(), cbomSyncTask.getJobClassName());
    }

}
