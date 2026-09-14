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

package net.mcreator.wingman.preferences;

import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.preferences.PreferencesDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.wingman.acp.provider.AcpProvider;
import net.mcreator.wingman.acp.provider.AcpProviders;
import net.mcreator.wingman.acp.provider.StdioAcpProvider;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Preferences page of Wingman: the list of agent providers to select from and the command of the selected provider.
 * Values are stored in {@link WingmanPreferences} by {@link #save()}.
 */
public class WingmanPreferencesPanel extends JPanel {

	private static final Color NOT_INSTALLED_COLOR = new Color(0xd9, 0x82, 0x2b);

	private final WingmanPreferences preferences = WingmanPreferences.get();

	// Executables of the known providers found on this device, checked once when the page is opened
	private final Map<AcpProvider, Optional<Path>> installed = new HashMap<>();

	private final JList<AcpProvider> providers;
	private final JTextField commandField = new JTextField();

	// The command field shows the command of the selected provider, so the custom command is kept while
	// another provider is shown
	private String customCommand = preferences.customCommand.get();
	private AcpProvider shown;

	public WingmanPreferencesPanel(PreferencesDialog dialog) {
		super(new BorderLayout());

		for (AcpProvider provider : AcpProviders.ALL) {
			if (provider instanceof StdioAcpProvider stdio && provider != AcpProviders.CUSTOM)
				installed.put(provider, stdio.installedExecutable());
		}

		JComponent titlebar = L10N.label("wingman.preferences.title");
		titlebar.setBorder(BorderFactory.createEmptyBorder(0, 10, 15, 10));
		add(titlebar, BorderLayout.NORTH);

		DefaultListModel<AcpProvider> model = new DefaultListModel<>();
		model.addAll(AcpProviders.ALL);
		providers = new JList<>(model);
		providers.setCellRenderer(new ProviderCellRenderer());
		providers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JPanel command = PanelUtils.westAndCenterElement(
				L10N.label("dialog.preferences.entry_description", L10N.t("wingman.preferences.command"),
						L10N.t("wingman.preferences.command.description")), commandField, 10, 0);
		command.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		JPanel main = new JPanel(new BorderLayout(0, 5));
		main.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		main.add(L10N.label("dialog.preferences.entry_description", L10N.t("wingman.preferences.provider"),
				L10N.t("wingman.preferences.provider.description")), BorderLayout.NORTH);
		main.add(new JScrollPane(providers), BorderLayout.CENTER);
		main.add(command, BorderLayout.SOUTH);
		add(main, BorderLayout.CENTER);

		providers.setSelectedValue(AcpProviders.selected(), true);
		show(providers.getSelectedValue());

		// Listeners are added after the initial state is shown, so only changes by the user enable to apply
		providers.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && providers.getSelectedValue() != null) {
				show(providers.getSelectedValue());
				dialog.markChanged();
			}
		});
		commandField.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) {
				dialog.markChanged();
			}

			@Override public void removeUpdate(DocumentEvent e) {
				dialog.markChanged();
			}

			@Override public void changedUpdate(DocumentEvent e) {
				dialog.markChanged();
			}
		});
	}

	private void show(AcpProvider provider) {
		if (shown == AcpProviders.CUSTOM)
			customCommand = commandField.getText();
		shown = provider;

		commandField.setText(provider == AcpProviders.CUSTOM ?
				customCommand :
				provider instanceof StdioAcpProvider stdio ? stdio.command().toString() : "");
		commandField.setEditable(provider == AcpProviders.CUSTOM);
	}

	public void save() {
		if (shown == AcpProviders.CUSTOM)
			customCommand = commandField.getText();
		preferences.provider.set(shown.id());
		preferences.customCommand.set(customCommand);
	}

	private class ProviderCellRenderer extends JPanel implements ListCellRenderer<AcpProvider> {

		private final JLabel icon = new JLabel();
		private final JLabel name = new JLabel();
		private final JLabel description = new JLabel();
		private final JLabel status = new JLabel();

		ProviderCellRenderer() {
			setLayout(new BorderLayout(4, 0));
			add(icon, BorderLayout.WEST);

			JComponent text = PanelUtils.column(2, name, description, status);
			text.setOpaque(false);
			text.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
			add(text, BorderLayout.CENTER);

			ComponentUtils.deriveFont(name, 14);
			ComponentUtils.deriveFont(description, 11);
			ComponentUtils.deriveFont(status, 11);

			setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 0));
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends AcpProvider> list, AcpProvider value, int index,
				boolean isSelected, boolean cellHasFocus) {
			icon.setIcon(value.icon());
			name.setText(value.name());
			description.setText(value.description());

			Optional<Path> executable = installed.get(value);
			status.setVisible(executable != null); // the custom provider has no known executable to check
			if (executable != null) {
				status.setText(executable.map(path -> L10N.t("wingman.provider.installed", path))
						.orElse(L10N.t("wingman.provider.not_installed")));
			}

			Color altForeground = isSelected ? list.getSelectionForeground() : Theme.current().getAltForegroundColor();
			name.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
			description.setForeground(altForeground);
			status.setForeground(executable != null && executable.isEmpty() ? NOT_INSTALLED_COLOR : altForeground);
			setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
			setOpaque(isSelected);
			return this;
		}
	}

}
