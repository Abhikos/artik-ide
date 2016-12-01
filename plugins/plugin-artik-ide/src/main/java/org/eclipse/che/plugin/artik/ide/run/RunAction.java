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

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.ide.api.action.AbstractPerspectiveAction;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.plugin.artik.ide.ArtikLocalizationConstant;

import static java.util.Collections.singletonList;
import static org.eclipse.che.ide.workspace.perspectives.project.ProjectPerspective.PROJECT_PERSPECTIVE_ID;
import static org.eclipse.che.plugin.cpp.shared.Constants.C_PROJECT_TYPE_ID;
import static org.eclipse.che.plugin.nodejs.shared.Constants.NODE_JS_PROJECT_TYPE_ID;

/**
 * Action for running binary file.
 *
 * @author Valeriy Svydenko
 */
public class RunAction extends AbstractPerspectiveAction {

    private final AppContext       appContext;
    private final Machine          machine;
    private final NodeJsRunner     nodeJsRunner;
    private final BinaryFileRunner binaryFileRunner;

    @Inject
    public RunAction(ArtikLocalizationConstant locale,
                     AppContext appContext,
                     @Assisted Machine machine,
                     NodeJsRunner nodeJsRunner,
                     BinaryFileRunner binaryFileRunner) {
        super(singletonList(PROJECT_PERSPECTIVE_ID), machine.getConfig().getName(), locale.runActionDescription(), null, null);

        this.appContext = appContext;
        this.machine = machine;
        this.nodeJsRunner = nodeJsRunner;
        this.binaryFileRunner = binaryFileRunner;
    }

    @Override
    public void updateInPerspective(ActionEvent event) {
        final Optional<Project> currentProject = getCurrentProject();
        event.getPresentation().setEnabled(currentProject.isPresent() && (currentProject.get().isTypeOf(NODE_JS_PROJECT_TYPE_ID) ||
                                                                          currentProject.get().isTypeOf(C_PROJECT_TYPE_ID)));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        final Project project = getCurrentProject().get();

        if (project.isTypeOf(NODE_JS_PROJECT_TYPE_ID)) {
            nodeJsRunner.run(machine);
        } else if (project.isTypeOf(C_PROJECT_TYPE_ID)) {
            binaryFileRunner.run(machine);
        }
    }

    private Optional<Project> getCurrentProject() {
        final Resource[] resources = appContext.getResources();
        if (resources == null || resources.length != 1) {
            return Optional.absent();
        }

        Resource resource = appContext.getResource();
        return resource.getRelatedProject();
    }
}
