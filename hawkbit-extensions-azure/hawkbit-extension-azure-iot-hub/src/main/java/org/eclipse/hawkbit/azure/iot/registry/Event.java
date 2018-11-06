/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot.registry;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {

    @JsonProperty
    @NotEmpty
    private String eventType;

    @JsonProperty
    @NotNull
    private Data data;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    public Data getData() {
        return data;
    }

    public void setData(final Data data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Data {
        @JsonProperty
        @NotEmpty
        private String deviceId;

        @JsonProperty
        @NotEmpty
        private String hubName;

        public Data() {
            // For jackson
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(final String deviceId) {
            this.deviceId = deviceId;
        }

        public String getHubName() {
            return hubName;
        }

        public void setHubName(final String hubName) {
            this.hubName = hubName;
        }

    }
}
