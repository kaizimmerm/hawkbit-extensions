/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot.registry;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;

public class AzureIotIoTHubRegistryToHawkbitSynchronizer {
    static final String AZURE_IOT_SCHEME = "azureiot";

    private static final String EVENT_MICROSOFT_DEVICES_DEVICE_DELETED = "Microsoft.Devices.DeviceDeleted";

    private static final String EVENT_MICROSOFT_DEVICES_DEVICE_CREATED = "Microsoft.Devices.DeviceCreated";

    private static final String EVENT_MICROSOFT_DEVICES_DEVICE_CONNECTED = "Microsoft.Devices.DeviceConnected";

    private static final Logger LOG = LoggerFactory.getLogger(AzureIotIoTHubRegistryToHawkbitSynchronizer.class);

    private final ControllerManagement controllerManagement;
    private final TargetManagement targetManagement;
    private final DeviceTwin deviceTwin;

    public AzureIotIoTHubRegistryToHawkbitSynchronizer(final ControllerManagement controllerManagement,
            final TargetManagement targetManagement, final DeviceTwin deviceTwin) {
        this.controllerManagement = controllerManagement;
        this.targetManagement = targetManagement;
        this.deviceTwin = deviceTwin;
    }

    // FIXME: configurable per tenant
    @KafkaListener(topics = "iothub", groupId = "iothub")
    public void processMessage(final List<List<Event>> batch) {
        final SecurityContext oldContext = SecurityContextHolder.getContext();
        try {
            setTenantSecurityContext("DEFAULT");

            batch.forEach(events -> events.forEach(this::processEvent));
        } finally {
            SecurityContextHolder.setContext(oldContext);
        }
    }

    private void processEvent(final Event event) {
        LOG.debug("Received event {} from Azure IoT Hub {} for device {}", event.getEventType(),
                event.getData().getHubName(), event.getData().getDeviceId());

        switch (event.getEventType()) {
        case EVENT_MICROSOFT_DEVICES_DEVICE_CREATED:
        case EVENT_MICROSOFT_DEVICES_DEVICE_CONNECTED:
            controllerManagement.findOrRegisterTargetIfItDoesNotexist(event.getData().getDeviceId(),
                    IpUtil.createUri(AZURE_IOT_SCHEME, event.getData().getHubName()));

            // TODO: get reported properties
            // try {
            // final DeviceTwinDevice device = new
            // DeviceTwinDevice(event.getData().getDeviceId());
            // deviceTwin.getTwin(device);
            //
            // device.getReportedProperties()
            //
            // controllerManagement.findOrRegisterTargetIfItDoesNotexist(device.getDeviceId()
            //
            // } catch (IotHubException | IOException e) {
            // LOG.error("Failed to retrieve device data from Azure IoT Hub for:
            // {}", event.getData().getDeviceId(),
            // e);
            // }

            break;
        case EVENT_MICROSOFT_DEVICES_DEVICE_DELETED:
            if (targetManagement.existsByControllerId(event.getData().getDeviceId())) {
                targetManagement.deleteByControllerID(event.getData().getDeviceId());
            }
            break;
        }
    }

    private static void setTenantSecurityContext(final String tenantId) {
        final AnonymousAuthenticationToken authenticationToken = new AnonymousAuthenticationToken(
                UUID.randomUUID().toString(), "Azure.IoT-Controller",
                Collections.singletonList(new SimpleGrantedAuthority(SpringEvalExpressions.CONTROLLER_ROLE_ANONYMOUS)));
        authenticationToken.setDetails(new TenantAwareAuthenticationDetails(tenantId, true));
        setSecurityContext(authenticationToken);
    }

    private static void setSecurityContext(final Authentication authentication) {
        final SecurityContextImpl securityContextImpl = new SecurityContextImpl();
        securityContextImpl.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContextImpl);
    }
}
