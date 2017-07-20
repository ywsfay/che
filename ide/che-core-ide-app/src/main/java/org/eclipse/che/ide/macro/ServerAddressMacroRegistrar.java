/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.macro;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.model.workspace.runtime.Machine;
import org.eclipse.che.api.core.model.workspace.runtime.Server;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.macro.BaseMacro;
import org.eclipse.che.ide.api.macro.Macro;
import org.eclipse.che.ide.api.macro.MacroRegistry;
import org.eclipse.che.ide.api.workspace.event.WorkspaceRunningEvent;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStoppedEvent;
import org.eclipse.che.ide.api.workspace.model.MachineImpl;
import org.eclipse.che.ide.api.workspace.model.WorkspaceImpl;
import org.eclipse.che.ide.bootstrap.BasicIDEInitializedEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.RUNNING;

/**
 * For every server in WsAgent's machine registers a {@link Macro} that
 * returns server's external address in form <b>hostname:port</b>.
 *
 * @author Vlad Zhukovskiy
 */
@Singleton
public class ServerAddressMacroRegistrar {

    public static final String MACRO_NAME_TEMPLATE = "${server.port.%}";

    private final Provider<MacroRegistry> macroRegistryProvider;
    private final AppContext              appContext;

    private Set<Macro> macros;

    @Inject
    public ServerAddressMacroRegistrar(EventBus eventBus,
                                       Provider<MacroRegistry> macroRegistryProvider,
                                       AppContext appContext) {
        this.macroRegistryProvider = macroRegistryProvider;
        this.appContext = appContext;

        eventBus.addHandler(BasicIDEInitializedEvent.TYPE, e -> {
            if (appContext.getWorkspace().getStatus() == RUNNING) {
                registerMacros();
            }
        });

        eventBus.addHandler(WorkspaceRunningEvent.TYPE, e -> registerMacros());

        eventBus.addHandler(WorkspaceStoppedEvent.TYPE, e -> {
            macros.forEach(macro -> macroRegistryProvider.get().unregister(macro));
            macros.clear();
        });
    }

    private void registerMacros() {
        final WorkspaceImpl workspace = appContext.getWorkspace();
        final Optional<MachineImpl> devMachine = workspace.getDevMachine();

        if (devMachine.isPresent()) {
            macros = getMacros(devMachine.get());
            macroRegistryProvider.get().register(macros);
        }
    }

    private Set<Macro> getMacros(Machine machine) {
        Set<Macro> macros = Sets.newHashSet();
        for (Map.Entry<String, ? extends Server> entry : machine.getServers().entrySet()) {
            macros.add(new ServerAddressMacro(entry.getKey(),
                                              entry.getValue().getUrl()));

            if (entry.getKey().endsWith("/tcp")) {
                macros.add(new ServerAddressMacro(entry.getKey().substring(0, entry.getKey().length() - 4),
                                                  entry.getValue().getUrl()));
            }
        }

        return macros;
    }

    private class ServerAddressMacro extends BaseMacro {
        ServerAddressMacro(String internalPort, String externalAddress) {
            super(MACRO_NAME_TEMPLATE.replaceAll("%", internalPort),
                  externalAddress,
                  "Returns external address of the server running on port " + internalPort);
        }
    }
}
