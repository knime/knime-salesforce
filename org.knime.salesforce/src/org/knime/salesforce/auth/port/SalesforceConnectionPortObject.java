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
 *   Dec 26, 2019 (wiswedel): created
 */
package org.knime.salesforce.auth.port;

import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.webui.node.port.PortViewManager;
import org.knime.credentials.base.CredentialPortObject;

/**
 * Subclass of {@link CredentialPortObject}, it's only there for the port type.
 * Since AP 5.3 this class extends {@link CredentialPortObject}.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author Bjoern Lohrmann, KNIME GmbH
 */
@SuppressWarnings("restriction")
public final class SalesforceConnectionPortObject extends CredentialPortObject {

    /** The type, eh. */
    @SuppressWarnings("hiding")
    public static final PortType TYPE;

    static {
        TYPE = PortTypeRegistry.getInstance().getPortType(SalesforceConnectionPortObject.class);

        final var credentialPOView = PortViewManager.getPortViews(CredentialPortObject.TYPE);
        PortViewManager.registerPortViews(TYPE, //
            credentialPOView.viewDescriptors(), //
            credentialPOView.configuredIndices(), //
            credentialPOView.executedIndices());
    }

    /** Serializer as required by ext point definition. */
    public static final class Serializer extends AbstractSimplePortObjectSerializer<SalesforceConnectionPortObject> {
    }

    /** Init object with spec.
     * @param spec Non null spec.
     */
    public SalesforceConnectionPortObject(final SalesforceConnectionPortObjectSpec spec) {
        super(spec);
    }

    /**
     * API method, do not use.
     */
    public SalesforceConnectionPortObject() {
    }

    @Override
    public SalesforceConnectionPortObjectSpec getSpec() {
        return (SalesforceConnectionPortObjectSpec)super.getSpec();
    }
}
