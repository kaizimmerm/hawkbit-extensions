/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hawkbit.azure.iot")
public class AzureIotHubProperties {
    // TODO Consider migrating this to TenantConfiguration
    @NotEmpty
    private Map<String, IoTHubConfig> iotHubs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public Map<String, IoTHubConfig> getIotHubs() {
        return iotHubs;
    }

    public void setIotHubs(final Map<String, IoTHubConfig> iotHubs) {
        this.iotHubs = iotHubs;
    }

    public Optional<IoTHubConfig> getIoTHubConfigByTenant(final String tenant) {
        return Optional.ofNullable(iotHubs.get(tenant));
    }

    public Optional<String> getTenantByHubName(final String hubName) {
        return iotHubs.entrySet().stream().filter(entry -> entry.getValue().getHubName().equalsIgnoreCase(hubName))
                .findAny().map(e -> e.getKey().toUpperCase());
    }

    public static class IoTHubConfig {
        @NotEmpty
        private String hubName;

        @NotEmpty
        private String connectionString;

        private RegistrySync registrySync = new RegistrySync();

        public String getHubName() {
            return hubName;
        }

        public void setHubName(final String hubName) {
            this.hubName = hubName;
        }

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(final String connectionString) {
            this.connectionString = connectionString;
        }

        public RegistrySync getRegistrySync() {
            return registrySync;
        }

        public void setRegistrySync(final RegistrySync registrySync) {
            this.registrySync = registrySync;
        }

        public static class RegistrySync {
            private boolean hubToHawkBitEnabled = true;
            private boolean hawkBitToHubEnabled = true;

            public boolean isHubToHawkBitEnabled() {
                return hubToHawkBitEnabled;
            }

            public void setHubToHawkBitEnabled(final boolean hubToHawkBitEnabled) {
                this.hubToHawkBitEnabled = hubToHawkBitEnabled;
            }

            public boolean isHawkBitToHubEnabled() {
                return hawkBitToHubEnabled;
            }

            public void setHawkBitToHubEnabled(final boolean hawkBitToHubEnabled) {
                this.hawkBitToHubEnabled = hawkBitToHubEnabled;
            }

        }

    }

}
