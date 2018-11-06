/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot.devicetwin;

import java.util.concurrent.locks.Lock;

import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.Scheduled;

public class AttributesUpdateScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttributesUpdateScheduler.class);

    private static final String PROP_SCHEDULER_DELAY_PLACEHOLDER = "${hawkbit.azure.iot.scheduler.fixedDelay:2000}";

    private final SystemManagement systemManagement;

    private final SystemSecurityContext systemSecurityContext;

    private final LockRegistry lockRegistry;

    /**
     * Instantiates a new AttributesUpdateScheduler
     * 
     */
    public AttributesUpdateScheduler(final SystemManagement systemManagement,
            final SystemSecurityContext systemSecurityContext, final LockRegistry lockRegistry) {
        this.systemManagement = systemManagement;
        this.systemSecurityContext = systemSecurityContext;
        this.lockRegistry = lockRegistry;
    }

    /**
     * Scheduler method called by the spring-async mechanism. Retrieves all
     * tenants from the {@link SystemManagement#findTenants()} and runs for each
     * tenant the auto assignments defined in the target filter queries
     * {@link SystemSecurityContext}.
     */
    @Scheduled(initialDelayString = PROP_SCHEDULER_DELAY_PLACEHOLDER, fixedDelayString = PROP_SCHEDULER_DELAY_PLACEHOLDER)
    public void attributeUpdateScheduler() {

        systemSecurityContext.runAsSystem(this::executeAttributeUpdate);
    }

    @SuppressWarnings("squid:S3516")
    private Object executeAttributeUpdate() {
        // workaround eclipselink that is currently not possible to
        // execute a query without multitenancy if MultiTenant
        // annotation is used.
        // https://bugs.eclipse.org/bugs/show_bug.cgi?id=355458. So
        // iterate through all tenants and execute the rollout check for
        // each tenant separately.
        final Lock lock = lockRegistry.obtain("azureIoTAttributeSync");
        if (!lock.tryLock()) {
            return null;
        }

        try {
            // FIXME
            // systemManagement.forEachTenant(tenant ->
            // autoAssignChecker.check());
        } finally {
            lock.unlock();
        }

        return null;
    }
}
