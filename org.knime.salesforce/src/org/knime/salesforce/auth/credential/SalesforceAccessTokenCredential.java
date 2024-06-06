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
 *   Jun 4, 2024 (bjoern): created
 */
package org.knime.salesforce.auth.credential;

import static org.knime.credentials.base.CredentialPortViewUtil.obfuscate;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.knime.core.node.util.CheckUtils;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialPortViewData;
import org.knime.credentials.base.CredentialPortViewData.Section;
import org.knime.credentials.base.CredentialType;
import org.knime.credentials.base.CredentialTypeRegistry;
import org.knime.credentials.base.NoOpCredentialSerializer;
import org.knime.credentials.base.oauth.api.AccessTokenAccessor;
import org.knime.credentials.base.oauth.api.HttpAuthorizationHeaderCredentialValue;

/**
 * {@link Credential} that provides a Salesforce access token (Bearer) and Salesforce instance URL.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 * @since 5.3.0
 */
public class SalesforceAccessTokenCredential
    implements Credential, AccessTokenAccessor, HttpAuthorizationHeaderCredentialValue {

    /**
     * The serializer class
     */
    public static class Serializer extends NoOpCredentialSerializer<SalesforceAccessTokenCredential> {
    }

    /**
     * Credential type.
     */
    public static final CredentialType TYPE =
        CredentialTypeRegistry.getCredentialType("knime.salesforceOAuth2Credential");

    private URI m_salesforceInstanceUrl;

    private AccessTokenAccessor m_wrappedAccessToken;

    /**
     * Constructor.
     *
     * @param salesforceInstanceUrl the URL of the Salesforce instance to use the access token with.
     * @param accessToken The access token to use for authentication.
     */
    public SalesforceAccessTokenCredential(final URI salesforceInstanceUrl, final AccessTokenAccessor accessToken) {
        m_salesforceInstanceUrl = CheckUtils.checkArgumentNotNull(salesforceInstanceUrl);
        m_wrappedAccessToken = CheckUtils.checkArgumentNotNull(accessToken);
    }

    /**
     * Empty constructuro for ser(de).
     */
    public SalesforceAccessTokenCredential() {
    }

    /**
     * @return the URL of the Salesforce instance to use the access token with.
     */
    public URI getSalesforceInstanceUrl() {
        return m_salesforceInstanceUrl;
    }

    @Override
    public String getAccessToken() throws IOException {
        return m_wrappedAccessToken.getAccessToken();
    }

    @Override
    public String getAccessToken(final boolean forceRefresh) throws IOException {
        return m_wrappedAccessToken.getAccessToken(forceRefresh);
    }

    @Override
    public String getTokenType() {
        return m_wrappedAccessToken.getTokenType();
    }

    @Override
    public String getAuthScheme() {
        return getTokenType();
    }

    @Override
    public String getAuthParameters() throws IOException {
        return getAccessToken();
    }

    @Override
    public Optional<Instant> getExpiresAfter() {
        return Optional.empty();
    }

    @Override
    public Set<String> getScopes() {
        return Collections.emptySet();
    }

    @Override
    public CredentialType getType() {
        return TYPE;
    }

    @Override
    public CredentialPortViewData describe() {
        return new CredentialPortViewData(List.of(new Section("Databricks Credentials", new String[][]{//
            {"Property", "Value"}, //
            {"Salesforce instance URL", m_salesforceInstanceUrl.toString()}, //
            {"Token", obfuscate("xxxxxxxxxxxxxxxxxxxxxxxx")}, // okay, this is just for show really...
            {"Token type", getTokenType()} //
        })));
    }
}
