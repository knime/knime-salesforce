/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   2023-12-08 (bernd.wiswedel): copied from org.knime.ext.sharepoint.dialog
 */
package org.knime.salesforce.connect;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.knime.salesforce.rest.Timeouts;

/**
 * Timeout panel with connect and read timeout
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class TimeoutPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SpinnerNumberModel m_connectTimeoutSpinnerModel;
    private final SpinnerNumberModel m_readTimeoutSpinnerModel;

    TimeoutPanel() {
        m_connectTimeoutSpinnerModel = new SpinnerNumberModel(60, 0, Integer.MAX_VALUE, 10);
        m_readTimeoutSpinnerModel = new SpinnerNumberModel(60, 0, Integer.MAX_VALUE, 10);
        setLayout(new GridBagLayout());

        final var c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 0;
        add(new JLabel("Connection timeout (seconds): "), c);

        c.gridy = 1;
        add(new JLabel("Read timeout (seconds): "), c);

        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        add(new JSpinner(m_connectTimeoutSpinnerModel), c);

        c.gridy = 1;
        add(new JSpinner(m_readTimeoutSpinnerModel), c);

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        c.weighty = 1;
        add(Box.createVerticalGlue(), c);
    }

    void saveSettingsTo(final SalesforceConnectorNodeSettings settings) {
        final var connect = ((Number)m_connectTimeoutSpinnerModel.getValue()).intValue();
        final var read = ((Number)m_readTimeoutSpinnerModel.getValue()).intValue();
        settings.setTimeouts(new Timeouts(connect, read));
    }

    void loadSettingsFrom(final SalesforceConnectorNodeSettings settings) {
        final var timeouts = settings.getTimeouts();
        m_connectTimeoutSpinnerModel.setValue(timeouts.connectionTimeoutS());
        m_readTimeoutSpinnerModel.setValue(timeouts.readTimeoutS());
    }

}
