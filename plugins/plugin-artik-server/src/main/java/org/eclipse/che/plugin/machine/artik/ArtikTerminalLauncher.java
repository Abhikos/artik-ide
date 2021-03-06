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
package org.eclipse.che.plugin.machine.artik;

import org.eclipse.che.api.agent.server.model.impl.AgentImpl;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.ServerConf2;
import org.eclipse.che.api.machine.server.spi.Instance;
import org.eclipse.che.plugin.machine.ssh.SshMachineImplTerminalLauncher;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 * Launch websocket terminal in Artik device.
 *
 * @author Valeriy Svydenko
 */
public class ArtikTerminalLauncher extends SshMachineImplTerminalLauncher {
    public static final  String TERMINAL_LOCATION_PROPERTY       = "artik.terminal.location";
    public static final  String TERMINAL_LAUNCH_COMMAND_PROPERTY = "artik.terminal.run_command";
    private static final String ARTIK_MACHINE_TYPE               = "artik";
    private static final long   TERMINAL_AGENT_MAX_START_TIME_MS = 120_000;
    private static final long   TERMINAL_AGENT_PING_DELAY_MS     = 2000;

    private final String runTerminalCommand;

    @Inject
    public ArtikTerminalLauncher(@Named(TERMINAL_LAUNCH_COMMAND_PROPERTY) String runTerminalCommand,
                                 @Named(TERMINAL_LOCATION_PROPERTY) String terminalLocation,
                                 ArtikDeviceTerminalFilesPathProvider terminalPathProvider) {
        super(TERMINAL_AGENT_MAX_START_TIME_MS, TERMINAL_AGENT_PING_DELAY_MS, terminalLocation, terminalPathProvider);
        this.runTerminalCommand = runTerminalCommand;
    }

    public void launch(Instance machine) throws ServerException {
        final String agentId = "org.eclipse.che.terminal";
        final String agentName = "artik.terminal.agent";
        final String version = "";
        final String description = "";
        final List<String> dependencies = emptyList();
        final Map<String, String> properties = emptyMap();
        final Map<String, ? extends ServerConf2> servers = emptyMap();
        AgentImpl agent = new AgentImpl(agentId,
                                        agentName,
                                        version,
                                        description,
                                        dependencies,
                                        properties,
                                        runTerminalCommand,
                                        servers);

        super.launch(machine, agent);
    }

    @Override
    public String getMachineType() {
        return ARTIK_MACHINE_TYPE;
    }
}
