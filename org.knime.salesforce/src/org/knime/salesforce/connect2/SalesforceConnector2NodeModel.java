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
package org.knime.salesforce.connect2;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.impl.WebUINodeModel;
import org.knime.credentials.base.CredentialCache;
import org.knime.credentials.base.CredentialPortObject;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.CredentialRef;
import org.knime.credentials.base.oauth.api.AccessTokenCredential;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.credential.SalesforceAuthenticationUtil;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObject;
import org.knime.salesforce.auth.port.SalesforceConnectionPortObjectSpec;
import org.knime.salesforce.connect2.SalesforceConnector2NodeSettings.AuthType;
import org.knime.salesforce.connect2.SalesforceConnector2NodeSettings.InstanceType;

import com.github.scribejava.apis.salesforce.SalesforceToken;

/**
 * Salesforce Connector node model.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
final class SalesforceConnector2NodeModel extends WebUINodeModel<SalesforceConnector2NodeSettings> {

    private static final String LOGIN_FIRST_ERROR = "Please use the configuration dialog to log in first.";

    /**
     * This references a {@link SalesforceAccessTokenCredential} that was acquired interactively in the node dialog. It
     * is disposed when the workflow is closed, or when the authentication scheme is switched to non-interactive.
     * However, it is NOT disposed during reset().
     */
    private CredentialRef m_interactiveCredentialRef;

    private UUID m_credentialCacheKey;

    SalesforceConnector2NodeModel(final PortsConfiguration config,
        final Class<SalesforceConnector2NodeSettings> settings) {
        super(config.getInputPorts(), config.getOutputPorts(), settings);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs,
        final SalesforceConnector2NodeSettings settings) throws InvalidSettingsException {

        final UUID cacheId;

        if (settings.checkPortAndValidateOnConfigure(inSpecs)) {
            disposeInteractiveCredential();
            final var scpo = (CredentialPortObjectSpec)inSpecs[0];
            m_credentialCacheKey = scpo.getCredential(SalesforceAccessTokenCredential.class) //
                .map(CredentialCache::store).orElse(null);
            cacheId = m_credentialCacheKey;

        } else if (settings.m_authType == AuthType.INTERACTIVE) {
            m_interactiveCredentialRef = Optional.ofNullable(settings.m_loginCredentialRef)//
                .map(CredentialRef::new)//
                .orElseThrow(() -> new InvalidSettingsException(LOGIN_FIRST_ERROR));
            cacheId = settings.m_loginCredentialRef;
        } else {
            disposeInteractiveCredential();
            cacheId = null;
        }

        return new PortObjectSpec[]{new SalesforceConnectionPortObjectSpec(cacheId, settings.getTimeouts())};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec,
        final SalesforceConnector2NodeSettings settings) throws Exception {

        final var timeouts = settings.getTimeouts();

        final SalesforceAccessTokenCredential credential;

        if (settings.checkPortAndValidateOnExecute(inData)) {
            final var scpo = (CredentialPortObject)inData[0];
            credential = scpo.getSpec().resolveCredential(SalesforceAccessTokenCredential.class);
        } else {
            credential = switch (settings.m_authType) {
                case INTERACTIVE -> m_interactiveCredentialRef.resolveCredential(SalesforceAccessTokenCredential.class);
                case USERNAME_PASSWORD -> createCredentialWithUsernamePassword(settings);
            };
        }

        m_credentialCacheKey = CredentialCache.store(credential);

        var spec = new SalesforceConnectionPortObjectSpec(m_credentialCacheKey, timeouts);
        return new PortObject[]{new SalesforceConnectionPortObject(spec)};
    }

    private static SalesforceAccessTokenCredential
        createCredentialWithUsernamePassword(final SalesforceConnector2NodeSettings settings) throws IOException {

        final var sfToken = fetchSalesforceTokenWithUsernamePassword(settings);

        return new SalesforceAccessTokenCredential(//
            URI.create(sfToken.getInstanceUrl()), //
            toAccessTokenCredential(sfToken, settings));
    }

    private static SalesforceToken
        fetchSalesforceTokenWithUsernamePassword(final SalesforceConnector2NodeSettings settings) throws IOException {

        final var userPassCreds = settings.m_usernamePasswordCredentials;
        final var isSandbox = settings.m_salesforceInstanceType == InstanceType.SANDBOX;

        return SalesforceAuthenticationUtil.authenticateUsingUserAndPassword(//
            userPassCreds.getUsername(), //
            userPassCreds.getPassword(), //
            userPassCreds.getSecondFactor(), //
            isSandbox, //
            settings.getTimeouts(),//
            settings.getClientApp());
    }

    private static AccessTokenCredential toAccessTokenCredential(final SalesforceToken sfToken,
        final SalesforceConnector2NodeSettings settings) {

        final Instant expiresAfter = sfToken.getExpiresIn() != null//
            ? Instant.now().plusSeconds(sfToken.getExpiresIn()) //
            : null;

        return new AccessTokenCredential(//
            sfToken.getAccessToken(), //
            expiresAfter, //
            sfToken.getTokenType(), //
            () -> refreshAccessTokenWithUsernamePassword(settings));
    }

    private static AccessTokenCredential
        refreshAccessTokenWithUsernamePassword(final SalesforceConnector2NodeSettings settings) {

        try {
            final var sfToken = fetchSalesforceTokenWithUsernamePassword(settings);
            return toAccessTokenCredential(sfToken, settings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void disposeInteractiveCredential() {
        if (m_interactiveCredentialRef != null) {
            m_interactiveCredentialRef.dispose();
            m_interactiveCredentialRef = null;
        }
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
        disposeInteractiveCredential();
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        setWarningMessage("Credential not available anymore. Please re-execute this node.");
    }
}
