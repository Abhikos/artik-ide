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
package org.eclipse.che.plugin.artik.ide.run;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.core.model.machine.Command;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.machine.shared.dto.CommandDto;
import org.eclipse.che.api.machine.shared.dto.MachineProcessDto;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.command.CommandImpl;
import org.eclipse.che.ide.api.macro.MacroProcessor;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.extension.machine.client.outputspanel.console.CommandConsoleFactory;
import org.eclipse.che.ide.extension.machine.client.outputspanel.console.DefaultOutputConsole;
import org.eclipse.che.ide.extension.machine.client.processes.panel.ProcessesPanelPresenter;
import org.eclipse.che.ide.part.explorer.project.macro.ExplorerCurrentFileNameMacro;
import org.eclipse.che.ide.part.explorer.project.macro.ExplorerCurrentFileParentPathMacro;
import org.eclipse.che.ide.util.UUID;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.events.MessageHandler;
import org.eclipse.che.plugin.artik.ide.command.macro.ReplicationFolderMacro;
import org.eclipse.che.plugin.artik.ide.debug.ProcessListener;
import org.eclipse.che.plugin.artik.ide.machine.DeviceServiceClient;

/**
 * Run NodeJs file.
 *
 * @author Valeriy Svydenko
 */
@Singleton
public class NodeJsRunner {
    private final DtoFactory              dtoFactory;
    private final DeviceServiceClient     deviceServiceClient;
    private final MacroProcessor          macroProcessor;
    private final ExplorerCurrentFileParentPathMacro currentFileParentPathMacro;
    private final ExplorerCurrentFileNameMacro currentFileNameMacro;
    private final ProcessListener processListener;
    private final MessageBus              messageBus;
    private final ProcessesPanelPresenter processesPanelPresenter;
    private final CommandConsoleFactory   commandConsoleFactory;

    @Inject
    public NodeJsRunner(DtoFactory dtoFactory,
                        MacroProcessor macroProcessor,
                        DeviceServiceClient deviceServiceClient,
                        ExplorerCurrentFileParentPathMacro currentFileParentPathMacro,
                        ExplorerCurrentFileNameMacro currentFileNameMacro,
                        ProcessListener processListener,
                        MessageBusProvider messageBusProvider,
                        ProcessesPanelPresenter processesPanelPresenter,
                        CommandConsoleFactory commandConsoleFactory) {
        this.dtoFactory = dtoFactory;
        this.deviceServiceClient = deviceServiceClient;
        this.macroProcessor = macroProcessor;
        this.currentFileParentPathMacro = currentFileParentPathMacro;
        this.currentFileNameMacro = currentFileNameMacro;
        this.processListener = processListener;
        this.messageBus = messageBusProvider.getMachineMessageBus();
        this.processesPanelPresenter = processesPanelPresenter;
        this.commandConsoleFactory = commandConsoleFactory;
    }


    /**
     * Run NodeJs file.
     *
     * @param machine
     *         current device.
     */
    public void run(final Machine machine) {
        final Command command = buildCommand(machine);
        final String outputChannel = "process:output:" + UUID.uuid();
        final DefaultOutputConsole outputConsole = (DefaultOutputConsole)commandConsoleFactory.create(command.getName());

        final MessageHandler messageHandler = new MessageHandler() {
            @Override
            public void onMessage(String message) {
                outputConsole.printText(message);
            }
        };
        try {
            messageBus.subscribe(outputChannel, messageHandler);
        } catch (WebSocketException e) {
            //do nothing
        }

        macroProcessor.expandMacros(command.getCommandLine()).then(new Operation<String>() {
            @Override
            public void apply(String arg) throws OperationException {
                final CommandDto toExecute = dtoFactory.createDto(CommandDto.class)
                                                       .withName(command.getName())
                                                       .withCommandLine(arg)
                                                       .withType(command.getType());

                final Promise<MachineProcessDto> processPromise = deviceServiceClient.executeCommand(machine.getId(),
                                                                                                     toExecute,
                                                                                                     outputChannel);
                processPromise.then(new Operation<MachineProcessDto>() {
                    @Override
                    public void apply(MachineProcessDto process) throws OperationException {
                        processesPanelPresenter.addCommandOutput(machine.getId(), outputConsole);
                        processListener.attachToProcess(process, machine, outputChannel, messageHandler);
                    }
                });
            }
        });
    }

    private Command buildCommand(Machine machine) {
        String commandLine = "cd " +
                             ReplicationFolderMacro.KEY.replace("%machineId%", machine.getId()) +
                             currentFileParentPathMacro.getName() +
                             " && node " +
                             currentFileNameMacro.getName();

        return new CommandImpl("run", commandLine, "custom");
    }

}
