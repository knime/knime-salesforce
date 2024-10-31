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
 *  This program is distributed in the hope that it will be useful, but.
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

import static org.knime.salesforce.auth.credential.SalesforceAuthenticationUtil.refreshToken;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.After;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.field.Persist;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.Credentials;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.NumberInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextMessage;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextMessage.MessageType;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextMessage.SimpleTextMessageProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.button.ButtonWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.button.CancelableActionHandler;
import org.knime.core.webui.node.dialog.defaultdialog.widget.credentials.CredentialsWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.WidgetHandlerException;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Predicate;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.PredicateProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Reference;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.ValueReference;
import org.knime.credentials.base.CredentialCache;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.oauth.api.AccessTokenCredential;
import org.knime.credentials.base.oauth.api.nodesettings.TokenCacheKeyPersistor;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.credential.SalesforceAuthenticationUtil;
import org.knime.salesforce.auth.credential.SalesforceAuthenticationUtil.ClientApp;
import org.knime.salesforce.connect2.SalesforceConnector2NodeSettings.AuthType.IsInteractiveAndHasNoCredentialPort;
import org.knime.salesforce.connect2.SalesforceConnector2NodeSettings.AuthType.IsUsernamePasswordAndHasNoCredentialPort;
import org.knime.salesforce.rest.Timeouts;

import com.github.scribejava.apis.SalesforceApi;
import com.github.scribejava.apis.salesforce.SalesforceToken;

