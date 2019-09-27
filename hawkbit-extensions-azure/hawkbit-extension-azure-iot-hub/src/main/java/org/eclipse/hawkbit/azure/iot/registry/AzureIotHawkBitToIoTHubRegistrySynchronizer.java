/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot.registry;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.eclipse.hawkbit.azure.iot.AzureIotHubProperties;
import org.eclipse.hawkbit.azure.iot.devicetwin.DeviceTwinToTargetAttributesSynchronizer;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
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
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubNotFoundException;

public class AzureIotHawkBitToIoTHubRegistrySynchronizer {
    private static final Logger LOG = LoggerFactory.getLogger(AzureIotHawkBitToIoTHubRegistrySynchronizer.class);

    /**
     * Maximum for target filter queries with auto assign DS Maximum for targets
     * that are fetched in one turn
     */
    private static final int PAGE_SIZE = 1000;

    private final ServiceMatcher serviceMatcher;
    private final AzureIotHubProperties properties;
    private final TargetManagement targetManagement;
    private final DeviceTwinToTargetAttributesSynchronizer deviceTwinToTargetAtrriutesSynchronizer;

    public AzureIotHawkBitToIoTHubRegistrySynchronizer(final ServiceMatcher serviceMatcher,
            final AzureIotHubProperties properties,
            final DeviceTwinToTargetAttributesSynchronizer deviceTwinToTargetAtrriutesSynchronizer,
            final TargetManagement targetManagement) {
        this.serviceMatcher = serviceMatcher;
        this.properties = properties;
        this.deviceTwinToTargetAtrriutesSynchronizer = deviceTwinToTargetAtrriutesSynchronizer;
        this.targetManagement = targetManagement;

    }

    @EventListener(classes = TargetCreatedEvent.class)
    protected void targetCreatedEvent(final TargetCreatedEvent createdEvent) {
        final Target target = createdEvent.getEntity();
        if (isNotFromSelf(createdEvent) || isAzureIoTUri(target.getAddress())) {
            return;
        }

        properties.getIoTHubConfigByTenant(createdEvent.getTenant()).ifPresent(iothub -> {

            if (!iothub.getRegistrySync().isHawkBitToHubEnabled()) {
                return;
            }

            try {
                final RegistryManager registry = RegistryManager
                        .createFromConnectionString(iothub.getConnectionString());
                final SymmetricKey key = new SymmetricKey();

                // TODO think about ways to handle dual key, i.e. teach hawkBit
                // to handle it
                key.setPrimaryKeyFinal(target.getSecurityToken());
                key.setSecondaryKeyFinal(target.getSecurityToken());

                registry.addDevice(Device.createFromId(target.getControllerId(), DeviceStatus.Enabled, key));

            } catch (JsonSyntaxException | IllegalArgumentException | IOException | IotHubException e) {
                LOG.error("Failed to add target {}", target.getControllerId(), e);
            }
        });

    }

    @EventListener(classes = TargetAttributesRequestedEvent.class)
    protected void targetAttributesRequestedEvent(final TargetAttributesRequestedEvent attributesRequestedEvent) {

        if (isNotFromSelf(attributesRequestedEvent) || isNotAzureIoTUri(attributesRequestedEvent.getTargetAddress())
                || isWrongTenant(attributesRequestedEvent.getTenant(), attributesRequestedEvent.getTargetAddress(),
                        properties)) {
            return;
        }

        properties.getIoTHubConfigByTenant(attributesRequestedEvent.getTenant()).ifPresent(iothub -> {
            if (!iothub.getRegistrySync().isHubToHawkBitEnabled()) {
                return;
            }

            DeviceTwin deviceTwin;
            try {
                deviceTwin = DeviceTwin.createFromConnectionString(iothub.getConnectionString());
            } catch (final IOException e) {
                LOG.error("Failed to retrieve Azure IoT Hub connection for tenant {}",
                        attributesRequestedEvent.getTenant(), e);
                return;
            }

            deviceTwinToTargetAtrriutesSynchronizer.sync(deviceTwin, attributesRequestedEvent.getControllerId());
        });
    }

    @EventListener(classes = TargetDeletedEvent.class)
    protected void targetDeletedEvent(final TargetDeletedEvent deletedEvent) {
        if (isNotFromSelf(deletedEvent) || isNotAzureIoTUri(deletedEvent.getTargetAddress())
                || isWrongTenant(deletedEvent.getTenant(), deletedEvent.getTargetAddress(), properties)) {
            return;
        }

        properties.getIoTHubConfigByTenant(deletedEvent.getTenant()).ifPresent(iothub -> {

            if (!iothub.getRegistrySync().isHawkBitToHubEnabled()) {
                return;
            }

            try {
                RegistryManager.createFromConnectionString(iothub.getConnectionString())
                        .removeDevice(deletedEvent.getControllerId());
            } catch (final IotHubNotFoundException nf) {
                LOG.debug("To be deleted device is already deleted in IoT Hub: {}", deletedEvent.getControllerId());
            } catch (JsonSyntaxException | IllegalArgumentException | IOException | IotHubException e) {
                LOG.error("Failed to remove target {}", deletedEvent.getControllerId(), e);
            }
        });
    }

    private boolean isNotFromSelf(final RemoteApplicationEvent event) {
        return serviceMatcher != null && !serviceMatcher.isFromSelf(event);
    }

    // TODO I guess logging and error handling is in order as this is clearly
    // wrong
    private boolean isWrongTenant(final String tenant, final String uri, final AzureIotHubProperties properties) {
        if (uri == null) {
            return false;
        }

        final Optional<Boolean> hubMatches = properties.getIoTHubConfigByTenant(tenant)
                .map(config -> !config.getHubName().equalsIgnoreCase(URI.create(uri).getHost()));

        return hubMatches.isPresent() && hubMatches.get();

    }

    private static boolean isAzureIoTUri(final URI uri) {
        return uri != null && AzureIotIoTHubRegistryToHawkbitSynchronizer.AZURE_IOT_SCHEME.equals(uri.getScheme());
    }

    private static boolean isNotAzureIoTUri(final String uri) {
        return uri != null
                && !AzureIotIoTHubRegistryToHawkbitSynchronizer.AZURE_IOT_SCHEME.equals(URI.create(uri).getScheme());
    }

}
