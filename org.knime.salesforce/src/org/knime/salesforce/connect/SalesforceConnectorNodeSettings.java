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
import static org.knime.salesforce.connect.InMemoryAuthenticationStore.getDialogToNodeExchangeInstance;
import static org.knime.salesforce.connect.InMemoryAuthenticationStore.getGlobalInstance;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
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
import org.knime.salesforce.auth.SalesforceAuthentication;

import com.github.scribejava.apis.SalesforceApi;

/**
 * Settings store managing all configurations required for the node.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeSettings {

    /**
     *
     */
    private static final String NODESETTINGS_KEY = "avi-339vd";

    private static final String CFG_KEY_AUTHENTICATION = "authentication";

    private static final String CFG_KEY_FILESYSTEM_LOCATION = "filesystem_location";

    private static final String CFG_KEY_CREDENTIALS_SAVE_LOCATION = "credentials_save_location";

    private static final String CFG_AUTH_TYPE = "auth_type";

    static final String CFG_USERNAME_PASSWORD = "username-password";

    private static final String CFG_PASSWORD_SECURITY_TOKEN = "password_security_token";

    private static final String CFG_SALESFORCE_INSTANCE = "salesforce_instance";

    private static final String CFG_KEY_NODE_INSTANCE_ID = "node_instance_id";

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

    private final UUID m_nodeInstanceID;

    private InstanceType m_salesforceInstanceType = InstanceType.ProductionInstance;

    private CredentialsLocationType m_credentialsSaveLocation = CredentialsLocationType.MEMORY;

    private String m_filesystemLocation;

    private final SettingsModelAuthentication m_usernamePasswortAuthenticationModel;

    private String m_passwordSecurityToken;

    private AuthType m_authType;

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
     * Called when dialog is about to close. This also saves the authentication information to the
     * file system (if configured to do so).
     * @param settings to save to
     * @throws IOException If {@link CredentialsLocationType#FILESYSTEM} and the credentials can't be saved to file.
     * @throws InvalidSettingsException If {@link CredentialsLocationType#FILESYSTEM} and the selected file path can't
     *             be resolved.
     */
    void saveSettingsInDialog(final NodeSettingsWO settings) throws IOException, InvalidSettingsException {
        Optional<SalesforceAuthentication> auth = saveSettingsTo(settings, getDialogToNodeExchangeInstance());
        if (auth.isPresent()) {
            String fsLocation = getFilesystemLocation();
            CheckUtils.checkSettingNotNull(StringUtils.isNotEmpty(fsLocation), "File system location must not be empty");
            File credentialsFile = resolveFilesystemLocation(fsLocation).orElseThrow(IllegalStateException::new);
            saveCredentialsToFile(credentialsFile, auth.get());
        }

    }

    void saveSettingsInModel(final NodeSettingsWO settings) {
        saveSettingsTo(settings, getGlobalInstance());
    }

    private Optional<SalesforceAuthentication> saveSettingsTo(
        final NodeSettingsWO settings, final InMemoryAuthenticationStore authStore) {
        settings.addString(CFG_KEY_NODE_INSTANCE_ID, m_nodeInstanceID.toString());
        settings.addString(CFG_SALESFORCE_INSTANCE, getSalesforceInstanceType().getLocation());
        settings.addString(CFG_AUTH_TYPE, getAuthType().name());
        settings.addString(CFG_KEY_CREDENTIALS_SAVE_LOCATION, getCredentialsSaveLocation().getActionCommand());
        Optional<SalesforceAuthentication> auth = authStore.get(m_nodeInstanceID);
        m_usernamePasswortAuthenticationModel.saveSettingsTo(settings);
        settings.addPassword(CFG_PASSWORD_SECURITY_TOKEN, NODESETTINGS_KEY, m_passwordSecurityToken);
        saveAuthentication(settings, auth.orElse(null));
        return auth;
    }

    static SalesforceConnectorNodeSettings loadInModel(final NodeSettingsRO settings, final boolean validateOnly)
        throws InvalidSettingsException {
        UUID nodeInstanceID;
        try {
            nodeInstanceID = UUID.fromString(CheckUtils.checkSettingNotNull(
                settings.getString(CFG_KEY_NODE_INSTANCE_ID), "Instance ID must not be null"));
        } catch (IllegalArgumentException ex) {
            throw new InvalidSettingsException("Instance ID can't be read as UUID", ex);
        }
        SalesforceConnectorNodeSettings r = new SalesforceConnectorNodeSettings(nodeInstanceID);
        r.m_salesforceInstanceType = InstanceType.fromLocation(settings.getString(CFG_SALESFORCE_INSTANCE));
        r.m_authType = AuthType.loadFrom(settings.getString(CFG_AUTH_TYPE));
        r.setCredentialsSaveLocation(
            CredentialsLocationType.fromActionCommand(settings.getString(CFG_KEY_CREDENTIALS_SAVE_LOCATION)));
        r.m_usernamePasswortAuthenticationModel.loadSettingsFrom(settings);
        r.m_passwordSecurityToken = settings.getPassword(CFG_PASSWORD_SECURITY_TOKEN, NODESETTINGS_KEY);

        // Load the authentication last (in case it fails)
        SalesforceAuthentication auth =
            r.loadAuthentication(settings, getDialogToNodeExchangeInstance());
        if (!validateOnly) {
            getGlobalInstance().put(nodeInstanceID, auth);
        }
        return r;
    }

    static SalesforceConnectorNodeSettings loadInDialog(final NodeSettingsRO settings) {
        UUID nodeInstanceID;
        try {
            nodeInstanceID = UUID.fromString(settings.getString(CFG_KEY_NODE_INSTANCE_ID));
        } catch (IllegalArgumentException | InvalidSettingsException | NullPointerException ex) {
            nodeInstanceID = UUID.randomUUID();
        }
        SalesforceConnectorNodeSettings result = new SalesforceConnectorNodeSettings(nodeInstanceID);
        InstanceType instanceType;
        try {
            instanceType = InstanceType.fromLocation(settings.getString(CFG_SALESFORCE_INSTANCE));
        } catch (InvalidSettingsException ex) {
            instanceType = InstanceType.TestInstance;
        }
        result.m_salesforceInstanceType = instanceType;
        AuthType authType;
        try {
            authType = AuthType.loadFrom(settings.getString(CFG_AUTH_TYPE));
        } catch (InvalidSettingsException e) {
            authType = AuthType.Interactive;
        }
        result.setAuthType(authType);

        result.setFilesystemLocation(settings.getString(CFG_KEY_FILESYSTEM_LOCATION, ""));
        result.setCredentialsSaveLocation(CredentialsLocationType.fromActionCommand(
            settings.getString(CFG_KEY_CREDENTIALS_SAVE_LOCATION, CredentialsLocationType.MEMORY.name())));
        try {
            result.m_usernamePasswortAuthenticationModel.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex1) {
            // ignore, use defaults
        }
        result.m_passwordSecurityToken = settings.getPassword(CFG_PASSWORD_SECURITY_TOKEN, NODESETTINGS_KEY, "");

        try {
            SalesforceAuthentication auth =
                result.loadAuthentication(settings, getGlobalInstance());
            getDialogToNodeExchangeInstance().put(nodeInstanceID, auth);
        } catch (InvalidSettingsException ex) {
        }
        return result;
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
    void clearAuthentication(final CredentialsLocationType locationType) throws InvalidSettingsException {
        switch (locationType) {
            case FILESYSTEM:
                String fsLocation = getFilesystemLocation();
                File credFile = resolveFilesystemLocation(fsLocation).orElse(null);
                if (credFile != null && credFile.isFile()) {
                    // To make sure not to delete something on accident, only delete if the file has the magic header.
                    try {
                        readCredentialsFromFile(credFile);
                    } catch (InvalidSettingsException ex) {
                        throw new InvalidSettingsException("File \"" + credFile + "\" was not created "
                            + "by KNIME Salesforce integration - will not delete it (do it manually)", ex);
                    }
                    FileUtils.deleteQuietly(credFile);
                }
                break;
            case MEMORY:
            case NODE:
                break;
            default:
                throw new NotImplementedException("Case " + locationType + " not yet implemented.");
        }
        getDialogToNodeExchangeInstance().remove(m_nodeInstanceID);
    }

    /**
     * Save the authentication to the selected credentials location.
     *
     * @param settings Setting to save authentication to if {@link CredentialsLocationType#FILESYSTEM}.
     * @param auth Authenticaton object
     */
    private void saveAuthentication(final NodeSettingsWO settings, final SalesforceAuthentication auth) {
        switch (m_credentialsSaveLocation) {
            case MEMORY:
                break;
            case FILESYSTEM:
                settings.addString(CFG_KEY_FILESYSTEM_LOCATION, getFilesystemLocation());
                break;
            case NODE:
                if (auth != null) {
                    auth.save(settings.addNodeSettings(CFG_KEY_AUTHENTICATION));
                }
                break;
            default:
                throw new NotImplementedException("Case " + m_credentialsSaveLocation + " not implemented.");
        }
    }

    /**
     * Load the authentication from the selected credentials location.
     *
     * @param settings Setting to load authentication from if {@link CredentialsLocationType#FILESYSTEM}.
     * @param authStore
     * @return
     * @throws InvalidSettingsException If {@link CredentialsLocationType#FILESYSTEM} and the selected file path can't
     *             be resolved.
     */
    private SalesforceAuthentication loadAuthentication(final NodeSettingsRO settings,
        final InMemoryAuthenticationStore authStore) throws InvalidSettingsException {
        SalesforceAuthentication authentication = null;
        switch (m_credentialsSaveLocation) {
            case MEMORY:
                authentication = authStore.get(m_nodeInstanceID).orElse(null);
                break;
            case FILESYSTEM:
                setFilesystemLocation(settings.getString(CFG_KEY_FILESYSTEM_LOCATION));
                File credentialsFile = resolveFilesystemLocation(getFilesystemLocation()).orElse(null);
                if (credentialsFile != null && credentialsFile.isFile()) {
                    authentication = readCredentialsFromFile(credentialsFile);
                }
                break;
            case NODE:
                if (settings.containsKey(CFG_KEY_AUTHENTICATION)) {
                    authentication = SalesforceAuthentication.load(settings.getNodeSettings(CFG_KEY_AUTHENTICATION));
                }
                break;
            default:
                throw new NotImplementedException("Case " + m_credentialsSaveLocation + " not yet implemented.");
        }
        return authentication;
    }

    /**
     * Attempts to save the specified credentials to the specified file.
     *
     * @param saveLocation The file to save to. Will be created if not yet existing, otherwise overwritten.
     * @param auth The authentication to save.
     * @throws IOException If the file exists and does not conform expected specification. Also see
     *             {@link #checkCredentialFileHeader(File)}. Or file can't be written.
     */
    private static void saveCredentialsToFile(final File saveLocation, final SalesforceAuthentication auth)
        throws IOException {
        NodeSettings settings = new NodeSettings(SALESFORCE_AUTHENTICATION_FILE_HEADER);
        settings.addInt("version", 20200101); // not used yet
        auth.save(settings.addNodeSettings(CFG_KEY_AUTHENTICATION));
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(saveLocation))) {
            settings.saveToXML(out);
        } catch (IOException ioe) {
            throw new IOException("Can't write Salesforce credentials file to \"" + saveLocation.getAbsolutePath()
                + "\":" + ioe.getMessage(), ioe);
        }
    }

    /**
     * Attempts to read credentials from the specified file.
     * @param instanceURL instance URL as read from settings
     * @param loadLocation The file to read from.
     *
     * @return The read credentials.
     * @throws IOException If the file exists and does not conform expected specification. Also see
     *             {@link #checkCredentialFileHeader(File)}. Or file can't be read.
     */
    private static SalesforceAuthentication readCredentialsFromFile(final File loadLocation)
        throws InvalidSettingsException {
        NodeSettingsRO settings;
        try (InputStream in = new BufferedInputStream(new FileInputStream(loadLocation))) {
            settings = NodeSettings.loadFromXML(in);
        } catch (IOException ioe) {
            throw new InvalidSettingsException("Unable to read Salesforce credentials from \""
                + loadLocation.getAbsolutePath() + "\": " + ioe.getMessage(), ioe);
        }
        CheckUtils.checkSetting(Objects.equals(settings.getKey(), SALESFORCE_AUTHENTICATION_FILE_HEADER),
            "Unexpected header in credentials file. Expected \"%s\", got \"%s\"", SALESFORCE_AUTHENTICATION_FILE_HEADER,
            settings.getKey());
        NodeSettingsRO authenticationSettings = settings.getNodeSettings(CFG_KEY_AUTHENTICATION);
        return SalesforceAuthentication.load(authenticationSettings);
    }

    /**
     * Attempts to resolve the given String to File, also resolving the KNIME protocol.
     *
     * @param filesystemLocation The String to resolve.
     * @return The file corresponding to the given String.
     * @throws InvalidSettingsException If the given String can't be resolved.
     */
    private static Optional<File> resolveFilesystemLocation(final String filesystemLocation) throws InvalidSettingsException {
        Path resolvedPath;
        if (StringUtils.isEmpty(filesystemLocation)) {
            return Optional.empty();
        }
        try {
            resolvedPath = FileUtil.resolveToPath(FileUtil.toURL(filesystemLocation));
            if (resolvedPath == null) {
                throw new InvalidSettingsException("Not a valid local file path: '" + filesystemLocation + "'.");
            }
        } catch (IOException | URISyntaxException | InvalidPathException e) {
            throw new InvalidSettingsException("Not a valid local file path: '" + filesystemLocation + "'.", e);
        }
        return Optional.of(new File(resolvedPath.toUri()));
    }

}
