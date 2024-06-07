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
 *   2024-06-04 (jloescher): created
 */
package org.knime.salesforce.connect2;

import java.io.IOException;
import java.util.Optional;

import org.apache.xmlbeans.XmlException;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeFactory;
import org.knime.credentials.base.CredentialPortObject;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObject;
import org.xml.sax.SAXException;

/**
 * Salesforce connector node.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public final class SalesforceConnector2NodeFactory extends ConfigurableNodeFactory<SalesforceConnector2NodeModel>
    implements NodeDialogFactory {

    private static final String FULL_DESCRIPTION = """
<p>
Authenticates and connects to Salesforce. The resulting Salesforce connection can be used
with other Salesforce nodes, or as credential in the REST nodes.
</p>

<p>This node uses a
<a href="https://help.salesforce.com/s/articleView?id=sf.connected_app_overview.htm">
Connected App</a> to connect to Salesforce. By default the KNIME Analytics Platform app needs to be registered
with your Salesforce domain. Please contact your Salesforce administrator or <a href="https://www.knime.com/contact">
KNIME</a> to get more information.
</p>

<p>
This node also allows you to use your own Connected App in Salesforce. Please consult the
<a href="https://help.salesforce.com/s/articleView?id=sf.connected_app_create.htm">Salesforce documentation</a>
on how to create your own app. The app needs to have the following settings:
<ul>
<li>Enable OAuth Settings</li>
<li>Set callback URL to <tt>http://localhost:51778/salesforce_oauth</tt></li>
<li>Select at least the following OAuth scopes: <i>Manage user data via APIs (api)</i> and
  <i>Perform requests at any time (refresh_token, offline_access)</i>
</li>
<li>Require Proof Key for Code Exchange (PKCE) Extension for Supported Authorization Flows</li>
<li>Require Secret for Web Server Flow</li>
</ul>
</p>
""";

    private static final String INPUT_PORT_GROUP = "Salesforce Credential";

    private static final String OUTPUT_PORT_GROUP = "Salesforce Connection";

    private static final WebUINodeConfiguration CONFIG = WebUINodeConfiguration.builder()//
        .name("Salesforce Connector")//
        .icon("./salesforce.png").shortDescription("Connect to Salesforce")//
        .fullDescription(FULL_DESCRIPTION)//
        .modelSettingsClass(SalesforceConnector2NodeSettings.class)//
        .nodeType(NodeType.Source)//
        .addOutputPort(OUTPUT_PORT_GROUP, SalesforceConnectionPortObject.TYPE,
            "Salesforce connection")//
        .addInputPort(INPUT_PORT_GROUP, CredentialPortObject.TYPE, """
                Optional Salesforce credential. Using the Secrets Retriever node you can
                retrieve a Salesforce credential from a KNIME Hub secret.
                """, true)//
        .sinceVersion(5, 3, 0).build();

    @Override
    protected NodeDescription createNodeDescription() throws SAXException, IOException, XmlException {
        return WebUINodeFactory.createNodeDescription(CONFIG);
    }

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var b = new PortsConfigurationBuilder();
        b.addOptionalInputPortGroup(INPUT_PORT_GROUP, CredentialPortObject.TYPE);
        b.addFixedOutputPortGroup(OUTPUT_PORT_GROUP, SalesforceConnectionPortObject.TYPE);
        return Optional.of(b);
    }

    @Override
    protected SalesforceConnector2NodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new SalesforceConnector2NodeModel(creationConfig.getPortConfig().orElseThrow(),
            SalesforceConnector2NodeSettings.class);
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, SalesforceConnector2NodeSettings.class);
    }

    @Override
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        // not used
        return null;
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<SalesforceConnector2NodeModel> createNodeView(final int viewIndex,
        final SalesforceConnector2NodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return false;
    }
}
