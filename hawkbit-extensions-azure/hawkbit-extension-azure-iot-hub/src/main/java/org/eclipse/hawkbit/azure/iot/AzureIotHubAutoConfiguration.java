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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.microsoft.azure.sdk.iot.service.RegistryManager;

@Configuration
@EnableConfigurationProperties(AzureIotHubProperties.class)
public class AzureIotHubAutoConfiguration {

    @Bean
    RegistryManager registryManager(final AzureIotHubProperties properties) throws IOException {
        return RegistryManager.createFromConnectionString(properties.getIotHubConnectionString());
    }

    @Bean
    AzureIotRegistrySynchronizer azureIotRegistrySynchronizer(final ServiceMatcher serviceMatcher,
            final RegistryManager registryManager) {
        return new AzureIotRegistrySynchronizer(serviceMatcher, registryManager);
    }

}
