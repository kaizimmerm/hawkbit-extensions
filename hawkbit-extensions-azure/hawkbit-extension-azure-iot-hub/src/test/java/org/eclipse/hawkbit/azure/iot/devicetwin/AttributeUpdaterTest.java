/**
 * Copyright (c) 2018 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.azure.iot.devicetwin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.hawkbit.azure.iot.devicetwin.AttributeUpdater.DeviceTwinConverter;
import org.junit.Test;

import com.microsoft.azure.sdk.iot.deps.twin.TwinCollection;
import com.microsoft.azure.sdk.iot.service.devicetwin.Pair;

public class AttributeUpdaterTest {
    private static final Set<Pair> TEST_PROPERTIES = new HashSet<Pair>() {
        {
            add(new Pair("Root1", "stringValue"));
            add(new Pair("Root2", new TwinCollection() {
                {
                    put("Value", 500.0);
                    put("Value2", 300.0);
                    put("Inner1", new TwinCollection() {
                        {
                            put("Inner2", "FinalInnerValue");
                        }
                    });
                }
            }));
        }
    };

    @Test
    public void test() {

        final DeviceTwinConverter converterUnderTest = new DeviceTwinConverter(TEST_PROPERTIES);

        final Map<String, String> converted = converterUnderTest.getAsAttributes();

        assertThat(converted.get("azureiot#Root1")).isEqualTo("stringValue");
        assertThat(converted.get("azureiot#Root2#Value")).isEqualTo("500.0");
        assertThat(converted.get("azureiot#Root2#Value2")).isEqualTo("300.0");
        assertThat(converted.get("azureiot#Root2#Inner1#Inner2")).isEqualTo("FinalInnerValue");

    }

}
