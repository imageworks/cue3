
/*
 * Copyright (c) 2018 Sony Pictures Imageworks Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package com.imageworks.spcue.test.dispatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.imageworks.spcue.DispatchHost;
import com.imageworks.spcue.GroupDetail;
import com.imageworks.spcue.JobDetail;
import com.imageworks.spcue.ShowDetail;
import com.imageworks.spcue.VirtualProc;
import com.imageworks.spcue.CueIce.HardwareState;
import com.imageworks.spcue.RqdIce.RenderHost;
import com.imageworks.spcue.dao.FrameDao;
import com.imageworks.spcue.dispatcher.DispatchSupport;
import com.imageworks.spcue.dispatcher.Dispatcher;
import com.imageworks.spcue.service.AdminManager;
import com.imageworks.spcue.service.GroupManager;
import com.imageworks.spcue.service.HostManager;
import com.imageworks.spcue.service.JobLauncher;
import com.imageworks.spcue.service.JobManager;
import com.imageworks.spcue.test.TransactionalTest;
import com.imageworks.spcue.util.CueUtil;

import static org.junit.Assert.*;

@ContextConfiguration
public class CoreUnitDispatcherGpuTests extends TransactionalTest {

    @Resource
    JobManager jobManager;

    @Resource
    JobLauncher jobLauncher;

    @Resource
    HostManager hostManager;

    @Resource
    AdminManager adminManager;

    @Resource
    GroupManager groupManager;

    @Resource
    Dispatcher dispatcher;

    @Resource
    DispatchSupport dispatchSupport;

    @Resource
    FrameDao frameDao;

    private static final String HOSTNAME = "beta";

    private static final String JOBNAME =
        "pipe-dev.cue-testuser_shell_dispatch_test_v1";

    private static final String TARGET_JOB =
        "pipe-dev.cue-testuser_shell_dispatch_test_v2";

    @Before
    public void launchJob() {
        jobLauncher.testMode = true;
        jobLauncher.launch(new File("src/test/resources/conf/jobspec/jobspec_dispatch_test.xml"));
    }

    @Before
    public void setTestMode() {
        dispatcher.setTestMode(true);
    }

    @Before
    public void createHost() {
        RenderHost host = new RenderHost();
        host.name = HOSTNAME;
        host.bootTime = 1192369572;
        host.freeMcp = 76020;
        host.freeMem = (int) CueUtil.GB8;
        host.freeSwap = 20760;
        host.load = 1;
        host.totalMcp = 195430;
        host.totalMem = (int) CueUtil.GB8;
        host.totalSwap = (int) CueUtil.GB2;
        host.nimbyEnabled = false;
        host.numProcs = 1;
        host.coresPerProc = 200;
        host.tags = new ArrayList<String>();
        host.tags.add("test");
        host.state = HardwareState.Up;
        host.facility = "spi";
        host.attributes = new HashMap<String, String>();
        host.attributes.put("SP_OS", "Linux");
        host.attributes.put("freeGpu", String.format("%d", CueUtil.MB512));
        host.attributes.put("totalGpu", String.format("%d", CueUtil.MB512));

        hostManager.createHost(host,
                adminManager.findAllocationDetail("spi", "general"));
    }

    public JobDetail getJob() {
        return jobManager.findJobDetail(JOBNAME);
    }

    public JobDetail getTargetJob() {
        return jobManager.findJobDetail(TARGET_JOB);
    }

    public DispatchHost getHost() {
        return hostManager.findDispatchHost(HOSTNAME);
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testDispatchHost() {
        DispatchHost host = getHost();

        List<VirtualProc> procs =  dispatcher.dispatchHost(host);
        assertEquals(1, procs.size());
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testDispatchGpuRemovedHostToNonGpuJob() {
        DispatchHost host = getHost();
        JobDetail job = getJob();

        host.idleMemory = (long) host.idleMemory - Math.min(CueUtil.GB4, host.idleMemory);
        host.idleCores = (int) host.idleCores - Math.min(100, host.idleCores);
        host.idleGpu = 0;
        List<VirtualProc> procs =  dispatcher.dispatchHost(host, job);
        assertEquals(1, procs.size());
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testDispatchGpuHostToGroup() {
        DispatchHost host = getHost();
        JobDetail job = getJob();
        GroupDetail group = groupManager.getGroupDetail(job);

        List<VirtualProc> procs =  dispatcher.dispatchHost(host, group);
        assertEquals(1, procs.size());
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testDispatchGpuHostToShowNoPrefer() {
        DispatchHost host = getHost();
        JobDetail job = getJob();
        ShowDetail show = adminManager.findShowDetail("edu");

        List<VirtualProc> procs =  dispatcher.dispatchHost(host);
        assertEquals(1, procs.size());
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testDispatchRemovedGpuHostToShowPrefer() {
        DispatchHost host = getHost();
        JobDetail job = getJob();
        ShowDetail show = adminManager.findShowDetail("edu");

        List<VirtualProc> procs =  dispatcher.dispatchHost(host, show);
        assertEquals(0, procs.size());
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testDispatchHostRemoveRestoreGpu() {
        DispatchHost host = getHost();

        long idleMemoryOrig = host.idleMemory;
        int idleCoresOrig = host.idleCores;
        long idleGpuOrig = host.idleGpu;

        host.removeGpu();
        assertEquals(0, host.idleGpu);
        assertEquals(idleMemoryOrig - CueUtil.GB4, host.idleMemory);
        assertEquals(idleCoresOrig - 100, host.idleCores);

        host.restoreGpu();
        assertEquals(idleMemoryOrig, host.idleMemory);
        assertEquals(idleCoresOrig, host.idleCores);
        assertEquals(idleGpuOrig, host.idleGpu);
    }

    @Test
    @Transactional
    @Rollback(true)
    public void dispatchProcToJob() {
        DispatchHost host = getHost();
        JobDetail job = getJob();

        host.idleGpu = 0;
        List<VirtualProc> procs = dispatcher.dispatchHost(host, job);
        VirtualProc proc = procs.get(0);
        dispatcher.dispatchProcToJob(proc, job);
    }
}

