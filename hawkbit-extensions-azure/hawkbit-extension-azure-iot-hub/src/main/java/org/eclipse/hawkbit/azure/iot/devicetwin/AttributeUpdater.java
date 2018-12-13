/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot.devicetwin;

import java.io.IOException;

import org.eclipse.hawkbit.azure.iot.AzureIotHubProperties;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;

public class AttributeUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(AttributeUpdater.class);

    private final TargetManagement targetManagement;
    private final AzureIotHubProperties properties;
    private final TenantAware tenantAware;
    private final DeviceTwinToTargetAttributesSynchronizer deviceTwinToTargetAtrriutesSynchronizer;

    /**
     * Maximum for target filter queries with auto assign DS Maximum for targets
     * that are fetched in one turn
     */
    private static final int PAGE_SIZE = 1000;

    public AttributeUpdater(final TargetManagement targetManagement, final AzureIotHubProperties properties,
            final TenantAware tenantAware,
            final DeviceTwinToTargetAttributesSynchronizer deviceTwinToTargetAtrriutesSynchronizer) {
        this.targetManagement = targetManagement;
        this.properties = properties;
        this.tenantAware = tenantAware;
        this.deviceTwinToTargetAtrriutesSynchronizer = deviceTwinToTargetAtrriutesSynchronizer;

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update() {

        properties.getIoTHubConfigByTenant(tenantAware.getCurrentTenant()).ifPresent(iothub -> {
            DeviceTwin deviceTwin;
            try {
                deviceTwin = DeviceTwin.createFromConnectionString(iothub.getConnectionString());
            } catch (final IOException e) {
                LOG.error("Failed to retrieve Azure IoT Hub connection", e);
                return;
            }

            final PageRequest pageRequest = PageRequest.of(0, PAGE_SIZE);

            final Page<Target> targets = targetManagement.findByControllerAttributesRequested(pageRequest);

            for (final Target target : targets) {
                deviceTwinToTargetAtrriutesSynchronizer.sync(deviceTwin,
                        target.getControllerId());
            }
        });

    }

}
