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
package org.eclipse.che.plugin.artik.ide.command;

import com.google.common.base.Optional;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.command.CommandImpl;
import org.eclipse.che.ide.api.command.CommandProducer;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.extension.machine.client.command.custom.CustomCommandType;

/**
 * Abstract {@link CommandProducer} which is applicable when current project type is C.
 *
 * @author Artem Zatsarynnyi
 */
public abstract class AbstractArtikProducer implements CommandProducer {

    protected final CustomCommandType customCommandType;
    protected final DtoFactory        dtoFactory;
    protected final AppContext        appContext;

    private final String name;
    private String projectType;

    protected AbstractArtikProducer(String name, String projectType, CustomCommandType customCommandType, DtoFactory dtoFactory, AppContext appContext) {
        this.name = name;
        this.projectType = projectType;
        this.customCommandType = customCommandType;
        this.dtoFactory = dtoFactory;
        this.appContext = appContext;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isApplicable() {
        Optional<Resource> selectedResource = getSelectedResource();
        if (!selectedResource.isPresent()) {
            return false;
        }

        Resource resource = selectedResource.get();
        Optional<Project> projectOptional = resource.getRelatedProject();

        return projectOptional.isPresent() && projectOptional.get().isTypeOf(projectType);

    }

    protected Optional<Resource> getSelectedResource() {
        final Resource[] resources = appContext.getResources();
        if (resources == null || resources.length != 1) {
            return Optional.absent();
        }

        return Optional.of(appContext.getResource());
    }

    @Override
    public CommandImpl createCommand(Machine machine) {
        return new CommandImpl(getName(), getCommandLine(machine), customCommandType.getId());
    }

    protected abstract String getCommandLine(Machine machine);
}
