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

import org.eclipse.hawkbit.azure.iot.registry.AzureIotHawkBitToIoTHubRegistrySynchronizer;
import org.eclipse.hawkbit.azure.iot.registry.AzureIotIoTHubRegistryToHawkbitSynchronizer;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

import com.microsoft.azure.sdk.iot.service.RegistryManager;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;

@Configuration
@EnableConfigurationProperties(AzureIotHubProperties.class)
public class AzureIotHubAutoConfiguration {
    // TODO multi tenancy -> tenant = hub
    // TODO properties sync
    // TODO tags sync
    // TODO all sub features configurable by properties
    // TODO README configuration
    // TODO Unit tests

    @Bean
    RegistryManager registryManager(final AzureIotHubProperties properties) throws IOException {
        return RegistryManager.createFromConnectionString(properties.getIotHubConnectionString());
    }

    @Bean
    DeviceTwin deviceTwin(final AzureIotHubProperties properties) throws IOException {
        return DeviceTwin.createFromConnectionString(properties.getIotHubConnectionString());
    }

    @Bean
    AzureIotHawkBitToIoTHubRegistrySynchronizer azureIotHawkBitToIoTHubRegistrySynchronizer(
            final ServiceMatcher serviceMatcher, final RegistryManager registryManager) {
        return new AzureIotHawkBitToIoTHubRegistrySynchronizer(serviceMatcher, registryManager);
    }

    @Bean
    AzureIotIoTHubRegistryToHawkbitSynchronizer azureIotIoTHubRegistryToHawkbitSynchronizer(
            final ControllerManagement controllerManagement, final TargetManagement targetManagement,
            final DeviceTwin deviceTwin) {
        return new AzureIotIoTHubRegistryToHawkbitSynchronizer(controllerManagement, targetManagement, deviceTwin);

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
