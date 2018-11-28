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
import java.util.stream.Collectors;

import org.eclipse.hawkbit.azure.iot.AzureIotHubProperties;
import org.eclipse.hawkbit.azure.iot.AzureIotHubProperties.IoTHubConfig;
import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
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

import com.microsoft.azure.sdk.iot.service.Device;
import com.microsoft.azure.sdk.iot.service.RegistryManager;

public class AzureIotIoTHubRegistryToHawkbitSynchronizer {
    static final String AZURE_IOT_SCHEME = "azureiot";

    private static final String EVENT_MICROSOFT_DEVICES_DEVICE_DELETED = "Microsoft.Devices.DeviceDeleted";

    private static final String EVENT_MICROSOFT_DEVICES_DEVICE_CREATED = "Microsoft.Devices.DeviceCreated";

    private static final String EVENT_MICROSOFT_DEVICES_DEVICE_CONNECTED = "Microsoft.Devices.DeviceConnected";

    private static final Logger LOG = LoggerFactory.getLogger(AzureIotIoTHubRegistryToHawkbitSynchronizer.class);

    private final ControllerManagement controllerManagement;
    private final TargetManagement targetManagement;
    private final AzureIotHubProperties properties;
    private final SystemSecurityContext systemSecurityContext;
    private final EntityFactory entityFactory;

    public AzureIotIoTHubRegistryToHawkbitSynchronizer(final ControllerManagement controllerManagement,
            final TargetManagement targetManagement, final AzureIotHubProperties properties,
            final SystemSecurityContext systemSecurityContext, final EntityFactory entityFactory) {
        this.controllerManagement = controllerManagement;
        this.targetManagement = targetManagement;
        this.properties = properties;
        this.systemSecurityContext = systemSecurityContext;
        this.entityFactory = entityFactory;
    }

    @KafkaListener(topics = "iothub", groupId = "iothub")
    public void processMessage(final List<List<Event>> batch) {

        batch.stream().flatMap(List::stream).collect(Collectors.groupingBy(event -> event.getData().getHubName()))
                .entrySet().forEach(entry ->

                properties.getTenantByHubName(entry.getKey()).ifPresent(tenant -> {
                    properties.getIoTHubConfigByTenant(tenant).ifPresent(iotHubConfig -> {

                        if (!properties.getIoTHubConfigByTenant(tenant).get().getRegistrySync()
                                .isHubToHawkBitEnabled()) {
                            return;
                        }

                        final SecurityContext oldContext = SecurityContextHolder.getContext();
                        try {
                            setTenantSecurityContext(tenant);

                            entry.getValue().stream().forEach(event -> processEvent(event, iotHubConfig));
                        } finally {
                            SecurityContextHolder.setContext(oldContext);
                        }

                    });
                }));

    }

    private void processEvent(final Event event, final IoTHubConfig iotHubConfig) {
        LOG.debug("Received event {} from Azure IoT Hub {} for device {}", event.getEventType(),
                event.getData().getHubName(), event.getData().getDeviceId());

        switch (event.getEventType()) {
        case EVENT_MICROSOFT_DEVICES_DEVICE_CREATED:
            final boolean targetExists = systemSecurityContext
                    .runAsSystem(() -> targetManagement.existsByControllerId(event.getData().getDeviceId()));

            if (!targetExists) {
                systemSecurityContext.runAsSystem(() -> {
                    final RegistryManager registry = RegistryManager
                            .createFromConnectionString(iotHubConfig.getConnectionString());

                    final Device device = registry.getDevice(event.getData().getDeviceId());

                    targetManagement.create(entityFactory.target().create().controllerId(event.getData().getDeviceId())
                            .address(IpUtil.createUri(AZURE_IOT_SCHEME, event.getData().getHubName()).toString())
                            .securityToken(device.getPrimaryKey()));

                    return null;
                });
            }

            // no break: need to update the targetAddress

        case EVENT_MICROSOFT_DEVICES_DEVICE_CONNECTED:
            // TODO timing issue with created?
            controllerManagement.findOrRegisterTargetIfItDoesNotexist(event.getData().getDeviceId(),
                    IpUtil.createUri(AZURE_IOT_SCHEME, event.getData().getHubName()));
            break;
        case EVENT_MICROSOFT_DEVICES_DEVICE_DELETED:
            systemSecurityContext.runAsSystem(() -> {
                if (targetManagement.existsByControllerId(event.getData().getDeviceId())) {
                    targetManagement.deleteByControllerID(event.getData().getDeviceId());
                }

                return null;
            });
            break;

        default:
            LOG.debug("Got unknown event {}", event.getEventType());
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
