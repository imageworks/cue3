
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



package com.imageworks.spcue.test.service;

import static org.junit.Assert.*;

import java.io.File;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ContextConfiguration;

import com.imageworks.spcue.FrameInterface;
import com.imageworks.spcue.FrameDetail;
import com.imageworks.spcue.JobInterface;
import com.imageworks.spcue.JobDetail;
import com.imageworks.spcue.LayerInterface;
import com.imageworks.spcue.LightweightDependency;
import com.imageworks.spcue.CueIce.FrameState;
import com.imageworks.spcue.dao.DependDao;
import com.imageworks.spcue.dao.FrameDao;
import com.imageworks.spcue.dao.LayerDao;
import com.imageworks.spcue.dao.criteria.FrameSearch;
import com.imageworks.spcue.depend.*;
import com.imageworks.spcue.service.DependManager;
import com.imageworks.spcue.service.JobLauncher;
import com.imageworks.spcue.service.JobManager;
import com.imageworks.spcue.service.JobManagerSupport;
import com.imageworks.spcue.test.TransactionalTest;

@ContextConfiguration
public class DependManagerTests extends TransactionalTest {

    @Resource
    DependDao dependDao;

    @Resource
    DependManager dependManager;

    @Resource
    FrameDao frameDao;

    @Resource
    LayerDao layerDao;

    @Resource
    JobManager jobManager;

    @Resource
    JobManagerSupport jobManagerSupport;

    @Resource
    JobLauncher jobLauncher;

    @Before
    public void launchTestJobs() {
        jobLauncher.testMode = true;
        jobLauncher.launch(new File("src/test/resources/conf/jobspec/jobspec_depend_test.xml"));
    }

    public JobDetail getJobA() {
        return jobManager.findJobDetail("pipe-dev.cue-testuser_depend_test_a");
    }

    public JobDetail getJobB() {
        return jobManager.findJobDetail("pipe-dev.cue-testuser_depend_test_b");
    }

    public int getTotalDependCount(JobInterface j) {
        return jdbcTemplate.queryForObject(
                "SELECT SUM(int_depend_count) FROM frame WHERE pk_job=?",
                Integer.class, j.getJobId());
    }

