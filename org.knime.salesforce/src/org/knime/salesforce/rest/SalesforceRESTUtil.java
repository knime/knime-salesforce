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

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.FailableFunction;
import org.apache.cxf.jaxrs.client.WebClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.JsonUtil;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.ThreadLocalHTTPAuthenticator.AuthenticationCloseable;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.rest.gsonbindings.ErrorResponse;
import org.knime.salesforce.rest.gsonbindings.fields.Field;
import org.knime.salesforce.rest.gsonbindings.fields.SObjectDescription;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObject;
import org.knime.salesforce.rest.gsonbindings.sobjects.SObjects;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.Response.StatusType;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Static methods that use the Salesforce
 * <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_what_is_rest_api.htm">REST
 * API</a>.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class SalesforceRESTUtil {

    private static final NodeLogger LOG = NodeLogger.getLogger(SalesforceRESTUtil.class);

    /**
     * The base path for all API calls. Version 48 was the latest available on production in July '20.
     */
    // get latest version by browsing, e.g. https://knime.my.salesforce.com/services/data/
    private static final String PREFIX_PATH = "/services/data/v48.0/"; // NOSONAR

    /** SOQL path. */
    public static final String QUERY_PATH = PREFIX_PATH + "query/";

    /** QueryAll path to include archived and deleted records. */
    public static final String QUERY_ALL_PATH = PREFIX_PATH + "queryAll/";

    /** SObjects path (object and field description). */
    private static final String SOBJECTS_PATH = PREFIX_PATH + "sobjects/";

    /** Field description path. */
    private static final String SOBJECT_FIELDS_PATH = SOBJECTS_PATH + "{sobjectname}/describe";

    private SalesforceRESTUtil() {
    }

    /**
     * Simple String to JSON conversion.
     *
     * @param body Input string, not null.
     * @return Json Object
     */
    public static JsonStructure readAsJsonStructure(final String body) {
        try (final var reader = new StringReader(body); //
                final var jsonReader = JsonUtil.getProvider().createReader(reader)) {
            return jsonReader.read();
        }
    }

    private static URI buildUri(final SalesforceAccessTokenCredential credential, final String path,
        final Object... values) {

        return UriBuilder.fromUri(credential.getSalesforceInstanceUrl())//
            .path(path)//
            .build(values);
    }

    /**
     * Perform a GET request.
     *
     * @param <R> result type
     *
     * @param uri
     * @param credential The Salesforce credential to use.
     * @param refreshTokenIff if true and the auth object contains refresh token, it will attempt to refresh the access
     *            token (and also save it in auth).
     * @param callback response transformer callback
     * @param timeouts connect/read timeouts
     * @return the response
     * @throws SalesforceResponseException
     */
    public static <R> R doGet(final URI uri, final SalesforceAccessTokenCredential credential, //
        final boolean refreshTokenIff,
        final FailableFunction<Response, R, SalesforceResponseException> callback, //
        final Timeouts timeouts) throws SalesforceResponseException {

        final WebClient client = getClient(uri, credential, timeouts);
        client.accept(MediaType.APPLICATION_JSON);
        client.acceptEncoding("deflate");

        try (final AuthenticationCloseable c = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
            final var response = client.get()) {

            if (refreshTokenIff && response.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
                LOG.debugWithFormat("Received %s (%d) -- attempting to refresh the access token and retry", //
                    Status.UNAUTHORIZED.name(), //
                    Status.UNAUTHORIZED.getStatusCode());

                tryAccessTokenRefresh(credential); // force a refresh
                return doGet(uri, credential, false, callback, timeouts);
            }
            return callback.apply(response);
        } finally {
            client.close();
        }
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
            } catch (final JsonSyntaxException | ProcessingException e) { // NOSONAR ignoring on purpose
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

    /**
     * Get a web client that accesses the given url with the given authentication
     *
     * @param timeouts connect/read timeout
     * @throws SalesforceResponseException
     */
    private static WebClient getClient(final URI uri, final SalesforceAccessTokenCredential auth,
        final Timeouts timeouts) throws SalesforceResponseException {

        final var client = WebClient.create(uri);

        // Set the timeout
        final var httpConduit = WebClient.getConfig(client).getHttpConduit();
        httpConduit.getClient().setConnectionTimeout(1000L * timeouts.connectionTimeoutS());
        httpConduit.getClient().setReceiveTimeout(1000L * timeouts.readTimeoutS());

        // Set the auth token
        if (auth != null) {
            client.authorization(getAuthenticationHeader(auth));
        }
        return client;
    }

    private static final String REFRESH_FAIL_MSG =
        "The access token is not valid anymore and could not be refreshed. Please update the authentication.";

    private static void tryAccessTokenRefresh(final SalesforceAccessTokenCredential auth)
        throws SalesforceResponseException {

        try {
            auth.getAccessToken(true);
        } catch (IOException e) {
            throw new SalesforceResponseException(REFRESH_FAIL_MSG, e);
        }
    }

    private static String getAuthenticationHeader(final SalesforceAccessTokenCredential auth)
        throws SalesforceResponseException {

        try {
            return String.format("%s %s", auth.getAuthScheme(), auth.getAccessToken());
        } catch (IOException e) {
            throw new SalesforceResponseException(REFRESH_FAIL_MSG, e);
        }
    }

    /**
     * Read objects from Salesforce. Used to populate components in the dialog UI.
     *
     * @param auth ...
     * @param timeouts ...
     * @return ...
     * @throws SalesforceResponseException
     */
    public static SObject[] getSObjects(final SalesforceAccessTokenCredential credential, final Timeouts timeouts)
        throws SalesforceResponseException {

        final var uri = buildUri(credential, SOBJECTS_PATH) ;
        return doGet(uri, credential, true, response -> checkResponse(response, SObjects.class).getSobjects(),
            timeouts);
    }

    /** Read objects from Salesforce. Used to populate components in the dialog UI.
     *
     * @param object ...
     * @param credential ...
     * @param timeouts connect/read timeout
     * @return ...
     * @throws SalesforceResponseException
     */
    public static Field[] getSObjectFields(final SObject object,//
        final SalesforceAccessTokenCredential credential,//
        final Timeouts timeouts) throws SalesforceResponseException {

        final var uri = buildUri(credential, SOBJECT_FIELDS_PATH, object.getName());

        return doGet(uri, credential, true, response -> checkResponse(response, SObjectDescription.class).getFields(),
            timeouts);
    }

    /**
     * Try to parse exception/errors from Salesforce response body. Supports multiple content types:
     * <ul>
     * <li>application/json - Parses JSON error structure</li>
     * <li>text/html - Extracts text from HTML (title or body)</li>
     * <li>other types - Returns raw response body</li>
     * </ul>
     *
     * @param response Error response with Content-Type header set
     * @return The concrete error messages, or the raw response body if parsing fails
     */
    public static Optional<String> readErrorFromResponseBody(final Response response) {
        final String responseBody = response.readEntity(String.class);
        final var mediaType = response.getMediaType();

        // Try JSON if media type matches
        if (mediaType != null && mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return tryParseJsonError(responseBody);
        }

        // Try HTML if media type matches
        if (mediaType != null && mediaType.isCompatible(MediaType.TEXT_HTML_TYPE)) {
            return tryExtractHtmlError(responseBody);
        }

        // For other types or no media type, try JSON as fallback
        if (mediaType == null) {
            LOG.debug("No Content-Type header in error response, attempting JSON parsing");
            return tryParseJsonError(responseBody);
        }
        return Optional.of(responseBody).filter(StringUtils::isNotBlank);
    }

    /**
     * Attempts to parse JSON error response following Salesforce API error format.
     *
     * @param responseBody JSON response body
     * @return Error message if found, empty otherwise
     */
    private static Optional<String> tryParseJsonError(final String responseBody) {
        final JsonStructure jsonError = readAsJsonStructure(responseBody);

        // "0/message" https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/errorcodes.htm
        // "error_description" OAuth2 error responses (https://www.rfc-editor.org/rfc/rfc6749#section-5.2)
        for (String pointerS : Arrays.asList("/0/message", "/error_description")) {
            JsonPointer pointer = JsonUtil.getProvider().createPointer(pointerS);
            if (pointer.containsValue(jsonError)) {
                JsonValue message = pointer.getValue(jsonError);
                if (message instanceof JsonString jsonMessage) {
                    return Optional.ofNullable((jsonMessage).getString());

                }
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to extract error message from HTML response body using JSoup.
     * Tries to extract from title, h1/h2 headers, or body text in that order.
     * Note: Charset handling is done by the HTTP layer when reading the response body.
     * The response is already decoded to a String using the Content-Type charset.
     *
     * @param responseBody HTML response body (already decoded by HTTP layer)
     * @return Error message extracted from HTML, or raw body if extraction fails
     */
    private static Optional<String> tryExtractHtmlError(final String responseBody) {
        try {
            // Note: charset is NOT passed to JSoup because responseBody is already a decoded String.
            // The HTTP layer (response.readEntity) already decoded the bytes using the Content-Type charset.
            final Document doc = Jsoup.parse(responseBody);

            return Optional.ofNullable(doc.title()) //
                .filter(StringUtils::isNotBlank) //
                .or(() -> Optional.ofNullable(doc.selectFirst("h1, h2")) //
                    .map(Element::text) //
                    .filter(StringUtils::isNotBlank)) //
                .or(() -> Optional.ofNullable(doc.body()) //
                    .map(Element::text) //
                    .filter(StringUtils::isNotBlank) //
                    .map(bodyText -> StringUtils.abbreviate(bodyText, 256))) //
                .or(() -> Optional.of(responseBody).filter(StringUtils::isNotBlank));

        } catch (final Exception e) { // NOSONAR - intentionally catching broad exception
            LOG.debug("Failed to extract error message from HTML response", e);
            return Optional.of(responseBody).filter(StringUtils::isNotBlank);
        }
    }
}
