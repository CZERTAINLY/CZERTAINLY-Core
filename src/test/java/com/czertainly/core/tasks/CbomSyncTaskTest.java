package com.czertainly.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
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


    @BeforeEach
    void setUp() {
    }

    @Test
    void testPerform() throws Exception {
        ScheduledJobInfo scheduledJobInfo = new ScheduledJobInfo(CbomSyncTask.NAME);
        Object taskData = new Object();
        cbomSyncTask.performJob(scheduledJobInfo, taskData);
        Mockito.verify(cbomService, Mockito.times(1)).sync();
    }

    @Test
    void testPerformJob_Success() throws Exception {
        ScheduledJobInfo scheduledJobInfo = new ScheduledJobInfo(CbomSyncTask.NAME);
        Object taskData = new Object();

        ScheduledTaskResult result = cbomSyncTask.performJob(scheduledJobInfo, taskData);

        assertEquals(SchedulerJobExecutionStatus.SUCCESS, result.getStatus());
        Mockito.verify(cbomService, Mockito.times(1)).sync();
    }

    @Test
    void testPerformJob_Failure() throws Exception {
        ScheduledJobInfo scheduledJobInfo = new ScheduledJobInfo(CbomSyncTask.NAME);
        Mockito.doThrow(new RuntimeException("Sync failed")).when(cbomService).sync();

        ScheduledTaskResult result = cbomSyncTask.performJob(scheduledJobInfo, new Object());

        assertEquals(SchedulerJobExecutionStatus.FAILED, result.getStatus());
        assertTrue(result.getResultMessage().contains("Sync failed"));
        Mockito.verify(cbomService, Mockito.times(1)).sync();
    }

}
