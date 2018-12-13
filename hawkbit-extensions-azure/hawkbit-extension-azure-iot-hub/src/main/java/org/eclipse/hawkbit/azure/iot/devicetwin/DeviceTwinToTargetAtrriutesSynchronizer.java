package org.eclipse.hawkbit.azure.iot.devicetwin;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.UpdateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.microsoft.azure.sdk.iot.deps.twin.TwinCollection;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinDevice;
import com.microsoft.azure.sdk.iot.service.devicetwin.Pair;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;

public class DeviceTwinToTargetAtrriutesSynchronizer {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceTwinToTargetAtrriutesSynchronizer.class);

    private final ControllerManagement controllerManagement;
    private final TargetManagement targetManagement;

    public DeviceTwinToTargetAtrriutesSynchronizer(final ControllerManagement controllerManagement,
            final TargetManagement targetManagement) {
        this.controllerManagement = controllerManagement;
        this.targetManagement = targetManagement;
    }

    public void sync(final DeviceTwin deviceTwin, final String controllerId) {
        try {
            final DeviceTwinDevice device = new DeviceTwinDevice(controllerId);
            deviceTwin.getTwin(device);

            final Set<Pair> reportedProperties = device.getReportedProperties();

            if (!CollectionUtils.isEmpty(reportedProperties) && targetManagement.existsByControllerId(controllerId)) {
                controllerManagement.updateControllerAttributes(controllerId, getAsAttributes(reportedProperties),
                        UpdateMode.MERGE);
            }

        } catch (IotHubException | IOException e) {
            LOG.error("Failed to retrieve device data from Azure IoT Hub for: {}", controllerId, e);
        }
    }

    static Map<String, String> getAsAttributes(final Set<Pair> properties) {
        return properties.stream().map(pair -> convert(new Pair("azureiot#" + pair.getKey(), pair.getValue())))
                .flatMap(List::stream).collect(Collectors.toMap(Pair::getKey, pair -> String.valueOf(pair.getValue())));
    }

    private static List<Pair> convert(final Pair original) {
        if (original.getValue() instanceof TwinCollection) {
            return ((TwinCollection) original.getValue()).entrySet().stream()
                    .map(entry -> convert(new Pair(original.getKey() + "#" + entry.getKey(), entry.getValue())))
                    .flatMap(List::stream).collect(Collectors.toList());
        }

        return Arrays.asList(original);
    }
}
