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
import org.eclipse.che.api.core.model.project.ProjectConfig;
import org.eclipse.che.api.core.model.project.SourceStorage;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

import static org.eclipse.che.plugin.artik.ide.command.macro.BinaryNameMacro.DEFAULT_BINARY_NAME;
import static org.eclipse.che.plugin.artik.ide.command.macro.CCompilationPropertiesMacro.DEFAULT_COMPILATION_OPTIONS;
import static org.eclipse.che.plugin.cpp.shared.Constants.BINARY_NAME_ATTRIBUTE;
import static org.eclipse.che.plugin.cpp.shared.Constants.COMPILATION_OPTIONS_ATTRIBUTE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EditCompilationOptionsPresenter}.
 *
 * @author Artem Zatsarynnyi
 */
@RunWith(MockitoJUnitRunner.class)
public class EditCompilationOptionsPresenterTest {

    private static final String BINARY_NAME         = "abc.out";
    private static final String COMPILATION_OPTIONS = "-a -b -c";
    @Mock
    private AppContext                 appContext;
    @Mock
    private EditCompilationOptionsView view;

    @Mock
    private Promise<Project>                   promise;
    @Captor
    private ArgumentCaptor<Operation<Project>> captor;
    @Captor
    private ArgumentCaptor<ProjectConfig>      projectConfigCaptor;

    @InjectMocks
    private EditCompilationOptionsPresenter presenter;

    private Project.ProjectRequest projectRequest;
    private Project                currentProject;

    @Before
    public void setUp() {
        projectRequest = mock(Project.ProjectRequest.class);
        when(projectRequest.withBody(any(ProjectConfig.class))).thenReturn(projectRequest);
        when(projectRequest.send()).thenReturn(promise);

        currentProject = mock(Project.class);
        SourceStorage sourceStorage = mock(SourceStorage.class);
        when(currentProject.getSource()).thenReturn(sourceStorage);
        when(currentProject.update()).thenReturn(projectRequest);

        Resource currentResource = mock(Resource.class);
        when(currentResource.getRelatedProject()).thenReturn(Optional.of(currentProject));

        when(appContext.getResources()).thenReturn(new Resource[]{currentResource});
        when(appContext.getResource()).thenReturn(currentResource);
    }

    @Test
    public void actionDelegateShouldBeSet() {
        verify(view).setDelegate(eq(presenter));
    }

    @Test
    public void shouldShowView() {
        presenter.show();

        verify(view).show();
    }

    @Test
    public void shouldInitializeViewByDefaultValues() {
        presenter.show();

        verify(view).setBinaryName(eq(DEFAULT_BINARY_NAME));
        verify(view).setCompilationOptions(eq(DEFAULT_COMPILATION_OPTIONS));
    }

    @Test
    public void shouldInitializeViewByValuesFromAttributes() {
        when(currentProject.getAttribute(eq(BINARY_NAME_ATTRIBUTE))).thenReturn(BINARY_NAME);
        when(currentProject.getAttribute(eq(COMPILATION_OPTIONS_ATTRIBUTE))).thenReturn(COMPILATION_OPTIONS);

        presenter.show();

        verify(view).setBinaryName(eq(BINARY_NAME));
        verify(view).setCompilationOptions(eq(COMPILATION_OPTIONS));
    }

    @Test
    public void shouldCloseView() {
        presenter.onClose();

        verify(view).close();
    }

    @Test
    public void shouldSaveCompilationOptions() throws Exception {
        // given
        when(view.getBinaryName()).thenReturn(BINARY_NAME);
        when(view.getCompilationOptions()).thenReturn(COMPILATION_OPTIONS);

        // when
        presenter.onSave();

        // then
        verify(view).getBinaryName();
        verify(view).getCompilationOptions();

        verify(projectRequest).withBody(projectConfigCaptor.capture());
        ProjectConfig projectConfig = projectConfigCaptor.getValue();
        Map<String, List<String>> attributes = projectConfig.getAttributes();
        assertThat(attributes.get(BINARY_NAME_ATTRIBUTE), notNullValue());
        assertThat(attributes.get(BINARY_NAME_ATTRIBUTE).size(), is(1));
        assertThat(attributes.get(BINARY_NAME_ATTRIBUTE).get(0), containsString(BINARY_NAME));
        assertThat(attributes.get(COMPILATION_OPTIONS_ATTRIBUTE), notNullValue());
        assertThat(attributes.get(COMPILATION_OPTIONS_ATTRIBUTE).size(), is(1));
        assertThat(attributes.get(COMPILATION_OPTIONS_ATTRIBUTE).get(0), containsString(COMPILATION_OPTIONS));

        verify(promise).then(captor.capture());
        captor.getValue().apply(currentProject);
        verify(view).close();
    }
}
