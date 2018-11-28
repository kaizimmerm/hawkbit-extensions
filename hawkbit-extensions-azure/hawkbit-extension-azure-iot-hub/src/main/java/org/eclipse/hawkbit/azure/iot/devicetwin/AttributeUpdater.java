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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.azure.iot.AzureIotHubProperties;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.UpdateMode;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.microsoft.azure.sdk.iot.deps.twin.TwinCollection;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinDevice;
import com.microsoft.azure.sdk.iot.service.devicetwin.Pair;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;

public class AttributeUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(AttributeUpdater.class);

    private final TargetManagement targetManagement;
    private final AzureIotHubProperties properties;
    private final TenantAware tenantAware;
    private final ControllerManagement controllerManagement;

    /**
     * Maximum for target filter queries with auto assign DS Maximum for targets
     * that are fetched in one turn
     */
    private static final int PAGE_SIZE = 1000;

    public AttributeUpdater(final TargetManagement targetManagement, final AzureIotHubProperties properties,
            final TenantAware tenantAware, final ControllerManagement controllerManagement) {
        this.targetManagement = targetManagement;
        this.properties = properties;
        this.tenantAware = tenantAware;
        this.controllerManagement = controllerManagement;

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

                try {
                    final DeviceTwinDevice device = new DeviceTwinDevice(target.getControllerId());
                    deviceTwin.getTwin(device);

                    controllerManagement.updateControllerAttributes(target.getControllerId(),
                            new DeviceTwinConverter(device.getReportedProperties()).getAsAttributes(),
                            UpdateMode.MERGE);

                } catch (IotHubException | IOException e) {
                    LOG.error("Failed to retrieve device data from Azure IoT Hub for: {}", target.getControllerId(), e);
                }
            }
        });

    }

    public static class DeviceTwinConverter {
        private final Set<Pair> properties;

        public DeviceTwinConverter(final Set<Pair> properties) {
            this.properties = properties;
        }

        Map<String, String> getAsAttributes() {
            return properties.stream().map(pair -> convert(new Pair("azureiot#" + pair.getKey(), pair.getValue())))
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(Pair::getKey, pair -> String.valueOf(pair.getValue())));
        }

        List<Pair> convert(final Pair original) {
            if (original.getValue() instanceof TwinCollection) {
                return ((TwinCollection) original.getValue()).entrySet().stream()
                        .map(entry -> convert(new Pair(original.getKey() + "#" + entry.getKey(), entry.getValue())))
                        .flatMap(List::stream).collect(Collectors.toList());
            }

            return Arrays.asList(original);
        }

    }

}
