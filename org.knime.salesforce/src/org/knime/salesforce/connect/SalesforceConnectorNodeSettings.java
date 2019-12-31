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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.FileUtil;
import org.knime.salesforce.auth.SalesforceAuthentication;

import com.github.scribejava.apis.SalesforceApi;

/**
 * Settings store managing all configurations required for the node.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 * @author David Kolb, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class SalesforceConnectorNodeSettings {

    private static final String CFG_KEY_AUTHENTICATION = "authentication";

    private static final String CFG_KEY_FILESYSTEM_LOCATION = "filesystem_location";

    private static final String CFG_KEY_CREDENTIALS_SAVE_LOCATION = "credentials_save_location";

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

    private final UUID m_nodeInstanceID;

    private InstanceType m_salesforceInstanceType = InstanceType.ProductionInstance;

    private CredentialsLocationType m_credentialsSaveLocation = CredentialsLocationType.MEMORY;

    private String m_filesystemLocation;

    private SalesforceAuthentication m_authentication;

    SalesforceConnectorNodeSettings(final UUID nodeInstanceID) {
        m_nodeInstanceID = nodeInstanceID;
    }

    /**
     * @return the nodeInstanceID
     */
    UUID getNodeInstanceID() {
        return m_nodeInstanceID;
    }

    /**
     * @return the authentication
     */
    Optional<SalesforceAuthentication> getAuthentication() {
        return Optional.ofNullable(m_authentication);
    }

    /**
     * @param authentication the authentication to set
     */
    void setAuthentication(final SalesforceAuthentication authentication) {
        m_authentication = authentication;
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

    void saveSettingsTo(final NodeSettingsWO settings) throws IOException, InvalidSettingsException {
        settings.addString(CFG_KEY_NODE_INSTANCE_ID, m_nodeInstanceID.toString());
        settings.addString(CFG_KEY_FILESYSTEM_LOCATION, getFilesystemLocation());
        settings.addString(CFG_KEY_CREDENTIALS_SAVE_LOCATION, getCredentialsSaveLocation().getActionCommand());
        settings.addString(CFG_SALESFORCE_INSTANCE, getSalesforceInstanceType().getLocation());
        saveAuthentication(settings);
    }

    static void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        CredentialsLocationType locationType = CredentialsLocationType.fromActionCommand(
            settings.getString(CFG_KEY_CREDENTIALS_SAVE_LOCATION));
        if (locationType == CredentialsLocationType.FILESYSTEM) {
            File file = resolveFilesystemLocation(settings.getString(CFG_KEY_FILESYSTEM_LOCATION));
            if (!file.isFile() || !file.canRead()) {
                throw new InvalidSettingsException("Selected credentials storage location (\"" + file.getAbsolutePath()
                    + "\") must be a readable file.");
            }
        }
    }

    static SalesforceConnectorNodeSettings loadInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException, IOException {
        UUID nodeInstanceID;
        try {
            nodeInstanceID = UUID.fromString(CheckUtils.checkSettingNotNull(
                settings.getString(CFG_KEY_NODE_INSTANCE_ID), "Instance ID must not be null"));
        } catch (IllegalArgumentException ex) {
            throw new InvalidSettingsException("Instance ID can't be read as UUID", ex);
        }
        SalesforceConnectorNodeSettings result = new SalesforceConnectorNodeSettings(nodeInstanceID);
        result.m_salesforceInstanceType = InstanceType.fromLocation(settings.getString(CFG_SALESFORCE_INSTANCE));
        result.setFilesystemLocation(settings.getString(CFG_KEY_FILESYSTEM_LOCATION));
        result.setCredentialsSaveLocation(
            CredentialsLocationType.fromActionCommand(settings.getString(CFG_KEY_CREDENTIALS_SAVE_LOCATION)));

        // Load the authentication last (in case it fails)
        result.loadAuthentication(settings);
        return result;
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
        result.setFilesystemLocation(settings.getString(CFG_KEY_FILESYSTEM_LOCATION, ""));
        result.setCredentialsSaveLocation(CredentialsLocationType.fromActionCommand(
            settings.getString(CFG_KEY_CREDENTIALS_SAVE_LOCATION, CredentialsLocationType.MEMORY.name())));

        try {
            result.loadAuthentication(settings);
        } catch (IOException | InvalidSettingsException ex) {
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
                File credFile;
                if (StringUtils.isNotEmpty(fsLocation) && (credFile = resolveFilesystemLocation(fsLocation)).exists()) {
                    // To make sure not to delete something on accident, only delete if the file has the magic header.
                    try {
                        readCredentialsFromFile(credFile);
                    } catch (IOException |InvalidSettingsException ex) {
                        throw new InvalidSettingsException("File \"" + credFile + "\" was not created "
                            + "by KNIME Salesforce integration - will not delete it (do it manually)");
                    }
                    credFile.delete();
                }
                break;
            case MEMORY:
            case NODE:
                break;
            default:
                throw new NotImplementedException("Case " + locationType + " not yet implemented.");
        }
        setAuthentication(null);
    }

    /**
     * Save the authentication to the selected credentials location.
     *
     * @param settings Setting to save authentication to if {@link CredentialsLocationType#FILESYSTEM}.
     * @throws IOException If {@link CredentialsLocationType#FILESYSTEM} and the credentials can't be saved to file.
     * @throws InvalidSettingsException If {@link CredentialsLocationType#FILESYSTEM} and the selected file path can't
     *             be resolved.
     */
    private void saveAuthentication(final NodeSettingsWO settings) throws IOException, InvalidSettingsException {
        if (m_authentication == null) {
            return;
        }
        switch (m_credentialsSaveLocation) {
            case MEMORY:
                break;
            case FILESYSTEM:
                File credentialsFile = resolveFilesystemLocation(getFilesystemLocation());
                saveCredentialsToFile(credentialsFile, m_authentication);
                break;
            case NODE:
                final NodeSettingsWO authConfig = settings.addNodeSettings(CFG_KEY_AUTHENTICATION);
                m_authentication.save(authConfig);
                break;
            default:
                throw new NotImplementedException("Case " + m_credentialsSaveLocation + " not implemented.");
        }

    }

    /**
     * Load the authentication from the selected credentials location.
     *
     * @param settings Setting to load authentication from if {@link CredentialsLocationType#FILESYSTEM}.
     * @throws IOException If {@link CredentialsLocationType#FILESYSTEM} and the credentials can't be loaded from file.
     * @throws InvalidSettingsException If {@link CredentialsLocationType#FILESYSTEM} and the selected file path can't
     *             be resolved.
     */
    private void loadAuthentication(final NodeSettingsRO settings) throws IOException, InvalidSettingsException {
        SalesforceAuthentication authentication = null;
        switch (m_credentialsSaveLocation) {
            case MEMORY:
                authentication = InMemoryAuthenticationStore.getGlobalInstance().get(m_nodeInstanceID).orElse(null);
                break;
            case FILESYSTEM:
                File credentialsFile = resolveFilesystemLocation(getFilesystemLocation());
                if (credentialsFile.exists()) {
                    authentication = readCredentialsFromFile(credentialsFile);
                } else {
                    throw new IOException("Could not load credentials from selected file: \""
                            + credentialsFile.getAbsoluteFile() + "\". File does not exist.");
                }
                break;
            case NODE:
                if (!settings.containsKey(CFG_KEY_AUTHENTICATION)) {
                    break;
                }
                final NodeSettingsRO authSettings = settings.getNodeSettings(CFG_KEY_AUTHENTICATION);
                authentication = SalesforceAuthentication.load(authSettings);
                break;
            default:
                throw new NotImplementedException("Case " + m_credentialsSaveLocation + " not yet implemented.");
        }

        setAuthentication(authentication);
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
        throws IOException, InvalidSettingsException {
        NodeSettingsRO settings;
        try (InputStream in = new BufferedInputStream(new FileInputStream(loadLocation))) {
            settings = NodeSettings.loadFromXML(in);
        } catch (IOException ioe) {
            throw new IOException("Unable to read Salesforce credentials from \"" + loadLocation.getAbsolutePath()
                + "\": " + ioe.getMessage(), ioe);
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
    private static File resolveFilesystemLocation(final String filesystemLocation) throws InvalidSettingsException {
        Path resolvedPath;
        if (filesystemLocation.isEmpty()) {
            throw new InvalidSettingsException("Local file path must not be empty.");
        }
        try {
            resolvedPath = FileUtil.resolveToPath(FileUtil.toURL(filesystemLocation));
            if (resolvedPath == null) {
                throw new InvalidSettingsException("Not a valid local file path: '" + filesystemLocation + "'.");
            }
        } catch (IOException | URISyntaxException | InvalidPathException e) {
            throw new InvalidSettingsException("Not a valid local file path: '" + filesystemLocation + "'.", e);
        }
        return new File(resolvedPath.toUri());
    }

}
