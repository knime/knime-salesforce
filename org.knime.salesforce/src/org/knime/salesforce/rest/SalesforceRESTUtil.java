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
 *   Dec 28, 2019 (wiswedel): created
 */
package org.knime.salesforce.rest;

import java.io.StringReader;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonPointer;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.SalesforceAuthenticationUtils;
import org.knime.salesforce.auth.SalesforceAuthenticationUtils.AuthenticationException;
import org.knime.salesforce.rest.gsonbindings.ErrorResponse;
import org.knime.salesforce.rest.gsonbindings.fields.Field;
import org.knime.salesforce.rest.gsonbindings.fields.SObjectDescription;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObject;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObjects;

import com.github.scribejava.apis.SalesforceApi;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Static methods that use the Salesforce
 * <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_what_is_rest_api.htm">REST
 * API</a>.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class SalesforceRESTUtil {

    static final long CONNECTION_TIMEOUT = 30000;

    static final long RECEIVE_TIMEOUT = 60000;

    /**
     * The base path for all API calls. Version 48 was the latest available on production in July '20.
     */
    // get latest version by browsing, e.g. https://knime.my.salesforce.com/services/data/
    public static final String PREFIX_PATH = "/services/data/v48.0/";

    /** SOQL path. */
    public static final String QUERY_PATH = PREFIX_PATH + "query/";

    /** SObjects path (object and field description). */
    public static final String SOBJECTS_PATH = PREFIX_PATH + "sobjects/";

    /** Field description path. */
    public static final String SOBJECT_FIELDS_PATH = SOBJECTS_PATH + "{sobjectname}/describe";

    private SalesforceRESTUtil() {
    }

    /** Performs authentication using username, password and security token (can create a new one in the user's account settings on Salesforce.com).
     * @param user
     * @param password
     * @param securityToken
     * @param isUseSandbox production or test instance?
     * @return The authentication object, not null.
     * @throws SalesforceResponseException If that fails.
     */
    public static SalesforceAuthentication authenticateUsingUserAndPassword(final String user, final String password,
        final String securityToken, final boolean isUseSandbox) throws SalesforceResponseException {
        CheckUtils.checkArgument(StringUtils.isNotEmpty(user), "User must not be empty (or null)");
        CheckUtils.checkArgument(StringUtils.isNotEmpty(password), "Password must not be empty");
        CheckUtils.checkArgumentNotNull(securityToken, "Security token must not be null");
        SalesforceApi api = isUseSandbox ? SalesforceApi.sandbox() : SalesforceApi.instance();
        WebClient client = getClient(UriBuilder.fromUri(api.getAccessTokenEndpoint()).build(), null);
        client.accept(MediaType.APPLICATION_JSON);
        MultivaluedHashMap<String, String> formData = new MultivaluedHashMap<>();
        formData.put("grant_type", Collections.singletonList("password"));
        formData.put("client_id", Collections.singletonList(SalesforceAuthenticationUtils.CLIENT_ID));
        formData.put("client_secret", Collections.singletonList(SalesforceAuthenticationUtils.CLIENT_SECRET));
        formData.put("username", Collections.singletonList(user));
        formData.put("password", Collections.singletonList(password + securityToken));
        try (Response response = client.form(new Form(formData))) {
            if (response.getStatusInfo().getFamily() != Status.Family.SUCCESSFUL) {
                String body = response.readEntity(String.class);
                String errorDescription = null;
                JsonStructure json = readAsJsonStructure(body);
                JsonValue errorDescriptionValue = json.getValue("/error_description");
                errorDescription = errorDescriptionValue != null ? ((JsonString)errorDescriptionValue).getString() : null;
                throw new SalesforceResponseException(
                    String.format("Authentication failed (status %d): \"%s\"%s", response.getStatus(), //
                        response.getStatusInfo().getReasonPhrase(), //
                        errorDescription != null ? (" - error: \"" + errorDescription + "\"") : ""));
            }
            String body = response.readEntity(String.class);
            JsonStructure json = readAsJsonStructure(body);
            JsonValue accessTokenValue = json.getValue("/access_token");
            JsonValue instanceURLValue = json.getValue("/instance_url");
            JsonValue issuedAtValue = json.getValue("/issued_at");
            return new SalesforceAuthentication(//
                ((JsonString)instanceURLValue).getString(), //
                ((JsonString)accessTokenValue).getString(), //
                /*refresh-token*/null, //
                Instant.ofEpochMilli(Long.parseLong(((JsonString)issuedAtValue).getString()))
                    .atZone(ZoneId.systemDefault()), //
                isUseSandbox);
        }
    }

    /** Simple String to JSON conversion.
     * @param body Input string, not null.
     * @return Json Object
     */
    public static JsonStructure readAsJsonStructure(final String body) {
        try (StringReader reader = new StringReader(body); JsonReader jsonReader = Json.createReader(reader)) {
            return jsonReader.read();
        }
    }

    /**
     * Perform a GET request.
     *
     * @param uri
     * @param auth
     * @param refreshTokenIff if true and the auth object contains refresh token, it will attempt to refresh the access
     *            token (and also save it in auth).
     * @return the response
     * @throws SalesforceResponseException
     */
    @SuppressWarnings("resource")
    public static Response doGet(final URI uri, final SalesforceAuthentication auth,
        final boolean refreshTokenIff) throws SalesforceResponseException {
        final WebClient client = getClient(uri, auth);
        client.accept(MediaType.APPLICATION_JSON);
        client.acceptEncoding("deflate");
        Response response = client.get();
        boolean refreshEnabled = refreshTokenIff && auth.getRefreshToken().isPresent();
        if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode() && refreshEnabled) {
            NodeLogger.getLogger(SalesforceRESTUtil.class).debugWithFormat(
                "Received %s (%d) -- attempting to refresh the access token and retry",
                Status.UNAUTHORIZED.name(), Status.UNAUTHORIZED.getStatusCode());
            SalesforceAuthentication newAuth = refreshToken(auth);
            response.close();
            response = doGet(uri, newAuth, false);
        }
        return response;
    }

    /**
     * Check the response of a call to the Salesforce REST API. Reads the body if successful or throws an exception if
     * unsuccessful.
     */
    private static <T> T checkResponse(final Response response, final Class<T> responseType)
        throws SalesforceResponseException {
        final StatusType statusInfo = response.getStatusInfo();
        if (statusInfo.getFamily() != Family.SUCCESSFUL) {
            String message;
            try {
                final ErrorResponse error = new Gson().fromJson(response.readEntity(String.class), ErrorResponse.class);
                message = error.toString();
            } catch (final JsonSyntaxException | ProcessingException e) {
                message = "Error occured during communicating with Salesforce: " + statusInfo.getReasonPhrase()
                    + " (Error Code: " + statusInfo.getStatusCode() + ")";
            }
            throw new SalesforceResponseException(message);
        }
        try {
            return new Gson().fromJson(response.readEntity(String.class), responseType);
        } catch (final JsonSyntaxException e) {
            throw new SalesforceResponseException("Invalid response from Salesforce.", e);
        }
    }

    /** Get a web client that accesses the given url with the given authentication */
    private static WebClient getClient(final URI uri, final SalesforceAuthentication auth) {
        final WebClient client = WebClient.create(uri);

        // Set the timeout
        final HTTPConduit httpConduit = WebClient.getConfig(client).getHttpConduit();
        httpConduit.getClient().setConnectionTimeout(CONNECTION_TIMEOUT);
        httpConduit.getClient().setReceiveTimeout(RECEIVE_TIMEOUT);

        // Set the auth token
        if (auth != null) {
            client.authorization(getAuthenticationHeader(auth));
        }
        return client;
    }

    private static String getAuthenticationHeader(final SalesforceAuthentication auth) {
        return "Bearer " + auth.getAccessToken();
    }

    /** Read objects from Salesforce. Used to populate components in the dialog UI.
     * @param auth ...
     * @return ...
     * @throws SalesforceResponseException
     */
    public static SObject[] getSObjects(final SalesforceAuthentication auth) throws SalesforceResponseException {
        URI uri = auth.uriBuilder().path(SOBJECTS_PATH).build();
        try (Response response = doGet(uri, auth, true)) {
            return checkResponse(response, SObjects.class).getSobjects();
        }
    }

    /** Read objects from Salesforce. Used to populate components in the dialog UI.
     * @param object ...
     * @param auth ...
     * @return ...
     * @throws SalesforceResponseException
     */
    public static Field[] getSObjectFields(final SObject object, final SalesforceAuthentication auth)
        throws SalesforceResponseException {
        URI uri = auth.uriBuilder().path(SOBJECT_FIELDS_PATH).build(object.getName().toString());
        try (Response response = doGet(uri, auth, true)) {
            return checkResponse(response, SObjectDescription.class).getFields();
        }
    }

    /** Try to parse exception/errors from Salesforce.
     * @param response Error response
     * @return The concrete error messages.
     */
    public static Optional<String> readErrorFromResponseBody(final Response response) {
        String errorBody  = response.readEntity(String.class);
        JsonStructure jsonError = readAsJsonStructure(errorBody);
        // the list of pointers will probably grow over time.
        for (String pointerS : Arrays.asList("/0/message")) {
            JsonPointer pointer = Json.createPointer(pointerS);
            if (pointer.containsValue(jsonError)) {
                JsonValue message = pointer.getValue(jsonError);
                if (message instanceof JsonString) {
                    return Optional.ofNullable(((JsonString)message).getString());

                }
            }
        }
        return Optional.empty();
    }

    /** Refresh the access token (if no other thread refreshed it already)
     * @return new auth */
    private static synchronized SalesforceAuthentication refreshToken(final SalesforceAuthentication auth) throws SalesforceResponseException {
        try {
            return SalesforceAuthenticationUtils.refreshToken(auth);
        } catch (final AuthenticationException | InterruptedException e) {
            if (e.getCause() instanceof UnknownHostException) {
                throw new SalesforceResponseException(
                    "Cannot connect to Salesforce. Please make sure to have an active internet connection.", e);
            }
            throw new SalesforceResponseException(
                "The access token is not valid anymore and could not be updated. Please update the authentication.",
                e);
        }
    }


}
