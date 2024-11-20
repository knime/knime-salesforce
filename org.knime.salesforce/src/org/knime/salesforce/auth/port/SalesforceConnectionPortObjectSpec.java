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
 *   Dec 26, 2019 (wiswedel): created
 */
package org.knime.salesforce.auth.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.ModelContentRO;
import org.knime.core.node.ModelContentWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialCache;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.CredentialRef;
import org.knime.salesforce.auth.credential.SalesforceAccessTokenCredential;
import org.knime.salesforce.rest.Timeouts;

/**
 * Subclass of {@link CredentialPortObjectSpec} which additionally provides HTTP timeouts. Since AP 5.3 this class
 * extends {@link CredentialPortObjectSpec}.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class SalesforceConnectionPortObjectSpec extends CredentialPortObjectSpec {

    private UUID m_restoredLegacyCacheId;

    /** Serializer as required by extension point. */
    public static final class Serializer
    extends AbstractSimplePortObjectSpecSerializer<SalesforceConnectionPortObjectSpec> {
    }

    /** Timeouts, added as part of AP-21579. */
    private Timeouts m_timeouts;

    /**
     * Constructor.
     *
     * @param cacheId The cache UUID of the underlying {@link SalesforceAccessTokenCredential}. May be null if currently unknown.
     * @param timeouts HTTP timeouts to use by downstream nodes.
     */
    public SalesforceConnectionPortObjectSpec(final UUID cacheId, final Timeouts timeouts) {
        super(SalesforceAccessTokenCredential.TYPE, cacheId);
        m_timeouts = CheckUtils.checkArgumentNotNull(timeouts);
    }

    /**
     * API method, do not use.
     */
    public SalesforceConnectionPortObjectSpec() {
    }

    private synchronized void lazyRestoreLegacyCredential() {
        if (m_restoredLegacyCacheId != null) {
            final var restoredLegacyCred =
                LegacySalesforceConnectionLoader.tryRestoreCredentialWithLegacyCacheId(m_restoredLegacyCacheId);
            m_restoredLegacyCacheId = null;

            if (restoredLegacyCred.isPresent()) {
                final var cacheId = CredentialCache.store(restoredLegacyCred.get());
                setCacheId(cacheId);
            }
        }
    }

    @Override
    public <T extends Credential> Optional<T> getCredential(final Class<T> credentialClass) {
        lazyRestoreLegacyCredential();
        return super.getCredential(credentialClass);
    }

    @Override
    public boolean isPresent() {
        lazyRestoreLegacyCredential();
        return super.isPresent();
    }

    @Override
    public CredentialRef toRef() {
        lazyRestoreLegacyCredential();
        return super.toRef();
    }

    @Override
    protected void save(final ModelContentWO model) {
        super.save(model);
        m_timeouts.save(model);
    }

    @Override
    protected void load(final ModelContentRO model) throws InvalidSettingsException {
        m_timeouts = Timeouts.read(model); // added in 5.2.1 (and other), see field comment

        // As of AP 5.3.0 this class extends CredentialPortObjectSpec. It must be able to restore
        // credentials loaded by the loadInternals() method of the deprecated Salesforce Authentication
        // node. The loadInternals() puts a restored credential into the "legacy cache" in
        // LegacySalesforceConnectionLoader and this port object spec here needs to retrieve it from
        // there. Unfortunately loadInternals() is executed AFTER the port object is loaded, so the
        // restored credential needs to be restored lazily (see lazyRestoreLegacyCredential())
        m_restoredLegacyCacheId = LegacySalesforceConnectionLoader.restoreLegacyCacheId(model).orElse(null);
        if (m_restoredLegacyCacheId == null) {
            super.load(model);
        } else {
            setCredentialType(SalesforceAccessTokenCredential.TYPE);
        }
    }

    /**
     * @return the timeouts configured during construction.
     */
    public Timeouts getTimeouts() {
        return m_timeouts;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_timeouts, super.hashCode());
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final SalesforceConnectionPortObjectSpec other = (SalesforceConnectionPortObjectSpec)obj;
        return Objects.equals(m_timeouts, other.m_timeouts) && super.equals(other);
    }
}
