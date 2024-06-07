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
package org.knime.salesforce.auth.credential;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.DesktopUtil;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.ThreadLocalHTTPAuthenticator.AuthenticationCloseable;
import org.knime.credentials.base.oauth.api.AccessTokenCredential;
import org.knime.salesforce.rest.SalesforceRESTUtil;
import org.knime.salesforce.rest.Timeouts;

import com.github.scribejava.apis.SalesforceApi;
import com.github.scribejava.apis.salesforce.SalesforceToken;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.github.scribejava.core.pkce.PKCE;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.AbstractFuture;

import jakarta.json.JsonString;
import jakarta.json.stream.JsonParsingException;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response.Status;

/**
 * Static utility class to authenticate with Salesforce.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class SalesforceAuthenticationUtil {

    private static final NodeLogger LOGGER =  NodeLogger.getLogger(SalesforceAuthenticationUtil.class);

    /** The data of the app registration (for KNIME AP) */
    public static final ClientApp DEFAULT_CLIENTAPP = new ClientApp( //
        "3MVG9LzKxa43zqdJIdD5755PBiXKqt29EVi.z6v_mIZbhn0rXJY62p.rUdyLti3mlMU0XR1CfoMjk4iQwXYlI", // id
        "678C2BF0272EC1D3E2A6C96AF6315BF14E1C79511728867A8CE7E2449D9BC837" ); // secret

    /** Only one OAuth flow can be in progress at one time. This value tracks if there is one in progress currently. */
    private static final AtomicBoolean OAUTH_IN_PROGRESS = new AtomicBoolean(false);

    /** The port of the callback server */
    private static final int OAUTH_CALLBACK_PORT = 51778;

    private static final String OAUTH_LISTENER_PATH = "/salesforce_oauth"; // NOSONAR nope...

    private static final String OAUTH_CALLBACK_URL = "http://localhost:" + OAUTH_CALLBACK_PORT + OAUTH_LISTENER_PATH;

    private static final String OAUTH_SUCCESS_PAGE = "Received verification code. You may now close this window...";

    private static final String OAUTH_ERROR_PAGE = "Authentication failed.\n";

    private SalesforceAuthenticationUtil() {
        // Utility class
    }

    /**
     * Performs authentication using username, password and security token (can create a new one in the user's account
     * settings on Salesforce.com).
     *
     * @param user
     * @param password
     * @param securityToken
     * @param isUseSandbox production or test instance?
     * @param timeouts http connect/read timeouts.
     * @return The {@link SalesforceToken}, not null.
     * @throws IOException if authentication has failed.
     */
    public static SalesforceToken authenticateUsingUserAndPassword(final String user, final String password,
        final String securityToken, final boolean isUseSandbox, final Timeouts timeouts) throws IOException {

        return authenticateUsingUserAndPassword(user, password, securityToken,
            isUseSandbox, timeouts, DEFAULT_CLIENTAPP);
        }

    /**
     * Performs authentication using username, password and security token (can create a new one in the user's account
     * settings on Salesforce.com).
     *
     * @param user
     * @param password
     * @param securityToken
     * @param isUseSandbox production or test instance?
     * @param timeouts http connect/read timeouts.
     * @param clientApp client app id and secret to use
     * @return The authentication object, not null.
     * @throws IOException if authentication has failed.
     */
    public static SalesforceToken authenticateUsingUserAndPassword(final String user, final String password,  // NOSONAR
        final String securityToken, final boolean isUseSandbox, final Timeouts timeouts, final ClientApp clientApp)
        throws IOException {

        CheckUtils.checkArgument(StringUtils.isNotEmpty(user), "User must not be empty (or null)");
        CheckUtils.checkArgument(StringUtils.isNotEmpty(password), "Password must not be empty");
        CheckUtils.checkArgumentNotNull(securityToken, "Security token must not be null");
        CheckUtils.checkArgumentNotNull(timeouts, "Timeout setting must not be null");

        final var api = isUseSandbox ? SalesforceApi.sandbox() : SalesforceApi.instance();
        final var client = WebClient.create(URI.create(api.getAccessTokenEndpoint()));

        // Set the timeout
        final var httpConduit = WebClient.getConfig(client).getHttpConduit();
        httpConduit.getClient().setConnectionTimeout(1000L * timeouts.connectionTimeoutS());
        httpConduit.getClient().setReceiveTimeout(1000L * timeouts.readTimeoutS());

        client.accept(MediaType.APPLICATION_JSON);

        MultivaluedHashMap<String, String> formData = new MultivaluedHashMap<>();
        formData.put("grant_type", Collections.singletonList("password"));
        formData.put("client_id", Collections.singletonList(clientApp.clientId));
        formData.put("client_secret", Collections.singletonList(clientApp.clientSecret));
        formData.put("username", Collections.singletonList(user));
        formData.put("password", Collections.singletonList(password + securityToken));

        try (final AuthenticationCloseable c = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
            final var response = client.form(new Form(formData))) {
            final var responseStr = response.readEntity(String.class);
            final var json = SalesforceRESTUtil.readAsJsonStructure(responseStr).asJsonObject();

            if (response.getStatusInfo().getFamily() != Status.Family.SUCCESSFUL) {
                if (response.getStatus() == 400) {
                    // credentials were rejected
                    throw new IOException("Authentication failed. Please provide valid credentials.");
                }

                final var errorDescriptionValue = json.getValue("/error_description");
                final var errorDescription =  errorDescriptionValue != null//
                        ? ((JsonString)errorDescriptionValue).getString()//
                        : null;

                throw new IOException(//
                    String.format("Authentication failed (HTTP %d: %s)",//
                        response.getStatus(), //
                        errorDescription != null//
                            ? errorDescription//
                            : response.getStatusInfo().getReasonPhrase()));
            }

            final var accessToken = json.getString("access_token");
            final var tokenType = json.getString("token_type");
            final var instanceUrl = json.getString("instance_url");
            final var refreshToken = json.getString("refresh_token", null);

            return new SalesforceToken(accessToken,//
                tokenType,//
                null,// expiresIn
                refreshToken,//
                null,//
                instanceUrl,//
                responseStr);
        } catch (JsonParsingException e) {
            throw new IOException("Could not parse reponse. The Client/App data may be incorrect or malformed. "
                + "Try loggin in interactively.", e);
        } catch (RuntimeException e) {
            throw new IOException(e);
        } finally {
            client.close();
        }
    }

    /**
     * Uses the given refresh token to fetch a new {@link AccessTokenCredential}.
     *
     * @param refreshToken the refresh token to use.
     * @param isSandbox whether or not to acquire an access token for the Salesforce sandbox.
     * @return a newly acquired {@link AccessTokenCredential}
     */
    public static AccessTokenCredential refreshToken(final String refreshToken, final boolean isSandbox) {
        return refreshToken(refreshToken, isSandbox, DEFAULT_CLIENTAPP);
    }



    /**
     * Uses the given refresh token to fetch a new {@link AccessTokenCredential}.
     *
     * @param refreshToken the refresh token to use.
     * @param isSandbox whether or not to acquire an access token for the Salesforce sandbox.
     * @param clientApp client app id and secret to use
     * @return a newly acquired {@link AccessTokenCredential}
     */
    public static AccessTokenCredential refreshToken(
        final String refreshToken, final boolean isSandbox, final ClientApp clientApp) {

        final var api = isSandbox ? SalesforceApi.sandbox() : SalesforceApi.instance();
        try (final var service = new ServiceBuilder(clientApp.clientId).build(api)) {
            final var newToken = (SalesforceToken)service.refreshAccessToken(refreshToken);

            Supplier<AccessTokenCredential> refresher = null;
            if (newToken.getRefreshToken() != null) {
                refresher = () -> refreshToken(newToken.getRefreshToken(), isSandbox);
            }

            final var newTokenExpiry = newToken.getExpiresIn() != null//
                    ? Instant.now().plusSeconds(newToken.getExpiresIn())//
                    : null;

            return new AccessTokenCredential(//
                newToken.getAccessToken(),//
                newTokenExpiry,//
                newToken.getTokenType(), //
                refresher);

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException ex) {// NOSONAR
            throw new UncheckedIOException(new IOException(ex));
        } catch (ExecutionException ex) {
            final var cause = ex.getCause();
            if (cause instanceof IOException ioeCause) {
                throw new UncheckedIOException(ioeCause);
            } else {
                throw new UncheckedIOException(new IOException(ex));
            }
        }
    }


    /**
     * Authenticate with Salesforce using OAuth2. Note that a web browser will be opened asking the user to login. This
     * call does not block until the user has authenticated. Wait until the future is done.
     *
     * Cancel the future to cancel the authentication process.
     *
     * @param isUseSandbox where to connect to (as per {@link SalesforceApi}.
     *
     * @return A future holding the authentication
     * @throws IOException if the authentication fails because of any reason
     */
    public static Future<SalesforceToken> authenticateInteractively(final boolean isUseSandbox)
             throws IOException {
        return authenticateInteractively(isUseSandbox, DEFAULT_CLIENTAPP);
    }

    /**
     * Authenticate with Salesforce using OAuth2. Note that a web browser will be opened asking the user to login. This
     * call does not block until the user has authenticated. Wait until the future is done.
     *
     * Cancel the future to cancel the authentication process.
     *
     * @param isUseSandbox where to connect to (as per {@link SalesforceApi}.
     * @param clientApp client app id and secret to use
     *
     * @return A future holding the authentication
     * @throws IOException if the authentication fails because of any reason
     */
    @SuppressWarnings("resource") // The service and server are closed by a waiting thread
    public static Future<SalesforceToken> authenticateInteractively(
        final boolean isUseSandbox, final ClientApp clientApp)
                throws IOException {

        if (!OAUTH_IN_PROGRESS.getAndSet(true)) {

            // The future authentication object
            final var authFuture = new SalesforceAuthenticationFuture();

            // The OAuth20 service
            final OAuth20Service service = new ServiceBuilder(clientApp.clientId) //
                .apiSecret(clientApp.clientSecret) //
                .callback(OAUTH_CALLBACK_URL) //
                .build(isUseSandbox ? SalesforceApi.sandbox() : SalesforceApi.instance());

            final var authorizationUrlBuilder = service.createAuthorizationUrlBuilder()//
                    .initPKCE();

            var callbackServer = new Server(OAUTH_CALLBACK_PORT);
            var callbackHandler = new OAuthCallbackHandler(service, authorizationUrlBuilder.getPkce(), authFuture);
            callbackServer.setHandler(callbackHandler);
            try {
                callbackServer.start();
            } catch (Exception ex) {
                throw new IOException("Could not start callback server: " + ex.getMessage(), ex);
            }

            // Start a thread which closes the service and everything once the authentication is done
            startClosingThread(authFuture, service, callbackServer);


            // Open the browser and show the authentication site
            try {
                DesktopUtil.browse(new URL(authorizationUrlBuilder.build()));
            } catch (final MalformedURLException ex) {
                authFuture.cancel(true);
                throw new IOException("Malformed authentication URL: " + ex.getMessage(), ex);
            }

            return authFuture;
        } else {
            // Another authentication is already in progress
            // We cannot do this in parallel because we open a webserver with a port
            throw new IOException("A authentication with Salesforce is already in progress. "
                + "Wait until the other authentication process is done or cancel it.");
        }
    }

    @SuppressWarnings("resource") // we must not close the servlet output stream
    private static class OAuthCallbackHandler extends Handler.Abstract {

        private final SalesforceAuthenticationFuture m_authFuture;

        private final OAuth20Service m_service;

        private final PKCE m_pkce;

        OAuthCallbackHandler(final OAuth20Service service, final PKCE pkce,
            final SalesforceAuthenticationFuture authFuture) {

            m_service = service;
            m_pkce= pkce;
            m_authFuture = authFuture;
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback)
            throws Exception {

            if (!OAUTH_LISTENER_PATH.equals(request.getHttpURI().getPath())) {
                Response.writeError(request, response, callback, HttpStatus.NOT_FOUND_404);
            } else {

                try {
                    // Get the auth code
                    final Optional<String> authCode = getAuthCodeFromRequest(request);
                    if (authCode.isPresent()) {

                        // Request a token
                        final var params = AccessTokenRequestParams.create(authCode.get())//
                                .pkceCodeVerifier(m_pkce.getCodeVerifier());
                        final var salesforceToken = (SalesforceToken)m_service.getAccessToken(params);
                        // Set the future result
                        m_authFuture.setResult(salesforceToken);
                        configureResponse(response, 200, OAUTH_SUCCESS_PAGE, callback);
                    } else {
                        throw new IOException(getErrorFromRequest(request));
                    }
                } catch (final Throwable t) { // NOSONAR
                    m_authFuture.setFailed(t);
                    configureResponse(response, 400, OAUTH_ERROR_PAGE + t.getMessage(), callback);
                }
            }

            return true; // NOSONAR
        }

        private static void configureResponse(final Response response, final int status, final String message,
            final Callback callback) {

            response.getHeaders().put(HttpHeader.CONTENT_TYPE, MediaType.TEXT_HTML);
            response.setStatus(status);
            Content.Sink.write(response, true, message, callback);
        }

        /** Get the auth code from the parameters of a request */
        private static Optional<String> getAuthCodeFromRequest(final Request request) {
            try {
                return Optional.ofNullable(Request.getParameters(request).get("code").getValue())
                    .map(Strings::emptyToNull);
            } catch (Exception ex) { // NOSONAR
                return Optional.empty();
            }
        }

        /** Parses the error from the request parameters (if present) and returns a formated error string */
        private static String getErrorFromRequest(final Request request) {
            final var errorMessage = new StringBuilder();

            // Error parameter
            List<String> errorParamValues;
            try {
                errorParamValues = Request.getParameters(request).getValues("error");
            } catch (Exception ex) {
                errorParamValues = null;
                LOGGER.warn("Could not extract errors from request.", ex);
            }
            if (errorParamValues != null) {
                if (errorParamValues.size() > 1) {
                    errorMessage.append("Errors: ").append(errorParamValues);
                } else if (errorParamValues.size() == 1) {
                    errorMessage.append("Error: ").append(errorParamValues.get(0));
                }
            }

            // Error description parameter
            List<String> errorDescParamValues;
            try {
                errorDescParamValues = Request.getParameters(request).getValues("error_description");
            } catch (Exception ex) {
                errorDescParamValues = null;
                LOGGER.warn("Could not extract error descriptions from request.", ex);
            }
            if (errorDescParamValues != null) {
                if (errorDescParamValues.size() > 1) {
                    errorMessage.append("\nError descriptions: ").append(errorDescParamValues);
                } else if (errorDescParamValues.size() == 1) {
                    errorMessage.append("\nError description: ").append(errorDescParamValues.get(0));
                }
            }

            return errorMessage.toString();
        }
    }


    /** Starts a thread which waits until the future is done and closes the service and stops the callback server */
    private static void startClosingThread(final Future<SalesforceToken> authFuture,
        final OAuth20Service service, final Server callbackServer) {
        new Thread(() -> { // NOSONAR: nope...

            // Wait until the authentication is done
            try {
                authFuture.get();
                /* Wait for a few seconds before stopping the service, otherwise users
                 * might get a 404 due to a race condition between stopping the service
                 * and retrieving the redirect page. Similar issue as (SRV-2482). */
                Thread.sleep(5000);
            } catch (final ExecutionException | InterruptedException | CancellationException ex) { // NOSONAR ignoring
            }

            try {
                service.close();
            } catch (final IOException ex) { // NOSONAR ignoring
            }

            // Check that everything gets closed and stopped correctly
            try {
                callbackServer.stop();
            } catch (Exception ex) {
                LOGGER.warn("Could not stop OAuth callback server" +  ex.getMessage(), ex);
            }
            OAUTH_IN_PROGRESS.set(false);
        }).start();

    }


    /**
     * Class implementing an Salesforce authentication future.
     *
     * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
     */
    private static final class SalesforceAuthenticationFuture extends AbstractFuture<SalesforceToken> {

        void setResult(final SalesforceToken auth) {
            set(auth);
        }

        boolean setFailed(final Throwable throwable) {
            return setException(throwable);
        }
    }

    /**
     * Contains the configuration for a Salesforce client app
     * @param clientId the client app id
     * @param clientSecret the client app secret
     */
    public record ClientApp(String clientId, String clientSecret) { }
}
