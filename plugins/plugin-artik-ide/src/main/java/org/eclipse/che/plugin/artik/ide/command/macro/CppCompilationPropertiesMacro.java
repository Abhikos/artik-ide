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
package org.eclipse.che.plugin.artik.ide.command.macro;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.che.ide.api.app.AppContext;

import static org.eclipse.che.plugin.cpp.shared.Constants.COMPILATION_OPTIONS_ATTRIBUTE;

/**
 * Macro that provides compilation properties value from the project's attributes.
 *
 * @author Artem Zatsarynnyi
 */
@Singleton
public class CppCompilationPropertiesMacro extends ProjectAttributeMacro {

    public static final String DEFAULT_COMPILATION_OPTIONS = "$CXX -lartik-sdk-base $(for i in $(ls $CPATH)\n" +
                                                             "do artik_sdk=-I$CPATH/$i\n" +
                                                             "echo $artik_sdk\n" +
                                                             "done) -lpthread -g";

    @Inject
    public CppCompilationPropertiesMacro(AppContext appContext) {
        super(appContext);
    }

    @Override
    public String getName() {
        return "${cpp.compilation.options}";
    }

    @Override
    public String getDescription() {
        return "provides C++ compilation properties";
    }

    @Override
    protected String getAttribute() {
        return COMPILATION_OPTIONS_ATTRIBUTE;
    }

    @Override
    protected String getDefaultValue() {
        return DEFAULT_COMPILATION_OPTIONS;
    }
}
