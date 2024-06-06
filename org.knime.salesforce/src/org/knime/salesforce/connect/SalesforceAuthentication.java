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
 *   Oct 9, 2019 (benjamin): created
 */
package org.knime.salesforce.connect;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.base.ConfigBaseRO;
import org.knime.core.node.config.base.ConfigBaseWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.credentials.base.oauth.api.AccessTokenCredential;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.auth.credential.SalesforceAuthenticationUtil;

import com.github.scribejava.apis.SalesforceApi;
import com.github.scribejava.apis.salesforce.SalesforceToken;

import jakarta.ws.rs.core.UriBuilder;

/**
 * Represents token information to authenticate with Salesforce.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceAuthentication {

    /** Error message shown when auth object is no longer in memory. */
    public static final String AUTH_NOT_CACHE_ERROR = "No authentication information available. "
        + "Perform authentication in connector node.";

    private static final String WEAK_ENCRYPTION_KEY = "u4d?:fnN,c";

    private final String m_accessToken;

    private final ZonedDateTime m_accessTokenCreatedWhen;

    private final String m_refreshToken;

    private final ZonedDateTime m_refreshTokenCreatedWhen;

    private final String m_instanceURLString;

    private final boolean m_isSandboxInstance;


    /**
     * Create a new default {@link SalesforceAuthentication} with the given tokens.
     *
     * @param instanceURLString as per {@link SalesforceToken#getInstanceUrl()}
     * @param accessToken the access token
     * @param refreshToken the refresh token. Can be <code>null</code>.
     * @param tokensCreatedWhen Timestamp when tokens were fetched
     * @param isUseSandboxInstance Whether to use a sandbox instance (as per {@link SalesforceApi#sandbox()}.
     */
    public SalesforceAuthentication(final String instanceURLString, final String accessToken,
        final String refreshToken, final ZonedDateTime tokensCreatedWhen, final boolean isUseSandboxInstance) {
        this(instanceURLString, accessToken, tokensCreatedWhen, refreshToken, tokensCreatedWhen, isUseSandboxInstance);
    }

    private SalesforceAuthentication(final String instanceURLString, final String accessToken,
        final ZonedDateTime accessTokenCreatedWhen, final String refreshToken,
        final ZonedDateTime refreshTokenCreatedWhen, final boolean isUseSandboxInstance) {
        m_instanceURLString = CheckUtils.checkArgumentNotNull(instanceURLString);
        m_accessToken = CheckUtils.checkArgumentNotNull(accessToken);
        m_accessTokenCreatedWhen = CheckUtils.checkArgumentNotNull(accessTokenCreatedWhen);
        m_refreshToken = refreshToken;
        m_refreshTokenCreatedWhen = refreshToken != null ? refreshTokenCreatedWhen : null;
        m_isSandboxInstance = isUseSandboxInstance;
    }

    /**
     * @return non-null access token, potentially invalid.
     */
    public String getAccessToken() {
        return m_accessToken;
    }

    /**
     * @return the timestamp the {@linkplain #getAccessToken() access token} was assigned.
     */
    public ZonedDateTime getAccessTokenCreatedWhen() {
        return m_accessTokenCreatedWhen;
    }

    /**
     * The salesforce instance to use, returned by the service. E.g. https://csr105.salesforce.com/
     * @return the instanceURLString The URL to be used, returned by the service. Not null.
     */
    public String getInstanceURLString() {
        return m_instanceURLString;
    }

    /** @return the refresh token, not null. */
    public Optional<String> getRefreshToken() {
        return Optional.ofNullable(m_refreshToken);
    }

    /**
     * @return the timestamp the {@linkplain #getRefreshToken() refresh token} was assigned.
     */
    public Optional<ZonedDateTime> getRefreshTokenCreatedWhen() {
        return Optional.ofNullable(m_refreshTokenCreatedWhen);
    }

    /**
     * @return the isSandboxInstance
     */
    public boolean isSandboxInstance() {
        return m_isSandboxInstance;
    }

    /**
     * @return empty builder starting with {@link #getInstanceURLString()}
     */
    public UriBuilder uriBuilder() {
        return UriBuilder.fromUri(m_instanceURLString);
    }

    /**
     * @param settings To save to.
     */
    public void save(final ConfigBaseWO settings) {
        settings.addString("instanceURL", m_instanceURLString);
        settings.addPassword("accessTokenCrypt", WEAK_ENCRYPTION_KEY, m_accessToken);
        settings.addString("accessTokenCreatedWhen", toString(m_accessTokenCreatedWhen));
        settings.addPassword("refreshTokenCrypt", WEAK_ENCRYPTION_KEY, m_refreshToken);
        settings.addString("refreshTokenCreatedWhen", toString(m_refreshTokenCreatedWhen));
        settings.addBoolean("isSandboxInstance", m_isSandboxInstance);
    }

    /**
     * @param settings ...
     * @return ...
     * @throws InvalidSettingsException ...
     */
    public static SalesforceAuthentication load(final ConfigBaseRO settings) throws InvalidSettingsException {
        final var instanceUrl = settings.getString("instanceURL");
        final var accessToken = settings.getPassword("accessTokenCrypt", WEAK_ENCRYPTION_KEY);
        final var atWhen = settings.getString("accessTokenCreatedWhen");
        final var accessTokenCreatedWhen = atWhen != null ? fromString(atWhen) : null;
        final var refreshToken = settings.getPassword("refreshTokenCrypt", WEAK_ENCRYPTION_KEY);
        final var rtWhen = settings.getString("refreshTokenCreatedWhen");
        final var refreshTokenCreatedWhen = rtWhen != null ? fromString(rtWhen) : null;
        final var  isSandbox = settings.getBoolean("isSandboxInstance");
        return new SalesforceAuthentication(instanceUrl, accessToken, accessTokenCreatedWhen, refreshToken,
            refreshTokenCreatedWhen, isSandbox);
    }

    private static String toString(final ZonedDateTime zdt) {
        return zdt != null ? zdt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME) : null;
    }

    private static ZonedDateTime fromString(final String str) throws InvalidSettingsException {
        CheckUtils.checkSettingNotNull(str, "ZonedDateTime string must not be null");
        try {
            return ZonedDateTime.parse(str, DateTimeFormatter.ISO_ZONED_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new InvalidSettingsException("Unable to parse \"" + str + "\": " + ex.getMessage(), ex);
        }
    }

    @Override
    public String toString() {
        return String.format("m_accessToken=%s (%s) m_refreshToken=%s (%s), m_instanceURLString=%s", //
            m_accessToken.substring(0, 5) + "...", //
            m_accessTokenCreatedWhen.toString(), //
            m_refreshToken != null ? (m_refreshToken.substring(0, 5) + "...") : "<none>", //
            m_refreshTokenCreatedWhen != null ? m_refreshTokenCreatedWhen.toString() : "--", //
            m_instanceURLString);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()//
                .append(m_accessToken)//
                .append(m_accessTokenCreatedWhen)//
                .append(m_refreshToken)//
                .append(m_refreshTokenCreatedWhen)//
                .append(m_instanceURLString)
                .toHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SalesforceAuthentication other = (SalesforceAuthentication)obj;
        return new EqualsBuilder()//
            .append(m_accessToken, other.m_accessToken)//
            .append(m_accessTokenCreatedWhen, other.m_accessTokenCreatedWhen)//
            .append(m_instanceURLString, other.m_instanceURLString)//
            .append(m_refreshToken, other.m_refreshToken)//
            .append(m_refreshTokenCreatedWhen, other.m_refreshTokenCreatedWhen)//
            .isEquals();
    }

    private Supplier<AccessTokenCredential> createRefresherIfPossible() {
        if (m_refreshToken == null) {
            return null;
        } else {
            return () -> SalesforceAuthenticationUtil.refreshToken(m_refreshToken, m_isSandboxInstance);
        }
    }

    public SalesforceAccessTokenCredential toCredential() {
        final var accessToken = new AccessTokenCredential(//
            m_accessToken,//
            Instant.now().plus(24, ChronoUnit.HOURS), // this is a wild guess, Salesforce does not provide this info
            "Bearer",//
            createRefresherIfPossible());

        return new SalesforceAccessTokenCredential(//
            URI.create(m_instanceURLString),//
            accessToken);
    }
}
