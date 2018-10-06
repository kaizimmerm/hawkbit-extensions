/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot;

import java.io.IOException;

import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.model.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.context.event.EventListener;

import com.google.gson.JsonSyntaxException;
import com.microsoft.azure.sdk.iot.service.Device;
import com.microsoft.azure.sdk.iot.service.DeviceStatus;
import com.microsoft.azure.sdk.iot.service.RegistryManager;
import com.microsoft.azure.sdk.iot.service.auth.SymmetricKey;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;

public class AzureIotRegistrySynchronizer {
    private static final Logger LOG = LoggerFactory.getLogger(AzureIotRegistrySynchronizer.class);

    private final ServiceMatcher serviceMatcher;
    private final RegistryManager registryManager;

    AzureIotRegistrySynchronizer(final ServiceMatcher serviceMatcher, final RegistryManager registryManager) {
        this.serviceMatcher = serviceMatcher;
        this.registryManager = registryManager;
    }

    @EventListener(classes = TargetCreatedEvent.class)
    protected void targetCreatedEvent(final TargetCreatedEvent createdEvent) {
        if (isNotFromSelf(createdEvent)) {
            return;
        }

        final Target target = createdEvent.getEntity();
        final SymmetricKey key = new SymmetricKey();
        key.setPrimaryKey(target.getSecurityToken());

        try {
            registryManager.addDevice(Device.createFromId(target.getControllerId(), DeviceStatus.Enabled, key));
        } catch (JsonSyntaxException | IllegalArgumentException | IOException | IotHubException e) {
            LOG.error("Failed to add target {}", target.getControllerId(), e);
        }
    }

    @EventListener(classes = TargetDeletedEvent.class)
    protected void targetDeletedEvent(final TargetDeletedEvent deletedEvent) {
        if (isNotFromSelf(deletedEvent)) {
            return;
        }

        try {
            registryManager.removeDevice(deletedEvent.getControllerId());
        } catch (IOException | IotHubException e) {
            LOG.error("Failed to remove target {}", deletedEvent.getControllerId(), e);
        }
    }

    private boolean isNotFromSelf(final RemoteApplicationEvent event) {
        return serviceMatcher != null && !serviceMatcher.isFromSelf(event);
    }

}
