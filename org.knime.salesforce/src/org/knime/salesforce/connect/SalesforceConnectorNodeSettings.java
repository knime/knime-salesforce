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
 *   Oct 9, 2019 (benjamin): created
 */
package org.knime.salesforce.connect;

import static org.knime.core.node.util.CheckUtils.checkSettingNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication.AuthenticationType;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.FileUtil;
import org.knime.salesforce.rest.Timeouts;

import com.github.scribejava.apis.SalesforceApi;

/**
 * Settings store managing all configurations required for the node.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeSettings {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private static final String NODESETTINGS_KEY = "avi-339vd";

    private static final String CFG_SALESFORCE_INSTANCE = "salesforce_instance";
    private static final String CFG_AUTH_TYPE = "auth_type";
    private static final String CFG_KEY_NODE_INSTANCE_ID = "node_instance_id";
    private static final String CFG_KEY_CREDENTIALS_SAVE_LOCATION = "credentials_save_location";
    private static final String CFG_KEY_FILESYSTEM_LOCATION = "filesystem_location";
    static final String CFG_USERNAME_PASSWORD = "username-password";
    private static final String CFG_PASSWORD_SECURITY_TOKEN = "password_security_token";
    private static final String CFG_KEY_AUTHENTICATION = "authentication";

    private static final String SALESFORCE_AUTHENTICATION_FILE_HEADER = "KNIME Salesforce Authentication";

    /** Use 'production' instance or a test instance (as per {@link SalesforceApi#sandbox()}. */
    public enum InstanceType {

        /** Corresponds to {@link SalesforceApi#instance()}. */
        ProductionInstance("login.salesforce.com"),
        /** Corresponds to {@link SalesforceApi#sandbox()}. */
        TestInstance("test.salesforce.com");

        /** The copied static private member in {@link SalesforceApi}. */
        private final String m_location;

        private InstanceType(final String location) {
            m_location = location;
        }

        /** @return the location */
        public String getLocation() {
            return m_location;
        }

        static InstanceType fromLocation(final String location) throws InvalidSettingsException {
            checkSettingNotNull(location, "Location must not be null");
            return Arrays.stream(values()).filter(it -> it.m_location.equals(location)).findFirst()
                .orElseThrow(() -> new InvalidSettingsException("Unknown instance type " + location));
        }
    }

    enum AuthType {
        Interactive,
        UsernamePassword;

        static AuthType loadFrom(final String value) throws InvalidSettingsException {
            checkSettingNotNull(value, "Value for auth type must not be null");
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new InvalidSettingsException("Invalid value for auth type: " + value, e);
            }

        }

    }

    private UUID m_nodeInstanceID;

    private InstanceType m_salesforceInstanceType = InstanceType.ProductionInstance;

    private AuthType m_authType = AuthType.Interactive;

    private CredentialsLocationType m_credentialsSaveLocation = CredentialsLocationType.MEMORY;

    private String m_filesystemLocation;

    private final SettingsModelAuthentication m_usernamePasswortAuthenticationModel;

    private String m_passwordSecurityToken;

    private Timeouts m_timeouts = new Timeouts(DEFAULT_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);

    /**
     * This field holds the results of SalesforceAuthentication.save(...), when the user
     * has chosen "Node settings" as the location where to save the access token.
     */
    private NodeSettings m_savedAuthenticationSettings;

    SalesforceConnectorNodeSettings(final UUID nodeInstanceID) {
        m_nodeInstanceID = nodeInstanceID;
        m_usernamePasswortAuthenticationModel =
            new SettingsModelAuthentication(CFG_USERNAME_PASSWORD, AuthenticationType.USER_PWD);
    }

    /**
     * @return the nodeInstanceID
     */
    UUID getNodeInstanceID() {
        return m_nodeInstanceID;
    }

    /**
     * @return the filesystemLocation
     */
    String getFilesystemLocation() {
        return m_filesystemLocation;
    }

    /**
     * @param filesystemLocation the filesystemLocation to set
     */
    void setFilesystemLocation(final String filesystemLocation) {
        m_filesystemLocation = filesystemLocation;
    }

    /**
     * @return the credentialsSaveLocation
     */
    CredentialsLocationType getCredentialsSaveLocation() {
        return m_credentialsSaveLocation;
    }

    /**
     * @param credentialsSaveLocation the credentialsSaveLocation to set
     */
    void setCredentialsSaveLocation(final CredentialsLocationType credentialsSaveLocation) {
        m_credentialsSaveLocation = credentialsSaveLocation;
    }

    /**
     * @return the instanceType
     */
    InstanceType getSalesforceInstanceType() {
        return m_salesforceInstanceType;
    }

    /**
     * @param instanceType the instanceType to set
     */
    void setSalesforceInstanceType(final InstanceType instanceType) {
        m_salesforceInstanceType = Objects.requireNonNull(instanceType);
    }

    /**
     * @param authType the authType to set
     */
    void setAuthType(final AuthType authType) {
        m_authType = authType;
    }

    /**
     * @return the authType
     */
    AuthType getAuthType() {
        return m_authType;
    }

    /**
     * @return the passwordSecurityToken
     */
    String getPasswordSecurityToken() {
        return m_passwordSecurityToken;
    }

    /**
     * @param passwordSecurityToken the passwordSecurityToken to set
     */
    void setPasswordSecurityToken(final String passwordSecurityToken) {
        m_passwordSecurityToken = passwordSecurityToken;
    }

    /**
     * @return the usernamePasswortAuthenticationModel
     */
    public SettingsModelAuthentication getUsernamePasswortAuthenticationModel() {
        return m_usernamePasswortAuthenticationModel;
    }

    /**
     * @return the timeouts
     */
    public Timeouts getTimeouts() {
        return m_timeouts;
    }

    /**
     * @param timeouts the timeouts to set
     */
    public void setTimeouts(final Timeouts timeouts) {
        m_timeouts = CheckUtils.checkArgumentNotNull(timeouts);
    }

    /**
     * Called when dialog is about to close. This also saves the authentication information to the
     * file system (if configured to do so).
     * @param settings to save to
     * @throws InvalidSettingsException If {@link CredentialsLocationType#FILESYSTEM} and the selected file path can't
     *             be resolved.
     */
    void saveSettingsInDialog(final NodeSettingsWO settings) throws InvalidSettingsException {
        if (getCredentialsSaveLocation() == CredentialsLocationType.FILESYSTEM) {
            CheckUtils.checkSetting(StringUtils.isNotEmpty(getFilesystemLocation()),
                "File system location must not be empty");
        }
        saveSettingsTo(settings);
    }

    void saveSettingsInModel(final NodeSettingsWO settings) {
        saveSettingsTo(settings);
    }

    private void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addString(CFG_KEY_NODE_INSTANCE_ID, m_nodeInstanceID.toString());
        settings.addString(CFG_SALESFORCE_INSTANCE, getSalesforceInstanceType().getLocation());
        settings.addString(CFG_AUTH_TYPE, getAuthType().name());
        settings.addString(CFG_KEY_CREDENTIALS_SAVE_LOCATION, getCredentialsSaveLocation().getActionCommand());
        settings.addString(CFG_KEY_FILESYSTEM_LOCATION, m_filesystemLocation);
        m_usernamePasswortAuthenticationModel.saveSettingsTo(settings);
        settings.addPassword(CFG_PASSWORD_SECURITY_TOKEN, NODESETTINGS_KEY, m_passwordSecurityToken);
        m_timeouts.save(settings);
        if (m_savedAuthenticationSettings != null) {
            settings.addNodeSettings(m_savedAuthenticationSettings);
        }
    }

    static SalesforceConnectorNodeSettings loadInModel(final NodeSettingsRO settings) throws InvalidSettingsException {

        UUID nodeInstanceID;
        try {
            nodeInstanceID = UUID.fromString(CheckUtils.checkSettingNotNull(
                settings.getString(CFG_KEY_NODE_INSTANCE_ID), "Instance ID must not be null"));
        } catch (IllegalArgumentException ex) {
            throw new InvalidSettingsException("Instance ID can't be read as UUID", ex);
        }

        final var toReturn = new SalesforceConnectorNodeSettings(nodeInstanceID);
        toReturn.m_salesforceInstanceType = InstanceType.fromLocation(settings.getString(CFG_SALESFORCE_INSTANCE));
        toReturn.m_authType = AuthType.loadFrom(settings.getString(CFG_AUTH_TYPE));
        toReturn.setCredentialsSaveLocation(
            CredentialsLocationType.fromActionCommand(settings.getString(CFG_KEY_CREDENTIALS_SAVE_LOCATION)));
        toReturn.m_filesystemLocation = settings.getString(CFG_KEY_FILESYSTEM_LOCATION, "");
        toReturn.m_usernamePasswortAuthenticationModel.loadSettingsFrom(settings);
        toReturn.m_passwordSecurityToken = settings.getPassword(CFG_PASSWORD_SECURITY_TOKEN, NODESETTINGS_KEY);
        toReturn.m_timeouts = Timeouts.read(settings); // non-fail (added in 5.2.1 and other bug fix releases, AP-

        if (settings.containsKey(CFG_KEY_AUTHENTICATION)) {
            toReturn.m_savedAuthenticationSettings = new NodeSettings(CFG_KEY_AUTHENTICATION);
            settings.getNodeSettings(CFG_KEY_AUTHENTICATION).copyTo(toReturn.m_savedAuthenticationSettings);
        }

        return toReturn;
    }

    void loadInDialog(final NodeSettingsRO settings) {
        try {
            m_nodeInstanceID = UUID.fromString(settings.getString(CFG_KEY_NODE_INSTANCE_ID));
        } catch (IllegalArgumentException | InvalidSettingsException | NullPointerException ex) {
            m_nodeInstanceID = UUID.randomUUID();
        }

        try {
            m_salesforceInstanceType = InstanceType.fromLocation(settings.getString(CFG_SALESFORCE_INSTANCE));
        } catch (InvalidSettingsException ex) {
            m_salesforceInstanceType = InstanceType.ProductionInstance;
        }

        try {
            m_authType = AuthType.loadFrom(settings.getString(CFG_AUTH_TYPE));
        } catch (InvalidSettingsException ex) {
            m_authType =  AuthType.Interactive;
        }

        m_credentialsSaveLocation = CredentialsLocationType.fromActionCommand(
            settings.getString(CFG_KEY_CREDENTIALS_SAVE_LOCATION, CredentialsLocationType.MEMORY.name()));
        m_filesystemLocation = settings.getString(CFG_KEY_FILESYSTEM_LOCATION, "");

        try {
            m_usernamePasswortAuthenticationModel.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex1) {
            // ignore, use defaults
        }
        try {
            m_passwordSecurityToken = settings.getPassword(CFG_PASSWORD_SECURITY_TOKEN, NODESETTINGS_KEY, "");
        } catch (RuntimeException e) { // NOSONAR
            // might occur when loading workflows saved with a future version of KNIME (different cipher; 4.4 -> 4.5)
        }
        m_timeouts = Timeouts.read(settings); // added in 5.2.1 (and other)
    }

    /**
     * Clear the stored credentials of specified type. Authentication will always be set to null after this call.
     *
     * @param locationType The type of credentials to clear.
     * @throws IOException If {@link CredentialsLocationType#FILESYSTEM} and the file does not conform expected
     *             specification. Also see {@link #checkCredentialFileHeader(File)}.
     * @throws InvalidSettingsException If {@link CredentialsLocationType#FILESYSTEM} and the selected file path can't
     *             be resolved.
     */
    void clearAuthentication(final CredentialsLocationType locationType) throws IOException {
        switch (locationType) {
            case FILESYSTEM:
                deleteFileSystemLocation();
                break;
            case MEMORY:
                InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().remove(m_nodeInstanceID);
                break;
            case NODE:
                m_savedAuthenticationSettings = null;
                break;
            default:
                throw new NotImplementedException("Case " + locationType + " not yet implemented.");
        }
    }

    private void deleteFileSystemLocation() throws IOException {
        if (StringUtils.isBlank(m_filesystemLocation)) {
            return;
        }

        final var localFile = resolveToLocalPath(m_filesystemLocation);
        if (Files.exists(localFile)) {
            try {
                loadAuthenticationFromFile(localFile);
            } catch (IOException ex) {
                throw new IOException("File \"" + localFile + "\" was not created "
                    + "by KNIME Salesforce integration - will not delete it (do it manually)", ex);
            }
            Files.delete(localFile);
        }
    }

    /**
     * Save the authentication to the selected credentials location.
     *
     * @param settings Setting to save authentication to if {@link CredentialsLocationType#FILESYSTEM}.
     * @param auth Authenticaton object
     */
    void saveAuthentication(final SalesforceAuthentication auth) throws IOException {
        switch (m_credentialsSaveLocation) {
            case MEMORY:
                InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().put(m_nodeInstanceID, auth);
                break;
            case FILESYSTEM:
                saveCredentialsToFile(resolveToLocalPath(m_filesystemLocation), auth);
                break;
            case NODE:
                m_savedAuthenticationSettings = new NodeSettings(CFG_KEY_AUTHENTICATION);
                auth.save(m_savedAuthenticationSettings);
                break;
            default:
                throw new NotImplementedException("Case " + m_credentialsSaveLocation + " not implemented.");
        }
    }

    /**
     * Attempts to save the specified credentials to the specified file.
     *
     * @param saveLocation The file to save to. Will be created if not yet existing, otherwise overwritten.
     * @param auth The authentication to save.
     * @throws IOException If the file exists and does not conform expected specification. Also see
     *             {@link #checkCredentialFileHeader(File)}. Or file can't be written.
     */
    private static void saveCredentialsToFile(final Path localFile, final SalesforceAuthentication auth)
        throws IOException {

        final var settings = new NodeSettings(SALESFORCE_AUTHENTICATION_FILE_HEADER);
        settings.addInt("version", 20200101); // not used yet
        auth.save(settings.addNodeSettings(CFG_KEY_AUTHENTICATION));

        try (final var out = Files.newOutputStream(localFile)) {
            settings.saveToXML(out);
        } catch (IOException ioe) {
            throw new IOException("Can't write Salesforce credentials file to \""
                + localFile.toAbsolutePath().toString() + "\":" + ioe.getMessage(), ioe);
        }
    }


    /**
     * Load the authentication from the selected credentials location.
     *
     * @return an optional with a the credentials, if they could be successfully loaded, an empty optional if none have
     *         been saved beforehand.
     * @throws IOException if loading the credentials failed for some reason (other than non-existence).
     */
    Optional<SalesforceAuthentication> loadAuthentication() throws IOException {

        final var toReturn = switch (m_credentialsSaveLocation) {
            case MEMORY -> InMemoryAuthenticationStore.getDialogToNodeExchangeInstance().get(m_nodeInstanceID)
                .orElse(null);
            case FILESYSTEM -> loadAuthenticationFromFile(resolveToLocalPath(m_filesystemLocation));
            case NODE -> loadAuthenticationFromNodeSettings();
            default -> throw new NotImplementedException("Case " + m_credentialsSaveLocation + " not yet implemented.");
        };

        return Optional.ofNullable(toReturn);
    }

    private SalesforceAuthentication loadAuthenticationFromNodeSettings() throws IOException {
        if (m_savedAuthenticationSettings == null || m_savedAuthenticationSettings.getChildCount() == 0) {
            return null;
        }

        try {
            return SalesforceAuthentication.load(m_savedAuthenticationSettings);
        } catch (InvalidSettingsException ex) {
            throw new IOException("Unable to read Salesforce credentials from node settings", ex);
        }
    }

    /**
     * Attempts to read credentials from the specified file.
     * @param instanceURL instance URL as read from settings
     * @param loadLocation The file to read from.
     *
     * @return The read credentials, or null if the file does not exist.
     * @throws IOException If the file exists and does not conform expected specification. Also see
     *             {@link #checkCredentialFileHeader(File)}. Or file can't be read.
     */
    private static SalesforceAuthentication loadAuthenticationFromFile(final Path localFile) throws IOException {
        if (!Files.exists(localFile)) {
            return null;
        }

        final var errorPrefix =
            String.format("Unable to read Salesforce credentials from \"%s\": ", localFile.toAbsolutePath().toString());

        NodeSettingsRO settings;
        try (final var in = Files.newInputStream(localFile)) {
            settings = NodeSettings.loadFromXML(in);
        } catch (IOException ioe) {
            throw new IOException(errorPrefix + ioe.getMessage(), ioe);
        }

        if (!Objects.equals(settings.getKey(), SALESFORCE_AUTHENTICATION_FILE_HEADER)
            || !settings.containsKey(CFG_KEY_AUTHENTICATION)) {
            throw new IOException(errorPrefix + "Unexpected contents");
        }

        try {
            return SalesforceAuthentication.load(settings.getNodeSettings(CFG_KEY_AUTHENTICATION));
        } catch (InvalidSettingsException ex) {
            throw new IOException(errorPrefix + "Unexpected contents: " + ex.getMessage(), ex);
        }
    }

    /**
     * Attempts to resolve the given String to a local {@link Path}, also resolving the KNIME protocol.
     *
     * @param location The String to resolve.
     * @return the resolved local path (which does not mean that the file exists!)
     * @throws IOException If the given string is invalid or does not resolve to a local path
     */
    private static Path resolveToLocalPath(final String location) throws IOException {
        if (StringUtils.isEmpty(location)) {
            throw new IOException("File system location must not be empty");
        }
        try {
            final var resolvedPath = FileUtil.resolveToPath(FileUtil.toURL(location));
            if (resolvedPath == null) {
                throw new IOException(String.format("File system location is not local: %s", location));
            } else {
                return resolvedPath;
            }
        } catch (URISyntaxException | InvalidPathException e) {
            throw new IOException("Not a valid local file path: '" + location + "'.", e);
        }
    }
}
