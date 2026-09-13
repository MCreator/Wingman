/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.wingman.acp;

import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.spec.AcpClientSession;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import net.mcreator.io.OS;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.init.L10N;
import net.mcreator.wingman.acp.provider.AcpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class AcpAuthenticator {

	private static final Logger LOG = LogManager.getLogger(AcpAuthenticator.class);

	// Error code of auth_required in the ACP specification
	private static final int AUTH_REQUIRED = -32000;

	static boolean isAuthRequired(Throwable error) {
		if (error instanceof AcpClientSession.AcpError acpError) {
			String message = acpError.getError().message();
			return acpError.getCode() == AUTH_REQUIRED || acpError.getCode() == AcpErrorCodes.AUTHENTICATION_REQUIRED
					|| (message != null && message.toLowerCase(Locale.ROOT).contains("auth"));
		}
		return false;
	}

	/**
	 * Asks the user to log in and authenticates the agent. Can be called from any thread and blocks until done.
	 * <p>
	 * If the provider has a login command, the user logs in out of band: login commands are interactive, so on Windows
	 * a terminal window running the command is opened for the user, on other systems the user is asked to run it in a
	 * terminal. Otherwise, the user picks one of the auth methods of the agent and the agent logs in itself.
	 *
	 * @param reconnect Reconnects to the agent and returns the new client
	 * @return true if the agent was authenticated and the failed request can be retried
	 */
	static boolean login(@Nullable Window parent, AcpProvider provider, List<AcpSchema.AuthMethod> methods,
			Supplier<AcpSyncClient> reconnect) {
		String loginCommand = provider.loginCommand();
		if (loginCommand != null) {
			boolean terminalOpened = OS.getOS() == OS.WINDOWS && openTerminal(loginCommand);
			String messageKey = terminalOpened ? "wingman.auth.message_terminal" : "wingman.auth.message_command";
			if (!confirm(parent, L10N.label(messageKey, provider.name(), loginCommand)))
				return false;
			return reconnectAndAuthenticate(parent, reconnect, null);
		}

		if (methods.isEmpty()) {
			// The agent offers no way to log in over ACP, the user has to use the agent's own tools
			if (!confirm(parent, L10N.label("wingman.auth.message_manual", provider.name())))
				return false;
			return reconnectAndAuthenticate(parent, reconnect, null);
		}

		AcpSchema.AuthMethod method = chooseMethod(parent, provider, methods);
		if (method == null)
			return false;
		return reconnectAndAuthenticate(parent, reconnect, method);
	}

	// Reconnects to the agent, which picks up credentials the user logged in with out of band, and authenticates
	// using the given method if there is one
	private static boolean reconnectAndAuthenticate(@Nullable Window parent, Supplier<AcpSyncClient> reconnect,
			@Nullable AcpSchema.AuthMethod method) {
		try {
			AcpSyncClient client = reconnect.get();
			if (method != null)
				client.authenticate(new AcpSchema.AuthenticateRequest(method.id()));
			return true;
		} catch (RuntimeException e) {
			LOG.warn("Login to agent failed", e);
			ThreadUtil.runOnSwingThreadAndWait(
					() -> JOptionPane.showMessageDialog(parent, L10N.t("wingman.auth.failed", e.getMessage()),
							L10N.t("wingman.auth.title"), JOptionPane.ERROR_MESSAGE));
			return false;
		}
	}

	// Opens a new command prompt window running the given command, the window stays open so its output can be read
	private static boolean openTerminal(String command) {
		List<String> cmd = new ArrayList<>(List.of("cmd.exe", "/c", "start", "Wingman login", "cmd.exe", "/k"));
		cmd.addAll(Arrays.asList(command.trim().split(" +")));
		try {
			new ProcessBuilder(cmd).start();
			return true;
		} catch (IOException e) {
			LOG.warn("Failed to open a terminal for the login command", e);
			return false;
		}
	}

	// Returns null if the user cancelled
	@Nullable private static AcpSchema.AuthMethod chooseMethod(@Nullable Window parent, AcpProvider provider,
			List<AcpSchema.AuthMethod> methods) {
		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.add(L10N.label("wingman.auth.message", provider.name()), BorderLayout.NORTH);

		JComboBox<AcpSchema.AuthMethod> methodBox = new JComboBox<>(methods.toArray(AcpSchema.AuthMethod[]::new));
		methodBox.setRenderer(new DefaultListCellRenderer() {
			@Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof AcpSchema.AuthMethod method) {
					setText(method.description() != null && !method.description().isBlank() ?
							method.name() + " - " + method.description() :
							method.name());
				}
				return this;
			}
		});
		content.add(methodBox, BorderLayout.CENTER);

		return confirm(parent, content) ? (AcpSchema.AuthMethod) methodBox.getSelectedItem() : null;
	}

	// Shows an OK/Cancel login dialog on the Swing thread and returns true if the user pressed OK
	private static boolean confirm(@Nullable Window parent, Component content) {
		AtomicReference<Integer> option = new AtomicReference<>();
		ThreadUtil.runOnSwingThreadAndWait(() -> option.set(
				JOptionPane.showConfirmDialog(parent, content, L10N.t("wingman.auth.title"),
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)));
		return option.get() == JOptionPane.OK_OPTION;
	}

}
