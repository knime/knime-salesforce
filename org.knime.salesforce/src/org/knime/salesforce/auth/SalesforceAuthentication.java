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
package org.knime.salesforce.auth;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;

import com.github.scribejava.apis.salesforce.SalesforceToken;

/**
 * Represents token information to authenticate with Salesforce.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class SalesforceAuthentication {

    /** Error message shown when auth object is no longer in memory. */
    public static final String AUTH_NOT_CACHE_ERROR = "No authentication information available in in-memory cache, "
        + "probably because the authentication node was configured to keep it in memory and the workflow was restored "
        + "from disc (application restart)";

    private final String m_accessToken;

    private final ZonedDateTime m_accessTokenCreatedWhen;

    private final String m_refreshToken;

    private final ZonedDateTime m_refreshTokenCreatedWhen;

    private final String m_instanceURLString;

    /**
     * Create a new default {@link SalesforceAuthentication} with the given tokens.
     *
     * @param instanceURLString as per {@link SalesforceToken#getInstanceUrl()}
     * @param accessToken the access token
     * @param refreshToken the refresh token. Can be <code>null</code>.
     * @param tokensCreatedWhen Timestamp when tokens were fetched
     */
    public SalesforceAuthentication(final String instanceURLString, final String accessToken,
        final String refreshToken, final ZonedDateTime tokensCreatedWhen) {
        this(instanceURLString, accessToken, tokensCreatedWhen, refreshToken, tokensCreatedWhen);
    }

    private SalesforceAuthentication(final String instanceURLString, final String accessToken, final ZonedDateTime accessTokenCreatedWhen,
        final String refreshToken, final ZonedDateTime refreshTokenCreatedWhen) {
        m_instanceURLString = CheckUtils.checkArgumentNotNull(instanceURLString);
        m_accessToken = CheckUtils.checkArgumentNotNull(accessToken);
        m_accessTokenCreatedWhen = CheckUtils.checkArgumentNotNull(accessTokenCreatedWhen);
        m_refreshToken = CheckUtils.checkArgumentNotNull(refreshToken);
        m_refreshTokenCreatedWhen = CheckUtils.checkArgumentNotNull(refreshTokenCreatedWhen);
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
    public String getRefreshToken() {
        return m_refreshToken;
    }

    /**
     * @return the timestamp the {@linkplain #getRefreshToken() refresh token} was assigned.
     */
    public ZonedDateTime getRefreshTokenCreatedWhen() {
        return m_refreshTokenCreatedWhen;
    }

    /**
     * Creates a clone with an updated access token.
     *
     * @param accessToken the new access token
     * @param tokenCreatedWhen when that token was created.
     * @return Clone with updated information.
     */
    public SalesforceAuthentication refreshAccessToken(final String accessToken, final ZonedDateTime tokenCreatedWhen) {
        return new SalesforceAuthentication(m_instanceURLString, accessToken, tokenCreatedWhen, m_refreshToken,
            m_refreshTokenCreatedWhen);
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
    public void save(final NodeSettingsWO settings) {
        settings.addString("instanceURL", m_instanceURLString);
        settings.addString("accessToken", m_accessToken);
        settings.addString("accessTokenCreatedWhen", toString(m_accessTokenCreatedWhen));
        settings.addString("refreshToken", m_refreshToken);
        settings.addString("refreshTokenCreatedWhen", toString(m_refreshTokenCreatedWhen));
    }

    /**
     * @param settings ...
     * @return ...
     * @throws InvalidSettingsException ...
     */
    public static SalesforceAuthentication load(final NodeSettingsRO settings) throws InvalidSettingsException {
        String instanceURLString = settings.getString("instanceURL");
        String accessToken = settings.getString("accessToken");
        ZonedDateTime accessTokenCreatedWhen = fromString(settings.getString("accessTokenCreatedWhen"));
        String refreshToken = settings.getString("refreshToken");
        ZonedDateTime refreshTokenCreatedWhen = fromString(settings.getString("refreshTokenCreatedWhen"));
        return new SalesforceAuthentication(instanceURLString, accessToken, accessTokenCreatedWhen, refreshToken,
            refreshTokenCreatedWhen);
    }

    private static String toString(final ZonedDateTime zdt) {
        return zdt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
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
            m_refreshToken.substring(0, 5) + "...", //
            m_refreshTokenCreatedWhen.toString(), //
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

}
