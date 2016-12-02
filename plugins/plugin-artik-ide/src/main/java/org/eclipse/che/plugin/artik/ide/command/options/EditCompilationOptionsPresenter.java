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
package org.eclipse.che.plugin.artik.ide.command.options;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.project.MutableProjectConfig;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;

import java.util.Collections;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.plugin.artik.ide.command.macro.BinaryNameMacro.DEFAULT_BINARY_NAME;
import static org.eclipse.che.plugin.artik.ide.command.macro.CCompilationPropertiesMacro.DEFAULT_COMPILATION_OPTIONS;
import static org.eclipse.che.plugin.cpp.shared.Constants.BINARY_NAME_ATTRIBUTE;
import static org.eclipse.che.plugin.cpp.shared.Constants.COMPILATION_OPTIONS_ATTRIBUTE;

/**
 * Presenter which allows to edit Artik compilation options.
 *
 * @author Artem Zatsarynnyi
 */
@Singleton
public class EditCompilationOptionsPresenter implements EditCompilationOptionsView.ActionDelegate {

    private final AppContext                 appContext;
    private final EditCompilationOptionsView view;

    @Inject
    public EditCompilationOptionsPresenter(AppContext appContext, EditCompilationOptionsView view) {
        this.appContext = appContext;
        this.view = view;

        view.setDelegate(this);
    }

    /** Show the presenter's view. */
    public void show() {
        view.show();

        Optional<Project> projectOptional = getCurrentProject();
        if (!projectOptional.isPresent()) {
            return;
        }

        Project project = projectOptional.get();

        String binaryName = project.getAttribute(BINARY_NAME_ATTRIBUTE);
        view.setBinaryName(!isNullOrEmpty(binaryName) ? binaryName : DEFAULT_BINARY_NAME);

        String compilationOptions = project.getAttribute(COMPILATION_OPTIONS_ATTRIBUTE);
        view.setCompilationOptions(!isNullOrEmpty(compilationOptions) ? compilationOptions : DEFAULT_COMPILATION_OPTIONS);
    }

    @Override
    public void onClose() {
        view.close();
    }

    @Override
    public void onSave() {
        saveOptions().then(new Operation<Project>() {
            @Override
            public void apply(Project project) throws OperationException {
                view.close();
            }
        });
    }

    private Promise<Project> saveOptions() {
        Optional<Project> projectOptional = getCurrentProject();
        if (!projectOptional.isPresent()) {
            return null;
        }

        final Project project = projectOptional.get();
        final MutableProjectConfig updateConfig = new MutableProjectConfig(project);

        updateConfig.getAttributes().put(BINARY_NAME_ATTRIBUTE, Collections.singletonList(view.getBinaryName()));
        updateConfig.getAttributes().put(COMPILATION_OPTIONS_ATTRIBUTE, Collections.singletonList(view.getCompilationOptions()));

        return project.update().withBody(updateConfig).send();
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
