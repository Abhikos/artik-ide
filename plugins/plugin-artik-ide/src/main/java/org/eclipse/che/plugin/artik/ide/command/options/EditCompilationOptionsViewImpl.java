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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.che.ide.CoreLocalizationConstant;
import org.eclipse.che.ide.ui.window.Window;
import org.eclipse.che.plugin.artik.ide.ArtikLocalizationConstant;

import static org.eclipse.che.plugin.artik.ide.command.macro.BinaryNameMacro.DEFAULT_BINARY_NAME;
import static org.eclipse.che.plugin.artik.ide.command.macro.CCompilationPropertiesMacro.DEFAULT_COMPILATION_OPTIONS;

/**
 * Implementation of {@link EditCompilationOptionsView}.
 *
 * @author Artem Zatsarynnyi
 */
@Singleton
public class EditCompilationOptionsViewImpl extends Window implements EditCompilationOptionsView {

    @UiField(provided = true)
    ArtikLocalizationConstant locale;

    @UiField
    TextBox binaryName;

    @UiField
    TextArea compilationOptions;

    private ActionDelegate actionDelegate;

    @Inject
    public EditCompilationOptionsViewImpl(EditCompilationOptionsViewImplUiBinder uiBinder,
                                          ArtikLocalizationConstant locale,
                                          CoreLocalizationConstant coreLocale) {
        this.locale = locale;

        setWidget(uiBinder.createAndBindUi(this));
        setTitle(locale.editCompilationOptionsViewTitle());

        Button closeButton = createButton(coreLocale.cancel(), "compilationOptions.button.cancel",
                                          new ClickHandler() {
                                              @Override
                                              public void onClick(ClickEvent event) {
                                                  actionDelegate.onClose();
                                              }
                                          });
        addButtonToFooter(closeButton);

        Button saveButton = createButton(coreLocale.save(), "compilationOptions.button.save",
                                         new ClickHandler() {
                                             @Override
                                             public void onClick(ClickEvent event) {
                                                 actionDelegate.onSave();
                                             }
                                         });
        saveButton.addStyleName(resources.windowCss().primaryButton());
        addButtonToFooter(saveButton);

        binaryName.getElement().setAttribute("placeholder", DEFAULT_BINARY_NAME);
        compilationOptions.getElement().setAttribute("placeholder", DEFAULT_COMPILATION_OPTIONS);
    }

    @Override
    public void setDelegate(ActionDelegate actionDelegate) {
        this.actionDelegate = actionDelegate;
    }

    @Override
    public void show() {
        super.show();

        setBinaryName("");
        setCompilationOptions("");
    }

    @Override
    public void close() {
        hide();
    }

    @Override
    public String getBinaryName() {
        return binaryName.getValue();
    }

    @Override
    public void setBinaryName(String binaryName) {
        this.binaryName.setValue(binaryName);
    }

    @Override
    public String getCompilationOptions() {
        return compilationOptions.getValue();
    }

    @Override
    public void setCompilationOptions(String compilationOptions) {
        this.compilationOptions.setValue(compilationOptions);
    }

    interface EditCompilationOptionsViewImplUiBinder extends UiBinder<Widget, EditCompilationOptionsViewImpl> {
    }
}
