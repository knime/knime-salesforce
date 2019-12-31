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
import java.net.URI;
import java.net.UnknownHostException;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.Response.StatusType;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.knime.core.data.DataCell;
import org.knime.core.data.MissingCell;
import org.knime.core.data.json.JSONCellFactory;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.salesforce.auth.SalesforceAuthentication;
import org.knime.salesforce.auth.SalesforceAuthenticationUtils;
import org.knime.salesforce.auth.SalesforceAuthenticationUtils.AuthenticationException;
import org.knime.salesforce.rest.bindings.ErrorResponse;
import org.knime.salesforce.rest.bindings.fields.Field;
import org.knime.salesforce.rest.bindings.fields.SObjectDescription;
import org.knime.salesforce.rest.bindings.sobjects.SObject;
import org.knime.salesforce.rest.bindings.sobjects.SObjects;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 *
 * @author wiswedel
 */
public final class SalesforceRESTUtil {

    private static final long CONNECTION_TIMEOUT = 30000;

    private static final long RECEIVE_TIMEOUT = 60000;

    private static final String PREFIX_PATH = "/services/data/v47.0/";

    private static final String QUERY_PATH = PREFIX_PATH + "query/";

    private static final String SOBJECTS_PATH = PREFIX_PATH + "sobjects/";

    private static final String SOBJECT_FIELDS_PATH = SOBJECTS_PATH + "{sobjectname}/describe";

    private SalesforceRESTUtil() {
    }

    public static DataCell get(final String soql, final SalesforceAuthentication auth) throws SalesforceResponseException {
        CheckUtils.checkArgument(StringUtils.isNotBlank(soql), "SOQL must not be blank");
        URI uri = auth.uriBuilder().path(QUERY_PATH).queryParam("q", soql).build();
        NodeLogger.getLogger(SalesforceRESTUtil.class).debugWithFormat("Executing SOQL - %s", uri.toString());
//        String s;
//        try {
//            s = URLEncoder.encode(soql, StandardCharsets.UTF_8.name());
//        } catch (UnsupportedEncodingException ex1) {
//            throw new InternalError(ex1.getMessage(), ex1);
//        }
        Response response = getInternal(uri, auth, true);
        if (response.getStatusInfo().getFamily() != Status.Family.SUCCESSFUL) {
            String error =
                    String.format("Status: %d -- %s", response.getStatus(), response.getStatusInfo().getReasonPhrase());
            return new MissingCell(error);
        }
        String body = response.readEntity(String.class);
        try {
            return JSONCellFactory.create(body, true);
        } catch (IOException ex) {
            return new MissingCell("Unable to parse response as JSON:\n" + ex.getMessage());
        }
    }

    public static Response getInternal(final URI uri, final SalesforceAuthentication auth,
        final boolean refreshTokenIff) throws SalesforceResponseException {
        final WebClient client = getClient(uri, auth);
        client.accept(MediaType.APPLICATION_JSON);
//        client.acceptEncoding("deflate");
        Response response = client.get();
        if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode() && refreshTokenIff) {
            NodeLogger.getLogger(SalesforceRESTUtil.class).debugWithFormat(
                "Received %s (%d) -- attempting to refresh the access token and retry",
                Status.UNAUTHORIZED.name(), Status.UNAUTHORIZED.getStatusCode());
            refreshToken(auth);
            response = getInternal(uri, auth, false);
        }
        return response;
    }

    /** Make a GET request */
    private static <T> T get(final URI uri, final Class<T> responseType, final SalesforceAuthentication auth)
        throws SalesforceResponseException {
        final WebClient client = getClient(uri, auth);
        client.accept(MediaType.APPLICATION_JSON);
        final Response response = client.get();
        return checkResponse(response, responseType);
    }

    /** Make a POST request */
    private static <T> T post(final URI uri, final Class<T> responseType, final String body,
        final SalesforceAuthentication auth) throws SalesforceResponseException {
        final WebClient client = getClient(uri, auth);
        client.accept(MediaType.APPLICATION_JSON);
        client.type(MediaType.APPLICATION_JSON);
        final Response response = client.post(body);
        return checkResponse(response, responseType);
    }

    /** Make a DELETE request */
    private static <T> T delete(final URI uri, final Class<T> responseType, final SalesforceAuthentication auth)
        throws SalesforceResponseException {
        final WebClient client = getClient(uri, auth);
        client.accept(MediaType.APPLICATION_JSON);
        final Response response = client.delete();
        return checkResponse(response, responseType);
    }

    /** Make a PUT request */
    private static <T> T put(final URI uri, final Class<T> responseType, final String body,
        final SalesforceAuthentication auth) throws SalesforceResponseException {
        final WebClient client = getClient(uri, auth);
        client.accept(MediaType.APPLICATION_JSON);
        client.type(MediaType.APPLICATION_JSON);
        final Response response = client.put(body);
        return checkResponse(response, responseType);
    }

    /**
     * Check the response of a call to the Power BI REST API. Reads the body if successful or throws an exception if
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
                message = "Error occured during communicating with Power BI: " + statusInfo.getReasonPhrase()
                    + " (Error Code: " + statusInfo.getStatusCode() + ")";
            }
            throw new SalesforceResponseException(message);
        }
        try {
            return new Gson().fromJson(response.readEntity(String.class), responseType);
        } catch (final JsonSyntaxException e) {
            throw new SalesforceResponseException("Invalid response from Power BI.", e);
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
        client.authorization(getAuthenticationHeader(auth));
        return client;
    }

    private static String getAuthenticationHeader(final SalesforceAuthentication auth) {
        return "Bearer " + auth.getAccessToken();
    }

    public static SObject[] getSObjects(final SalesforceAuthentication auth) throws SalesforceResponseException {
        URI uri = auth.uriBuilder().path(SOBJECTS_PATH).build();
        Response response = getInternal(uri, auth, true);
        return checkResponse(response, SObjects.class).getSobjects();
    }

    public static Field[] getSObjectFields(final SObject object, final SalesforceAuthentication auth) throws SalesforceResponseException {
        URI uri = auth.uriBuilder().path(SOBJECT_FIELDS_PATH).build(object.getName().toString());
        Response response = getInternal(uri, auth, true);
        return checkResponse(response, SObjectDescription.class).getFields();
    }

    /** Check if the access token is still valid and refresh it if it is not valid anymore */
    private static void refreshTokenIfNecessary(final SalesforceAuthentication auth) throws SalesforceResponseException {
        if (false) {
            // The token needs to be refreshed
            refreshToken(auth);
        }
    }

    /** Refresh the access token (if no other thread refreshed it already) */
    private static synchronized void refreshToken(final SalesforceAuthentication auth) throws SalesforceResponseException {
        try {
            SalesforceAuthenticationUtils.refreshToken(auth);
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

    /**
     * An exception that is thrown if an error occurs during the communication with the Power BI REST API.
     */
    public static class SalesforceResponseException extends Exception {

        private static final long serialVersionUID = 1L;

        private SalesforceResponseException(final String message, final Throwable cause) {
            super(message, cause);
        }

        private SalesforceResponseException(final String message) {
            super(message);
        }
    }


}
