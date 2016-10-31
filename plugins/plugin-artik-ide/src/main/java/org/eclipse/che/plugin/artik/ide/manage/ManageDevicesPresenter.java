/*******************************************************************************
 * Copyright (c) 2016 Samsung Electronics Co., Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - Initial implementation
 *   Samsung Electronics Co., Ltd. - Initial implementation
 *******************************************************************************/
package org.eclipse.che.plugin.artik.ide.manage;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.machine.shared.dto.MachineConfigDto;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.MachineLimitsDto;
import org.eclipse.che.api.machine.shared.dto.MachineSourceDto;
import org.eclipse.che.api.machine.shared.dto.recipe.RecipeDescriptor;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.dialogs.CancelCallback;
import org.eclipse.che.ide.api.dialogs.ConfirmCallback;
import org.eclipse.che.ide.api.dialogs.DialogFactory;
import org.eclipse.che.ide.api.machine.events.MachineStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateHandler;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.api.workspace.event.MachineStatusChangedEvent;
import org.eclipse.che.ide.collections.Jso;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.extension.machine.client.inject.factories.EntityFactory;
import org.eclipse.che.ide.util.StringUtils;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.plugin.artik.ide.ArtikLocalizationConstant;
import org.eclipse.che.plugin.artik.ide.discovery.DeviceDiscoveryServiceClient;
import org.eclipse.che.plugin.artik.ide.machine.DeviceServiceClient;
import org.eclipse.che.plugin.artik.ide.profile.SoftwareManager;
import org.eclipse.che.plugin.artik.shared.dto.ArtikDeviceDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.api.core.model.machine.MachineStatus.RUNNING;
import static org.eclipse.che.ide.api.machine.events.MachineStateEvent.MachineAction.DESTROYED;
import static org.eclipse.che.plugin.artik.shared.Constants.ARTIK_DEVICE_STATUS_CHANNEL;

/**
 * Presenter for managing Artik devices.
 *
 * @author Vitaliy Guliy
 * @author Valeriy Svydenko
 * @author Ann Shumilova
 */
