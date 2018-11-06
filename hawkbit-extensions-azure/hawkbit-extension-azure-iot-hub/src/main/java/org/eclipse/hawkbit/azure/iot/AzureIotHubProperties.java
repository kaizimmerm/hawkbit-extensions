/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot;

import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hawkbit.azure.iot")
public class AzureIotHubProperties {
    @NotEmpty
    private String iotHubConnectionString;

    public String getIotHubConnectionString() {
        return iotHubConnectionString;
    }

    public void setIotHubConnectionString(final String iotHubConnectionString) {
        this.iotHubConnectionString = iotHubConnectionString;
    }

}