/**
 * Settings store managing all configurations required for the node.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
final class SalesforceConnector2NodeSettings implements DefaultNodeSettings {

    private static boolean hasCredentialPort(final DefaultNodeSettingsContext context) {
        return Arrays.stream(context.getInPortTypes())
            .anyMatch(inPortType -> CredentialPortObjectSpec.class.equals(inPortType.getPortObjectSpecClass()));
    }

    static final class AuthenticationManagedByPortMessage implements SimpleTextMessageProvider {

        @Override
        public boolean showMessage(final DefaultNodeSettingsContext context) {
            return hasCredentialPort(context);
        }

        @Override
        public String title() {
            return "Authentication managed by Credential Input Port";
        }

        @Override
        public String description() {
            return "Remove the Credential Input Port to use methods Interactive or Username/Password";
        }

        @Override
        public MessageType type() {
            return MessageType.INFO;
        }

    }

    @TextMessage(value = AuthenticationManagedByPortMessage.class)
    Void m_authenticationManagedByPortText;

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SalesforceConnector2NodeSettings.class);

    @Section(title = "Credentials")
    @Effect(predicate = IsUsernamePasswordAndHasNoCredentialPort.class, type = EffectType.SHOW)
    interface UsernamePasswordSection {
    }

    @Section(title = "Salesforce Instance", advanced = true)
    @Effect(predicate = HasNoCredentialPort.class, //
        type = EffectType.SHOW)
    @After(UsernamePasswordSection.class)
    interface SalesforceInstanceSection {
    }

    @Section(title = "Client/App", advanced = true)
    @Effect(predicate = HasNoCredentialPort.class, //
        type = EffectType.SHOW)
    @After(SalesforceInstanceSection.class)
    interface AppSection {
    }

    @Section(title = "Timeouts", advanced = true)
    @After(AppSection.class)
    interface TimeoutsSection {
    }

    @Section(title = "Authentication")
    @Effect(predicate = IsInteractiveAndHasNoCredentialPort.class, type = EffectType.SHOW)
    @After(TimeoutsSection.class)
    interface AuthenticationSection {
    }

    @Widget(title = "Authentication Type", description = """
            Authentication type to use. The following types are supported:
              <ul>
                  <li><b>Interactive</b>: Authenticate interactively with Salesforce.</li>
                  <li><b>Username/Password</b>: Enter username, password and security token directly.
                                                The <i>Security Token</i> can be reset in the Account
                                                settings of the Salesforce user.</li>
                  <li><b>Salesforce Authentication Port</b> (if connected):
                                                Authenticate using by connecting a Salesforce credential
                                                to the optional input port.</li>
             </ul>
            """)
    @ValueReference(AuthTypeRef.class)
    @Effect(predicate = HasNoCredentialPort.class, type = EffectType.ENABLE)
    AuthType m_authType = AuthType.INTERACTIVE;

    @Widget(title = "User credentials", description = """
              Specify the username, password and security token (optional) to use.
            """)
    @CredentialsWidget(hasSecondAuthenticationFactor = true, secondFactorLabel = "Security Token")
    @Layout(UsernamePasswordSection.class)
    Credentials m_usernamePasswordCredentials = new Credentials();

    @Widget(title = "Which Salesforce instance to use", description = """
              Specifies the Salesforce instance to connect to. The following values are supported:
                <ul>
                  <li><b>Production</b>: Connect to the production instance. If in doubt, use this.</li>
                  <li><b>Sandbox</b>: Connect to the sandbox instance.</li>
                </ul>
            """)
    @ValueSwitchWidget
    @Layout(SalesforceInstanceSection.class)
    InstanceType m_salesforceInstanceType = InstanceType.PRODUCTION;

    @Widget(title = "Which Connected App to use", description = """
            The <a href="https://help.salesforce.com/s/articleView?id=sf.connected_app_overview.htm">
            Connected App</a> to use when connecting to Salesforce. The following values are supported:
            <ul>
              <li><b>Default</b>: Connect using the KNIME Analytics Platform default app</li>
              <li><b>Custom</b>: Connect using a custom Connected App.</li>
            </ul>
            """)
    @ValueSwitchWidget
    @Layout(AppSection.class)
    @ValueReference(ConnectedAppTypeRef.class)
    ConnectedAppType m_appType = ConnectedAppType.DEFAULT;

    @Widget(title = "Custom App", description = """
              Specify the client id and client secret to use.
            """)
    @CredentialsWidget(usernameLabel = "ID", passwordLabel = "Secret") // NOSONAR: no PASSWORD here
    @Layout(AppSection.class)
    @Effect(predicate = ConnectedAppType.IsCustom.class, type = EffectType.SHOW)
    Credentials m_customClientAppCredentials = new Credentials();

    @Widget(title = "Connection timeout (seconds)", //
        description = "The HTTP connection timeout used in this node and downstream Salesforce nodes.")
    @Layout(TimeoutsSection.class)
    @NumberInputWidget(min = 0)
    int m_connectionTimeout = 30;

    @Widget(title = "Read timeout (seconds)", //
        description = "The HTTP read timeout used in this node and downstream Salesforce nodes.")
    @Layout(TimeoutsSection.class)
    @NumberInputWidget(min = 0)
    int m_readTimeout = 60;

    @ButtonWidget(actionHandler = LoginActionHandler.class, //
        updateHandler = LoginUpdateHandler.class, //
        showTitleAndDescription = false)
    @Widget(title = "Login", description = """
            Clicking on login opens a new browser window/tab which
            allows to interactively log into the service.""")
    @Layout(AuthenticationSection.class)
    @Persist(optional = true, hidden = true, customPersistor = TokenCacheKeyPersistor.class)
    @Effect(predicate = AuthType.IsInteractive.class, type = EffectType.SHOW)
    UUID m_loginCredentialRef;

    Timeouts getTimeouts() {
        return new Timeouts(m_connectionTimeout, m_readTimeout);
    }

    ClientApp getClientApp() {
        return switch (m_appType) {
            case DEFAULT -> SalesforceAuthenticationUtil.DEFAULT_CLIENTAPP;
            case CUSTOM -> new ClientApp(m_customClientAppCredentials.getUsername(),
                m_customClientAppCredentials.getPassword());
        };
    }

    boolean isSandBox() {
        return m_salesforceInstanceType == InstanceType.SANDBOX;
    }

    boolean checkPortAndValidateOnConfigure(final PortObjectSpec[] specs) throws InvalidSettingsException {
        return checkPortAndValidate(specs);
    }

    boolean checkPortAndValidateOnExecute(final PortObject[] specs) throws InvalidSettingsException {
        return checkPortAndValidate(Arrays.stream(specs).map(PortObject::getSpec).toArray(PortObjectSpec[]::new));
    }

    private boolean checkPortAndValidate(final PortObjectSpec[] specs) throws InvalidSettingsException {
        CheckUtils.checkSetting(m_connectionTimeout >= 0, "Please specify a non-negative connection timeout");
        CheckUtils.checkSetting(m_readTimeout >= 0, "Please specify a non-negative read timeout");

        if (credentialPortConnected(specs)) {
            // Credential port type
            return true;
        }

        if (m_authType == AuthType.USERNAME_PASSWORD && //
            (StringUtils.isBlank(m_usernamePasswordCredentials.getUsername()) || //
                StringUtils.isBlank(m_usernamePasswordCredentials.getPassword()))) {
            throw new InvalidSettingsException(
                "Please specify the username, password and optionally the security token of the Salesforce account "
                    + "to use");
        }

        if (m_appType == ConnectedAppType.CUSTOM && //
            (StringUtils.isBlank(m_customClientAppCredentials.getUsername()) || //
                StringUtils.isBlank(m_customClientAppCredentials.getPassword()))) {
            throw new InvalidSettingsException("Please specify both the client ID and secret of the custom app to use");
        }
        return false;
    }

    static boolean credentialPortConnected(final PortObjectSpec[] specs) throws InvalidSettingsException {
        for (var i = 0; i < specs.length; i++) {
            if (specs[i] instanceof CredentialPortObjectSpec spec) {
                final var optCredType = spec.getCredentialType();
                if (optCredType.isPresent()) {
                    CheckUtils.checkSetting(optCredType.get() == SalesforceAccessTokenCredential.TYPE, //
                        "Ingoing credential is incompatible, it must be a Salesforce credential.");
                }
                return true;
            }
        }
        return false;
    }

    static class LoginActionHandler extends CancelableActionHandler<UUID, SalesforceConnector2NodeSettings> {

        @Override
        protected UUID invoke(final SalesforceConnector2NodeSettings settings, final DefaultNodeSettingsContext context)
            throws WidgetHandlerException {
            try {
                settings.checkPortAndValidate(context.getPortObjectSpecs());
            } catch (InvalidSettingsException e) { // NOSONAR
                throw new WidgetHandlerException(e.getMessage());
            }

            Future<SalesforceToken> future = null;

            try {

                future = SalesforceAuthenticationUtil //
                    .authenticateInteractively(settings.isSandBox(), settings.getClientApp());

                final var sfToken = future.get();

                final Instant expiresAfter = sfToken.getExpiresIn() != null//
                    ? Instant.now().plusSeconds(sfToken.getExpiresIn()) //
                    : null;

                final Supplier<AccessTokenCredential> refresher = sfToken.getRefreshToken() != null//
                    ? (() -> refreshToken(sfToken.getRefreshToken(), settings.isSandBox()))//
                    : null;

                final var accessTokenCred = new AccessTokenCredential(//
                    sfToken.getAccessToken(), //
                    expiresAfter, //
                    sfToken.getTokenType(), //
                    refresher);

                final var credential = new SalesforceAccessTokenCredential(//
                    URI.create(sfToken.getInstanceUrl()), //
                    accessTokenCred);

                return CredentialCache.store(credential);

            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw new WidgetHandlerException(e.getMessage());
            } catch (InterruptedException e) { // NOSONAR
                if (!future.isDone()) {
                    future.cancel(true);
                }
                LOGGER.error(e.getMessage(), e);
                throw new WidgetHandlerException("Login process was interrupted!");
            } catch (ExecutionException e) {
                LOGGER.error(e.getMessage(), e);
                throw new WidgetHandlerException("An error occurred during the login process!");
            }
        }

        @Override
        protected String getButtonText(final States state) {
            return switch (state) {
                case READY -> "Login";
                case CANCEL -> "Cancel login";
                case DONE -> "Login again";
                default -> null;
            };
        }
    }

    static class LoginUpdateHandler
        extends CancelableActionHandler.UpdateHandler<UUID, SalesforceConnector2NodeSettings> {
    }

    /**
     * Constant signal to indicate whether the user has added a credential port or not.
     */
    static final class HasNoCredentialPort implements PredicateProvider {
        @Override
        public Predicate init(final PredicateInitializer i) {
            // to guide the user to the error we disable the GUI also if the credential
            // input is not of the correct type per se
            return i.getConstant(context -> !hasCredentialPort(context));
        }
    }

    static final class AuthTypeRef implements Reference<AuthType> {
    }

    enum AuthType {
            @Label("Interactive")
            INTERACTIVE,

            @Label("Username/Password")
            USERNAME_PASSWORD;

        static final class IsUsernamePasswordAndHasNoCredentialPort implements PredicateProvider {
            @Override
            public Predicate init(final PredicateInitializer i) {
                return i.getEnum(AuthTypeRef.class).isOneOf(USERNAME_PASSWORD)
                    .and(i.getPredicate(HasNoCredentialPort.class));
            }
        }

        static final class IsInteractive implements PredicateProvider {
            @Override
            public Predicate init(final PredicateInitializer i) {
                return i.getEnum(AuthTypeRef.class).isOneOf(INTERACTIVE);
            }
        }

        static final class IsInteractiveAndHasNoCredentialPort implements PredicateProvider {
            @Override
            public Predicate init(final PredicateInitializer i) {
                return i.getPredicate(IsInteractive.class).and(i.getPredicate(HasNoCredentialPort.class));
            }
        }
    }

    /** Use 'production' instance or a test instance (as per {@link SalesforceApi#sandbox()}. */
    public enum InstanceType {

            /** Corresponds to {@link SalesforceApi#instance()}. */
            @Label("Production")
            PRODUCTION, //
            /** Corresponds to {@link SalesforceApi#sandbox()}. */
            @Label("Sandbox")
            SANDBOX;
    }

    static final class ConnectedAppTypeRef implements Reference<ConnectedAppType> {
    }

    enum ConnectedAppType {
            @Label("Default")
            DEFAULT, //
            @Label("Custom")
            CUSTOM;

        static final class IsCustom implements PredicateProvider {
            @Override
            public Predicate init(final PredicateInitializer i) {
                if (i.isMissing(ConnectedAppTypeRef.class)) {
                    return i.never();
                }
                return i.getEnum(ConnectedAppTypeRef.class).isOneOf(CUSTOM);
            }
        }
    }
}