public class ManageDevicesPresenter implements ManageDevicesView.ActionDelegate,
                                               WsAgentStateHandler,
                                               MachineStatusChangedEvent.Handler {
    public final static String ARTIK_CATEGORY = "artik";
    public final static String SSH_CATEGORY   = "ssh-config";
    public final static String DEFAULT_NAME   = "artik_device";
    public final static String VALID_NAME     = "[\\w-]*";

    private final ManageDevicesView               view;
    private final DeviceStatusSubscriptionHandler deviceStatusSubscriptionHandler;
    private final DtoFactory                      dtoFactory;
    private final EntityFactory                   entityFactory;
    private final DeviceServiceClient             deviceServiceClient;
    private final DialogFactory                   dialogFactory;
    private final NotificationManager             notificationManager;
    private final ArtikLocalizationConstant       locale;
    private final DeviceDiscoveryServiceClient    deviceDiscoveryService;
    private final EventBus                        eventBus;
    private final MessageBusProvider              messageBusProvider;
    private final SoftwareManager                 softwareManager;

    private final List<Device>            devices  = new ArrayList<>();
    private final Map<String, MachineDto> machines = new HashMap<>();

    private Device             selectedDevice;
    /* Notification informing connecting to the target is in progress */
    private StatusNotification connectNotification;
    /* Name currently connecting target  */
    private String             connectTargetName;

    @Inject
    public ManageDevicesPresenter(final ManageDevicesView view,
                                  final EntityFactory entityFactory,
                                  final DeviceStatusSubscriptionHandler deviceStatusSubscriptionHandler,
                                  final DtoFactory dtoFactory,
                                  final DeviceServiceClient deviceServiceClient,
                                  final DialogFactory dialogFactory,
                                  final NotificationManager notificationManager,
                                  final ArtikLocalizationConstant locale,
                                  final DeviceDiscoveryServiceClient deviceDiscoveryService,
                                  final EventBus eventBus,
                                  final MessageBusProvider messageBusProvider,
                                  final SoftwareManager softwareManager) {
        this.view = view;
        this.entityFactory = entityFactory;
        this.deviceStatusSubscriptionHandler = deviceStatusSubscriptionHandler;
        this.dtoFactory = dtoFactory;
        this.deviceServiceClient = deviceServiceClient;
        this.dialogFactory = dialogFactory;
        this.notificationManager = notificationManager;
        this.locale = locale;
        this.deviceDiscoveryService = deviceDiscoveryService;
        this.eventBus = eventBus;
        this.messageBusProvider = messageBusProvider;
        this.softwareManager = softwareManager;

        view.setDelegate(this);

        eventBus.addHandler(WsAgentStateEvent.TYPE, this);
        eventBus.addHandler(MachineStatusChangedEvent.TYPE, this);
    }

    @Override
    public void onWsAgentStarted(WsAgentStateEvent event) {
        checkArtikMachineExists();
        subscribeOnDeviceStatusChanel();
    }

    private void checkArtikMachineExists() {
        deviceServiceClient.getDevices().then(new Operation<List<MachineDto>>() {
            @Override
            public void apply(List<MachineDto> arg) throws OperationException {
                for (MachineDto machine : arg) {
                    if (RUNNING.equals(machine.getStatus())) {
                        eventBus.fireEvent(new MachineStateEvent(entityFactory.createMachine(machine),
                                                                 MachineStateEvent.MachineAction.RUNNING));
                    }
                }
            }
        });
    }

    private void subscribeOnDeviceStatusChanel() {
        try {
            messageBusProvider.getMachineMessageBus().subscribe(ARTIK_DEVICE_STATUS_CHANNEL, deviceStatusSubscriptionHandler);
        } catch (WebSocketException e) {
            Log.error(getClass(), e);
        }
    }

    @Override
    public void onWsAgentStopped(WsAgentStateEvent event) {
    }

    /**
     * Opens Manage devices popup.
     */
    public void edit() {
        view.show();
        view.clear();

        discoverDevices();

        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
                updateDevices(null);
            }
        });
    }

    /**
     * Discovers connected devices.
     */
    private void discoverDevices() {
        Promise<List<ArtikDeviceDto>> promise = deviceDiscoveryService.getDevices();

        promise.then(new Operation<List<ArtikDeviceDto>>() {
            @Override
            public void apply(List<ArtikDeviceDto> devices) throws OperationException {
                List<String> hosts = new ArrayList<String>();
                for (ArtikDeviceDto device : devices) {
                    hosts.add(device.getIPAddress());
                }
                view.setHosts(hosts);
            }
        });

        promise.catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError e) throws OperationException {
                view.setHosts(null);
                Log.error(ManageDevicesPresenter.class, "Failed to discover devices. " + e.getMessage());
            }
        });
    }

    /**
     * Determines whether machine is running or not.
     *
     * @return true for running machine
     */
    private boolean isMachineRunning(MachineDto machine) {
        return machine != null && machine.getStatus() == RUNNING;
    }

    /**
     * Restore device properties from its recipe.
     *
     * @param device
     *         device to restore
     */
    private void restoreDevice(Device device) {
        if (device.getScript() == null) {
            return;
        }

        try {
            JSONObject json = JSONParser.parseStrict(device.getScript()).isObject();

            if (json.get("host") != null) {
                String host = json.get("host").isString().stringValue();
                device.setHost(host);
            }

            if (json.get("port") != null) {
                String port = json.get("port").isString().stringValue();
                device.setPort(port);
            }

            if (json.get("username") != null) {
                String username = json.get("username").isString().stringValue();
                device.setUserName(username);
            }

            if (json.get("password") != null) {
                String password = json.get("password").isString().stringValue();
                device.setPassword(password);
            }

            if (json.get("replicationFolder") != null) {
                String syncFolder = json.get("replicationFolder").isString().stringValue();
                device.setReplicationFolder(syncFolder);
            }
        } catch (Exception e) {
            Log.error(ManageDevicesPresenter.class, "Unable to parse recipe JSON. " + e.getMessage());
        }
    }

    @Override
    public void onCloseClicked() {
        view.hide();
    }

    @Override
    public void onAddDevice(String category) {
        this.selectedDevice = null;
        String deviceName = generateDeviceName();
        Device device = new Device(deviceName, ARTIK_CATEGORY);
        device.setHost("");
        device.setPort("22");
        device.setUserName("root");
        device.setPassword("");
        device.setReplicationFolder("/root");
        device.setDirty(true);
        device.setConnected(false);
        devices.add(device);

        view.showDevices(devices);
        view.selectDevice(device);
    }

    @Override
    public void onDeviceSelected(Device device) {
        if (device == null) {
            view.showHintPanel();
            return;
        }

        restoreDevice(device);

        view.showPropertiesPanel();
        view.setDeviceName(device.getName());
        view.setHost(device.getHost());
        view.setPort(device.getPort());
        view.setUserName(device.getUserName());
        view.setPassword(device.getPassword());
        view.setReplicationFolder(device.getReplicationFolder());

        view.selectDeviceName();

        selectedDevice = device;
        updateButtons();
    }

    @Override
    public void onDeviceNameChanged(String value) {
        if (selectedDevice.getName().equals(value)) {
            return;
        }

        selectedDevice.setName(value);
        selectedDevice.setDirty(true);
        updateButtons();
    }

    @Override
    public void onHostChanged(String value) {
        if (selectedDevice.getHost().equals(value)) {
            return;
        }

        selectedDevice.setHost(value);
        selectedDevice.setDirty(true);
        updateButtons();
    }

    @Override
    public void onPortChanged(String value) {
        if (selectedDevice.getPort().equals(value)) {
            return;
        }

        selectedDevice.setPort(value);
        selectedDevice.setDirty(true);
        updateButtons();
    }

    @Override
    public void onUserNameChanged(String value) {
        if (selectedDevice.getUserName().equals(value)) {
            return;
        }

        selectedDevice.setUserName(value);
        selectedDevice.setDirty(true);
        updateButtons();
    }

    @Override
    public void onPasswordChanged(String value) {
        if (selectedDevice.getPassword().equals(value)) {
            return;
        }

        selectedDevice.setPassword(value);
        selectedDevice.setDirty(true);
        updateButtons();
    }

    @Override
    public void onReplicationFolderChanged(String value) {
        if (selectedDevice.getReplicationFolder().equals(value)) {
            return;
        }

        selectedDevice.setReplicationFolder(value);
        selectedDevice.setDirty(true);
        updateButtons();
    }

    @Override
    public void onCancelClicked() {
        if (selectedDevice.getRecipe() == null) {
            devices.remove(selectedDevice);
            view.showDevices(devices);

            view.selectDevice(null);
            view.showHintPanel();

            return;
        }

        selectedDevice.setName(selectedDevice.getRecipe().getName());
        restoreDevice(selectedDevice);
        selectedDevice.setDirty(false);
        view.selectDevice(selectedDevice);
    }

    @Override
    public void onConnectClicked() {
        if (selectedDevice == null) {
            return;
        }

        if (selectedDevice.isConnected()) {
            disconnect();
        } else {
            saveDeviceChanges();
        }
    }

    private void saveDeviceChanges() {
        // Save only Artik type
        if (!ARTIK_CATEGORY.equals(selectedDevice.getType())) {
            return;
        }

        if (selectedDevice.getScript() == null) {
            createDevice();
        } else {
            connectToExistingDevice();
        }
    }

    /**
     * Creates a new device connection.
     */
    private void createDevice() {
        List<String> tags = new ArrayList<>();
        tags.add(ARTIK_CATEGORY);

        Jso content = Jso.create();
        content.addField("host", selectedDevice.getHost());
        content.addField("port", selectedDevice.getPort());
        content.addField("username", selectedDevice.getUserName());
        content.addField("password", selectedDevice.getPassword());
        content.addField("replicationFolder", selectedDevice.getReplicationFolder());


        RecipeDescriptor recipe = dtoFactory.createDto(RecipeDescriptor.class)
                                            .withName(selectedDevice.getName())
                                            .withType(SSH_CATEGORY)
                                            .withScript(content.serialize())
                                            .withTags(tags);

        onDeviceSaved(recipe);
    }

    /**
     * Performs actions when device is saved.
     */
    private void onDeviceSaved(RecipeDescriptor recipe) {
        selectedDevice.setRecipe(recipe);
        selectedDevice.setDirty(false);

        view.showDevices(devices);
        view.selectDevice(selectedDevice);

        connect();
    }

    /**
     * Opens a connection to the selected device.
     * Starts a machine based on the selected recipe.
     */
    private void connect() {
        view.setConnectButtonText(null);

        connectTargetName = selectedDevice.getName();
        connectNotification = notificationManager.notify(locale.deviceConnectProgress(selectedDevice.getName()),
                                                         StatusNotification.Status.PROGRESS,
                                                         StatusNotification.DisplayMode.FLOAT_MODE);

        MachineLimitsDto limitsDto = dtoFactory.createDto(MachineLimitsDto.class).withRam(1024);
        MachineSourceDto sourceDto = dtoFactory.createDto(MachineSourceDto.class)
                                               .withType("ssh-config")
                                               .withContent(selectedDevice.getRecipe().getScript());

        MachineConfigDto configDto = dtoFactory.createDto(MachineConfigDto.class)
                                               .withDev(false)
                                               .withName(selectedDevice.getName())
                                               .withSource(sourceDto)
                                               .withLimits(limitsDto)
                                               .withType(ARTIK_CATEGORY);

        deviceServiceClient.connect(configDto);
    }

    @Override
    public void onMachineStatusChanged(MachineStatusChangedEvent event) {
        if (connectNotification == null || connectTargetName == null || !connectTargetName.equals(event.getMachineName())) {
            return;
        }
        switch (event.getEventType()) {
            case RUNNING:
                onConnected(event.getMachineId());
                break;
            case ERROR:
                onConnectingFailed(event.getErrorMessage());
                break;
        }
    }

    /**
     * Ensures device is started.
     */
    private void onConnected(final String deviceId) {
        new Timer() {
            @Override
            public void run() {
                deviceServiceClient.getDevice(deviceId).then(new Operation<MachineDto>() {
                    @Override
                    public void apply(MachineDto machineDto) throws OperationException {
                        if (machineDto != null && machineDto.getStatus() == RUNNING) {
                            eventBus.fireEvent(new MachineStateEvent(entityFactory.createMachine(machineDto),
                                                                     MachineStateEvent.MachineAction.RUNNING));
                            final String machineName = machineDto.getConfig().getName();
                            connectNotification.setTitle(locale.deviceConnectSuccess(machineName));
                            connectNotification.setStatus(StatusNotification.Status.SUCCESS);
                            updateDevices(machineName);

                            softwareManager.checkAndInstall(machineName);
                        } else {
                            onConnectingFailed(null);
                        }
                    }
                }).catchError(new Operation<PromiseError>() {
                    @Override
                    public void apply(PromiseError arg) throws OperationException {
                        onConnectingFailed(arg.getMessage());
                    }
                });
            }
        }.schedule(1000);
    }


    /**
     * Makes a list of devices and selects specified device.
     */
    private void updateDevices(final String deviceToSelect) {
        devices.clear();
        machines.clear();

        deviceServiceClient.getDevices().then(new Operation<List<MachineDto>>() {
            @Override
            public void apply(List<MachineDto> serverDevices) throws OperationException {
                for (MachineDto machine : serverDevices) {
                    machines.put(machine.getConfig().getName(), machine);

                    final String script = machine.getConfig().getSource().getContent();
                    final Device device = new Device(machine.getConfig().getName(), ARTIK_CATEGORY);
                    device.setScript(script);
                    device.setId(machine.getId());
                    restoreDevice(device);

                    devices.add(device);
                    device.setConnected(isMachineRunning(machine));
                }

                view.showDevices(devices);

                if (deviceToSelect == null && !devices.isEmpty()) {
                    view.selectDevice(devices.get(0));
                    return;
                }

                for (Device device : devices) {
                    if (device.getName().equals(deviceToSelect)) {
                        view.selectDevice(device);
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void onDeleteDevice(final Device device) {
        dialogFactory.createConfirmDialog("IDE", locale.deviceDeleteConfirm(device.getName()),
                                          new ConfirmCallback() {
                                              @Override
                                              public void accepted() {
                                                  disconnectAndDelete(device);
                                              }
                                          }, new CancelCallback() {
                    @Override
                    public void cancelled() {
                    }
                }).show();
    }

    /**
     * Generates device name base on existing ones.
     *
     * @return generated name
     */
    private String generateDeviceName() {
        int i = 1;
        while (isDeviceNameExists(DEFAULT_NAME + "_" + i)) {
            i++;
        }
        return DEFAULT_NAME + "_" + i;
    }

    /**
     * Updates buttons state.
     */
    private void updateButtons() {
        if (selectedDevice == null) {
            return;
        }

        view.setConnectButtonText(selectedDevice.isConnected() ? "Disconnect" : "Connect");

        view.enableCancelButton(selectedDevice.isDirty());

        view.enableEditing(!selectedDevice.isConnected());

        if (selectedDevice.isConnected()) {
            view.enableConnectButton(true);
            return;
        }

        boolean deviceAlreadyExists = isDeviceNameExists(view.getDeviceName());
        boolean deviceNameValid = !StringUtils.isNullOrEmpty(view.getDeviceName()) && view.getDeviceName().matches(VALID_NAME);

        boolean isNotValid = !deviceNameValid ||
                             StringUtils.isNullOrEmpty(view.getHost()) ||
                             StringUtils.isNullOrEmpty(view.getPort()) || deviceAlreadyExists;
        view.enableConnectButton(!isNotValid);

        // check host is not empty
        if (view.getHost().isEmpty()) {
            view.markHostInvalid();
        } else {
            view.unmarkHost();
        }

        // check port is not empty
        if (view.getPort().isEmpty()) {
            view.markPortInvalid();
        } else {
            view.unmarkPort();
        }

        // check device name is not empty and doesn't exist
        if (!deviceNameValid || deviceAlreadyExists) {
            view.markDeviceNameInvalid();
        } else {
            view.unmarkDeviceName();
        }
    }

    /**
     * Checks device name on existence.
     *
     * @param deviceName
     *         name of the device to check on existence
     * @return boolean <code>true</code> id name already exists
     */
    private boolean isDeviceNameExists(String deviceName) {
        for (Device device : devices) {
            if (device != selectedDevice && device.getName().equals(deviceName)) {
                return true;
            }
        }
        return false;
    }

    private void connectToExistingDevice() {
        connectNotification = notificationManager.notify(locale.deviceConnectProgress(selectedDevice.getName()),
                                                         StatusNotification.Status.PROGRESS,
                                                         StatusNotification.DisplayMode.FLOAT_MODE);
        deviceServiceClient.connectById(selectedDevice.getId()).then(new Operation<MachineDto>() {
            @Override
            public void apply(MachineDto arg) throws OperationException {
                eventBus.fireEvent(new MachineStateEvent(entityFactory.createMachine(arg),
                                                         MachineStateEvent.MachineAction.RUNNING));
                connectNotification.setTitle(locale.deviceConnectSuccess(arg.getConfig().getName()));
                connectNotification.setStatus(StatusNotification.Status.SUCCESS);
                updateDevices(arg.getConfig().getName());
            }
        });
    }

    /**
     * Closes the connection to the selected device.
     * Destroys a machine based on the selected recipe.
     */
    private void disconnect() {
        if (selectedDevice == null || !selectedDevice.isConnected()) {
            return;
        }

        MachineDto machine = machines.get(selectedDevice.getName());
        disconnect(machine);
    }

    /**
     * Disconnect the device.
     *
     * @param device
     *         device to disconnect
     */
    private void disconnect(final MachineDto device) {
        if (device == null || device.getStatus() != RUNNING) {
            return;
        }
        view.setConnectButtonText(null);

        deviceServiceClient.disconnect(device.getId(), false).then(new Operation<MachineDto>() {
            @Override
            public void apply(MachineDto arg) throws OperationException {
                eventBus.fireEvent(new MachineStateEvent(entityFactory.createMachine(device), DESTROYED));
                notificationManager.notify(locale.deviceDisconnectSuccess(selectedDevice.getName()),
                                           StatusNotification.Status.SUCCESS,
                                           StatusNotification.DisplayMode.FLOAT_MODE);
                updateDevices(selectedDevice.getName());
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.notify(locale.deviceDisconnectError(selectedDevice.getName()),
                                           StatusNotification.Status.FAIL,
                                           StatusNotification.DisplayMode.FLOAT_MODE);
            }
        });
    }

    private void disconnectAndDelete(final Device device) {
        final MachineDto machine = machines.get(device.getName());
        if (machine == null) {
            return;
        }

        deviceServiceClient.disconnect(machine.getId(), true).then(new Operation<MachineDto>() {
            @Override
            public void apply(MachineDto arg) throws OperationException {
                if (RUNNING.equals(machine.getStatus())) {
                    eventBus.fireEvent(new MachineStateEvent(entityFactory.createMachine(arg), DESTROYED));
                    notificationManager.notify(locale.deviceDisconnectSuccess(device.getName()),
                                               StatusNotification.Status.SUCCESS,
                                               StatusNotification.DisplayMode.FLOAT_MODE);
                }
                devices.remove(device);
                view.showDevices(devices);

                view.selectDevice(null);
                view.showHintPanel();

                notificationManager.notify(locale.deviceDeleteSuccess(device.getName()),
                                           StatusNotification.Status.SUCCESS,
                                           StatusNotification.DisplayMode.FLOAT_MODE);
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.notify(locale.deviceDisconnectError(device.getName()),
                                           StatusNotification.Status.FAIL,
                                           StatusNotification.DisplayMode.FLOAT_MODE);
                updateDevices(device.getName());
            }
        });
    }

    /**
     * Handles connecting error and displays an error message.
     *
     * @param reason
     *         a reason to be attached to the error message
     */
    private void onConnectingFailed(String reason) {
        if (isNullOrEmpty(reason)) {
            return;
        }
        connectNotification.setTitle(locale.deviceConnectError(selectedDevice.getName()));
        connectNotification.setContent(reason);

        connectNotification.setStatus(StatusNotification.Status.FAIL);

        view.selectDevice(selectedDevice);
    }

}
