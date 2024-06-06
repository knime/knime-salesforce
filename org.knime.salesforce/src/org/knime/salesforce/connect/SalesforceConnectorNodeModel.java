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
 *   Oct 4, 2019 (benjamin): created
 */
package org.knime.salesforce.connect;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.message.Message;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.credentials.base.CredentialCache;
import org.knime.credentials.base.oauth.api.AccessTokenCredential;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.credential.SalesforceAuthenticationUtil;
import org.knime.salesforce.auth.port.LegacySalesforceConnectionLoader;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObject;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.connect.SalesforceConnectorNodeSettings.AuthType;
import org.knime.salesforce.connect.SalesforceConnectorNodeSettings.InstanceType;

import com.github.scribejava.apis.salesforce.SalesforceToken;

/**
 * Salesforce Authentication (deprecated) node model.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeModel extends NodeModel {

    private static final String NOT_AUTHENTICATED_MSG = "Not authenticated (open configuration to do so)";

    private SalesforceConnectorNodeSettings m_settings;

    private UUID m_credentialCacheKey;

    SalesforceConnectorNodeModel() {
        super(new PortType[0], new PortType[] {SalesforceConnectionPortObject.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[] {configureInternal()};
    }

    private SalesforceConnectionPortObjectSpec configureInternal() throws InvalidSettingsException {
        m_credentialCacheKey = null;

        // Check if the user is authenticated
        CheckUtils.checkSettingNotNull(m_settings, "No configuration available");

        final var isInteractiveInMemory = m_settings.getAuthType() == AuthType.Interactive
                && m_settings.getCredentialsSaveLocation() == CredentialsLocationType.MEMORY;

        if (isInteractiveInMemory) {
            final var salesforceAuth =
                InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().get(m_settings.getNodeInstanceID());
            m_credentialCacheKey = salesforceAuth.map(SalesforceAuthentication::toCredential)//
                .map(CredentialCache::store).orElse(null);
        }

        final var credSpec = new SalesforceConnectionPortObjectSpec(//
            m_credentialCacheKey, //
            m_settings.getTimeouts());

        if (isInteractiveInMemory) {
            CheckUtils.checkSetting(credSpec.isPresent(), NOT_AUTHENTICATED_MSG);
        }

        return credSpec;
    }

    @Override
    protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec)
        throws Exception {

        final var credential = getCredential();
        final var cacheId = CredentialCache.store(credential);
        final var spec = new SalesforceConnectionPortObjectSpec(cacheId, m_settings.getTimeouts());

        return new PortObject[] {new SalesforceConnectionPortObject(spec)};
    }


    private SalesforceAccessTokenCredential getCredential() throws IOException, KNIMEException {
        return switch (m_settings.getAuthType()) {
            case UsernamePassword -> createCredentialWithUsernamePassword();
            case Interactive -> retrieveInteractiveCredential();
            default -> throw new IllegalStateException();
        };
    }

    private SalesforceAccessTokenCredential retrieveInteractiveCredential() throws IOException, KNIMEException {
        final var auth = m_settings.loadAuthentication()
            .orElseThrow(() -> KNIMEException.of(Message.fromSummary(NOT_AUTHENTICATED_MSG)));

        return auth.toCredential();
    }

    private SalesforceToken fetchSalesforceTokenWithUsernamePassword() throws IOException {
        final var userPassModel = m_settings.getUsernamePasswortAuthenticationModel();

        final var user = userPassModel.getUserName(getCredentialsProvider());
        final var password = userPassModel.getPassword(getCredentialsProvider());
        final var securityToken = m_settings.getPasswordSecurityToken();
        final var isSandbox = m_settings.getSalesforceInstanceType() == InstanceType.TestInstance;

        return SalesforceAuthenticationUtil.authenticateUsingUserAndPassword(//
            user, password, securityToken, //
            isSandbox, //
            m_settings.getTimeouts());
    }

    private AccessTokenCredential refreshAccessTokenWithUsernamePassword() {
        try {
            final var sfToken = fetchSalesforceTokenWithUsernamePassword();

            return new AccessTokenCredential(//
                sfToken.getAccessToken(), //
                null, //
                sfToken.getTokenType(), //
                this::refreshAccessTokenWithUsernamePassword);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AccessTokenCredential toAccessTokenCredential(final SalesforceToken sfToken) {
        return new AccessTokenCredential(//
            sfToken.getAccessToken(), //
            null, //
            sfToken.getTokenType(), //
            this::refreshAccessTokenWithUsernamePassword);
    }

    private SalesforceAccessTokenCredential createCredentialWithUsernamePassword() throws IOException {

        final var sfToken = fetchSalesforceTokenWithUsernamePassword();

        return new SalesforceAccessTokenCredential(//
            URI.create(sfToken.getInstanceUrl()), //
            toAccessTokenCredential(sfToken));
    }

    @Override
    protected void reset() {
        if (m_credentialCacheKey != null) {
            CredentialCache.delete(m_credentialCacheKey);
            m_credentialCacheKey = null;
        }
    }

    @Override
    protected void onDispose() {
        reset();
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        if (m_settings != null) {
            m_settings.saveSettingsInModel(settings);
        }
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        SalesforceConnectorNodeSettings.loadInModel(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings = SalesforceConnectorNodeSettings.loadInModel(settings);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {

        switch (m_settings.getAuthType()) {
            case Interactive:
                tryRestoreInteractiveAuthenticationFromSettings();
                break;
            case UsernamePassword:
                restoreUserPasswordAuthenticationFromNodeInternals(nodeInternDir);
                break;
            default:
                break; // nothing to do
        }
    }

    private void restoreUserPasswordAuthenticationFromNodeInternals(final File nodeInternDir) throws IOException {

        final var internalsFile = getInternalsPath(nodeInternDir);
        // In AP 5.2 and earlier this node saved the credential with the workflow
        // this restores such a saved credential so that old workflows which were
        // saved in executed form continue to work
        if (Files.isRegularFile(internalsFile)) {
            try (final var in = Files.newInputStream(internalsFile)) {

                // ugly hack to be able to pass the credential to the output port object
                // of this legacy authenticator node, if it has been saved in executed state
                final var auth = SalesforceAuthentication.load(NodeSettings.loadFromXML(in));
                final var fakeSfToken = new SalesforceToken(//
                    auth.getAccessToken(),//
                    "Bearer",//
                    null,//
                    auth.getRefreshToken().orElse(null),//
                    null, auth.getInstanceURLString(), "");

                // unfortunately we cannot use SalesforceAuthentication.toCredential() because it cannot
                // create a refreshable credential (it does not contain username/password)
                final var restoredCredential = new SalesforceAccessTokenCredential(//
                    URI.create(auth.getInstanceURLString()),//
                    toAccessTokenCredential(fakeSfToken));

                LegacySalesforceConnectionLoader.addToLegacyCache(//
                    m_settings.getNodeInstanceID(), //
                    restoredCredential);

            } catch (InvalidSettingsException ex) {
                throw new IOException(ex);
            }
        } else {
            setWarningMessage("Credential not available anymore. Please re-execute this node.");
        }
    }

    private void tryRestoreInteractiveAuthenticationFromSettings() throws IOException {
        final var auth = m_settings.loadAuthentication();
        if (auth.isPresent()) {
            LegacySalesforceConnectionLoader.addToLegacyCache(//
                m_settings.getNodeInstanceID(), //
                auth.get().toCredential());
        }
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // not doing anything here
    }

    private static Path getInternalsPath(final File nodeInternDir) {
        return nodeInternDir.toPath().resolve("internals.xml");
    }
}
