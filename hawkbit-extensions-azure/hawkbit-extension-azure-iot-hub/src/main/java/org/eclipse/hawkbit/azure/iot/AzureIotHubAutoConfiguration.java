/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot;

import org.eclipse.hawkbit.azure.iot.devicetwin.AttributeUpdater;
import org.eclipse.hawkbit.azure.iot.devicetwin.AttributesUpdateScheduler;
import org.eclipse.hawkbit.azure.iot.devicetwin.DeviceTwinToTargetAtrriutesSynchronizer;
import org.eclipse.hawkbit.azure.iot.registry.AzureIotHawkBitToIoTHubRegistrySynchronizer;
import org.eclipse.hawkbit.azure.iot.registry.AzureIotIoTHubRegistryToHawkbitSynchronizer;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AzureIotHubProperties.class)
public class AzureIotHubAutoConfiguration {
    // TODO properties sync
    // TODO status snyc?
    // TODO tags sync
    // TODO README configuration
    // TODO Unit tests
    // TODO clear concept for sync vs device management including a definition
    // of scenarios -> DDI/DMF with Azure IoT in parallel and Azure IoT below
    // hawkBit

    @Bean
    AzureIotHawkBitToIoTHubRegistrySynchronizer azureIotHawkBitToIoTHubRegistrySynchronizer(
            final ServiceMatcher serviceMatcher, final AzureIotHubProperties properties,
            final DeviceTwinToTargetAtrriutesSynchronizer deviceTwinToTargetAtrriutesSynchronizer,
            final TargetManagement targetManagement) {
        return new AzureIotHawkBitToIoTHubRegistrySynchronizer(serviceMatcher, properties,
                deviceTwinToTargetAtrriutesSynchronizer, targetManagement);
    }

    @Bean
    DeviceTwinToTargetAtrriutesSynchronizer deviceTwinToTargetAtrriutesSynchronizer(
            final ControllerManagement controllerManagement, final TargetManagement targetManagement) {
        return new DeviceTwinToTargetAtrriutesSynchronizer(controllerManagement, targetManagement);
    }

    @Bean
    AzureIotIoTHubRegistryToHawkbitSynchronizer azureIotIoTHubRegistryToHawkbitSynchronizer(
            final ControllerManagement controllerManagement, final TargetManagement targetManagement,
            final AzureIotHubProperties properties, final SystemSecurityContext systemSecurityContext,
            final EntityFactory entityFactory) {
        return new AzureIotIoTHubRegistryToHawkbitSynchronizer(controllerManagement, targetManagement, properties,
                systemSecurityContext, entityFactory);

    }

    @Bean
    AttributesUpdateScheduler attributesUpdateScheduler(final SystemManagement systemManagement,
            final SystemSecurityContext systemSecurityContext, final LockRegistry lockRegistry,
            final AttributeUpdater attributeUpdater) {
        return new AttributesUpdateScheduler(systemManagement, systemSecurityContext, lockRegistry, attributeUpdater);
    }

    @Bean
    AttributeUpdater attributeUpdater(final TargetManagement targetManagement, final AzureIotHubProperties properties,
            final TenantAware tenantAware,
            final DeviceTwinToTargetAtrriutesSynchronizer deviceTwinToTargetAtrriutesSynchronizer) {
        return new AttributeUpdater(targetManagement, properties, tenantAware, deviceTwinToTargetAtrriutesSynchronizer);
    }

    @Bean
    KafkaListenerContainerFactory<?> kafkaListenerContainerFactory(
            final ConsumerFactory<Integer, String> kafkaConsumerFactory) {
        final ConcurrentKafkaListenerContainerFactory<Integer, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory);
        factory.setBatchListener(true);
        factory.setMessageConverter(new BatchMessagingMessageConverter(converter()));
        return factory;
    }

    @Bean
    StringJsonMessageConverter converter() {
        return new StringJsonMessageConverter();
    }
}
