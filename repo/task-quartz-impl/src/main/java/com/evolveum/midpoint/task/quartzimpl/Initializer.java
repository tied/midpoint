/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.task.quartzimpl;

import com.evolveum.midpoint.xml.ns._public.common.common_3.NodeErrorStateType;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import com.evolveum.midpoint.common.configuration.api.MidpointConfiguration;
import com.evolveum.midpoint.repo.sqlbase.JdbcRepositoryConfiguration;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.TaskManagerInitializationException;
import com.evolveum.midpoint.task.quartzimpl.execution.JobExecutor;
import com.evolveum.midpoint.task.quartzimpl.execution.JobStarter;
import com.evolveum.midpoint.task.quartzimpl.handlers.NoOpTaskHandler;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NodeType;

/**
 * Initializes the task manager.
 */
public class Initializer {

    private static final Trace LOGGER = TraceManager.getTrace(Initializer.class);

    private final TaskManagerQuartzImpl taskManager;

    Initializer(TaskManagerQuartzImpl taskManager) {
        this.taskManager = taskManager;
    }

    public void init(OperationResult result) throws TaskManagerInitializationException {

        MidpointConfiguration midpointConfiguration = taskManager.getMidpointConfiguration();

        LOGGER.info("Task Manager initialization.");

        // get the configuration (general section + JDBC section as well)
        TaskManagerConfiguration configuration = taskManager.getConfiguration();
        configuration.checkAllowedKeys(midpointConfiguration);
        configuration.setBasicInformation(midpointConfiguration, result);
        configuration.validateBasicInformation();

        LOGGER.info("Task Manager: Quartz Job Store: "
                + (configuration.isJdbcJobStore() ? "JDBC" : "in-memory") + ", "
                + (configuration.isClustered() ? "" : "NOT ") + "clustered. Threads: "
                + configuration.getThreads());

        if (configuration.isJdbcJobStore()) {
            // Let's find Quartz JDBC setup fallback (which will be used very likely)
            JdbcRepositoryConfiguration jdbcConfig = null;
            try {
                jdbcConfig = taskManager.getBeanFactory().getBean(JdbcRepositoryConfiguration.class);
            } catch (NoSuchBeanDefinitionException e) {
                LOGGER.info("JdbcRepositoryConfiguration is not available, JDBC Job Store"
                        + " configuration will be taken from taskManager section only.");
                LOGGER.trace("Reason is", e);
            }

            configuration.setJdbcJobStoreInformation(midpointConfiguration, jdbcConfig);
            configuration.validateJdbcJobStoreInformation();
        }

        // register node
        NodeType node = taskManager.getClusterManager().createOrUpdateNodeInRepo(result);     // may throw initialization exception
        if (!taskManager.getConfiguration().isTestMode()) {  // in test mode do not start cluster manager thread nor verify cluster config
            taskManager.getClusterManager().checkClusterConfiguration(result);      // Does not throw exceptions. Sets the ERROR state if necessary, however.
        }

        NoOpTaskHandler.instantiateAndRegister(taskManager);
        JobExecutor.setTaskManagerQuartzImpl(taskManager);       // unfortunately, there seems to be no clean way of letting jobs know the taskManager
        JobStarter.setTaskManagerQuartzImpl(taskManager);        // the same here

        taskManager.getExecutionManager().initializeLocalScheduler();
        if (taskManager.getLocalNodeErrorStatus() == NodeErrorStateType.OK) {
            taskManager.getExecutionManager().setLocalExecutionLimitations(node);
        } else {
            taskManager.getExecutionManager().shutdownLocalSchedulerChecked();
        }

        // populate the scheduler with jobs (if RAM-based), or synchronize with midPoint repo
        if (!taskManager.getExecutionManager().synchronizeJobStores(result)) {
            if (!configuration.isJdbcJobStore()) {
                LOGGER.error("Some or all tasks could not be imported from midPoint repository to Quartz job store. They will therefore not be executed.");
            } else {
                LOGGER.warn("Some or all tasks could not be synchronized between midPoint repository and Quartz job store. They may not function correctly.");
            }
        }

        LOGGER.trace("Quartz scheduler initialized (not yet started, however)");
        LOGGER.info("Task Manager initialized");
    }
}