    public boolean hasDependFrames(JobInterface j) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM frame WHERE pk_job=? AND str_state=?",
                Integer.class, j.getJobId(), FrameState.Depend.toString()) > 0;
    }

    public int getTotalDependCount(LayerInterface l) {
        return jdbcTemplate.queryForObject(
                "SELECT SUM(int_depend_count) FROM frame WHERE pk_layer=?",
                Integer.class, l.getLayerId());
    }

    public boolean hasDependFrames(LayerInterface l) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM frame WHERE pk_layer=? AND str_state=?",
                Integer.class, l.getLayerId(), FrameState.Depend.toString()) > 0;
    }

    public int getTotalDependCount(FrameInterface f) {
        return jdbcTemplate.queryForObject(
                "SELECT SUM(int_depend_count) FROM frame WHERE pk_frame=?",
                Integer.class, f.getFrameId());
    }

    public boolean hasDependFrames(FrameInterface f) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM frame WHERE pk_frame=? AND str_state=?",
                Integer.class, f.getFrameId(), FrameState.Depend.toString()) > 0;
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testUnsatisfyFrameOnFrame() {
        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");
        FrameInterface frame_a = frameDao.findFrame(layer_a, 1);
        FrameInterface frame_b = frameDao.findFrame(layer_b, 1);

        FrameOnFrame depend = new FrameOnFrame(frame_a, frame_b);
        dependManager.createDepend(depend);

        // Check to ensure depend was setup properly.
        assertTrue(hasDependFrames(layer_a));
        assertEquals(1, getTotalDependCount(layer_a));
        assertTrue(hasDependFrames(frame_a));
        assertEquals(1, getTotalDependCount(frame_a));

        LightweightDependency lwd = dependManager.getDepend(depend.getId());
        dependManager.satisfyDepend(lwd);

        // Check to ensure it was satisfied properly.
        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
        assertFalse(hasDependFrames(frame_a));
        assertEquals(0, getTotalDependCount(frame_a));

        // Now unsatisfy it.
        dependManager.unsatisfyDepend(lwd);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(1, getTotalDependCount(layer_a));
        assertTrue(hasDependFrames(frame_a));
        assertEquals(1, getTotalDependCount(frame_a));
    }


    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyJobOnJob() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();

        JobOnJob depend = new JobOnJob(job_a, job_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(job_a));
        assertFalse(hasDependFrames(job_b));
        assertEquals(20, getTotalDependCount(job_a));
        assertEquals(0, getTotalDependCount(job_b));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(job_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(job_a));
        assertFalse(hasDependFrames(job_b));
        assertEquals(0, getTotalDependCount(job_a));
        assertEquals(0, getTotalDependCount(job_b));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyJobOnLayer() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");

        JobOnLayer depend = new JobOnLayer(job_a, layer_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(job_a));
        assertFalse(hasDependFrames(job_b));
        assertEquals(20, getTotalDependCount(job_a));
        assertEquals(0, getTotalDependCount(job_b));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(layer_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(job_a));
        assertFalse(hasDependFrames(job_b));
        assertEquals(0, getTotalDependCount(job_a));
        assertEquals(0, getTotalDependCount(job_b));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyJobOnFrame() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");
        FrameInterface frame_b = frameDao.findFrame(layer_b, 1);

        JobOnFrame depend = new JobOnFrame(job_a, frame_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(job_a));
        assertFalse(hasDependFrames(job_b));
        assertEquals(20, getTotalDependCount(job_a));
        assertEquals(0, getTotalDependCount(job_b));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(frame_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(job_a));
        assertFalse(hasDependFrames(job_b));
        assertEquals(0, getTotalDependCount(job_a));
        assertEquals(0, getTotalDependCount(job_b));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyLayerOnJob() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");

        LayerOnJob depend = new LayerOnJob(layer_a, job_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(10, getTotalDependCount(layer_a));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(job_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyLayerOnLayer() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");

        LayerOnLayer depend = new LayerOnLayer(layer_a, layer_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(10, getTotalDependCount(layer_a));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(layer_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyLayerOnFrame() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");
        FrameInterface frame_b = frameDao.findFrame(layer_b, 1);

        LayerOnFrame depend = new LayerOnFrame(layer_a, frame_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(10, getTotalDependCount(layer_a));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(frame_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyLayerOnSimFrame() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");
        FrameInterface frame_b = frameDao.findFrame(layer_b, 1);

        LayerOnSimFrame depend = new LayerOnSimFrame(layer_a, frame_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(10, getTotalDependCount(layer_a));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(frame_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyFrameOnJob() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        FrameInterface frame_a = frameDao.findFrame(layer_a, 1);

        FrameOnJob depend = new FrameOnJob(frame_a, job_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(1, getTotalDependCount(layer_a));
        assertTrue(hasDependFrames(frame_a));
        assertEquals(1, getTotalDependCount(frame_a));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(job_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
        assertFalse(hasDependFrames(frame_a));
        assertEquals(0, getTotalDependCount(frame_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyFrameOnLayer() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");
        FrameInterface frame_a = frameDao.findFrame(layer_a, 1);

        FrameOnLayer depend = new FrameOnLayer(frame_a, layer_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(1, getTotalDependCount(layer_a));
        assertTrue(hasDependFrames(frame_a));
        assertEquals(1, getTotalDependCount(frame_a));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(layer_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
        assertFalse(hasDependFrames(frame_a));
        assertEquals(0, getTotalDependCount(frame_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyFrameOnFrame() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");
        FrameInterface frame_a = frameDao.findFrame(layer_a, 1);
        FrameInterface frame_b = frameDao.findFrame(layer_b, 1);

        FrameOnFrame depend = new FrameOnFrame(frame_a, frame_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(1, getTotalDependCount(layer_a));
        assertTrue(hasDependFrames(frame_a));
        assertEquals(1, getTotalDependCount(frame_a));

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(frame_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
        assertFalse(hasDependFrames(frame_a));
        assertEquals(0, getTotalDependCount(frame_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyFrameByFrame() {

        /**
         * A compound depend, like FrameByFrame or PreviousFrame cannot
         * be satisfied by using dependDao.getWhatDependsOn.  You must
         * have a reference to the actual dependency.
         */

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");

        FrameByFrame depend = new FrameByFrame(layer_a, layer_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(10, getTotalDependCount(layer_a));

        LightweightDependency lwd = dependDao.getDepend(depend.getId());
        dependManager.satisfyDepend(lwd);

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyLayerOnLayerAnyFrame() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");

        LayerOnLayer depend = new LayerOnLayer(layer_a, layer_b);
        depend.setAnyFrame(true);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(10, getTotalDependCount(layer_a));

        FrameInterface frame_b = frameDao.findFrame(layer_b, 5);

        for (LightweightDependency lwd: dependDao.getWhatDependsOn(frame_b)) {
            dependManager.satisfyDepend(lwd);
        }

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyPreviousFrame() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");

        PreviousFrame depend = new PreviousFrame(layer_a, layer_b);
        dependManager.createDepend(depend);

        assertTrue(hasDependFrames(layer_a));
        assertEquals(9, getTotalDependCount(layer_a));

        FrameInterface frame_b = frameDao.findFrame(layer_b, 9);
        for (LightweightDependency lwd: dependDao.getWhatDependsOn(frame_b)) {
            dependManager.satisfyDepend(lwd);
            for (FrameDetail f: frameDao.findFrameDetails(
                    new FrameSearch(layer_a))) {
                logger.info(f.getName() + " " + f.state.toString());
            }
        }

        assertTrue(hasDependFrames(layer_a));
        assertEquals(8, getTotalDependCount(layer_a));
    }

    /**
     * In this test, some of the dependOnFrames are already
     * completed.  The FrameOnFrame depends
     * that get setup on those frames should be inactive,
     * and the depend count should not be updated the corresponding
     * dependErFrames.
     */
    @Test
    @Transactional
    @Rollback(true)
    public void testCreateAndSatisfyFrameByFrameParital() {

        JobDetail job_a = getJobA();
        JobDetail job_b = getJobB();
        LayerInterface layer_a = layerDao.findLayer(job_a, "pass_1");
        LayerInterface layer_b = layerDao.findLayer(job_b, "pass_1");

        jdbcTemplate.update(
                "UPDATE frame SET str_state=? WHERE pk_layer=? AND " +
                "int_number IN (1,2,3)",
                FrameState.Succeeded.toString(), layer_b.getLayerId());

        FrameByFrame depend = new FrameByFrame(layer_a, layer_b);
        dependManager.createDepend(depend);

        /** Check the b_active state **/

        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT b_active FROM depend WHERE pk_frame_depend_on=?",
                Integer.class, frameDao.findFrame(layer_b, 1).getFrameId()));

        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT b_active FROM depend WHERE pk_frame_depend_on=?",
                Integer.class, frameDao.findFrame(layer_b, 2).getFrameId()));

        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT b_active FROM depend WHERE pk_frame_depend_on=?",
                Integer.class, frameDao.findFrame(layer_b, 3).getFrameId()));

        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT b_active FROM depend WHERE pk_frame_depend_on=?",
                Integer.class, frameDao.findFrame(layer_b, 4).getFrameId()));

        assertTrue(hasDependFrames(layer_a));
        assertEquals(7, getTotalDependCount(layer_a));

        LightweightDependency lwd = dependDao.getDepend(depend.getId());
        dependManager.satisfyDepend(lwd);

        assertFalse(hasDependFrames(layer_a));
        assertEquals(0, getTotalDependCount(layer_a));
    }
}

