package org.reviewboard.rbjenkins.config;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a global configuration for ReviewBoard servers.
 */
@Extension
public class ReviewBoardGlobalConfiguration extends GlobalConfiguration {
    private final Object serverConfigurationsLock = new Object();
    private List<ReviewBoardServerConfiguration> serverConfigurations =
        new ArrayList<>();

    /**
     * Construct the configuration from prior saved entries.
     */
    public ReviewBoardGlobalConfiguration() {
        load();
    }

    /**
     * Construct the global configuration with the given server configurations.
     * @param serverConfigurations List of Review Board server configurations
     */
    public ReviewBoardGlobalConfiguration(
        final List<ReviewBoardServerConfiguration> serverConfigurations) {
        synchronized (serverConfigurationsLock) {
            this.serverConfigurations = serverConfigurations;
        }
    }

    /**
     * Set the server configurations list then save the entries.
     * @param serverConfigurations List of Review Board server configurations
     */
    public void setServerConfigurations(
        final List<ReviewBoardServerConfiguration> serverConfigurations) {
        synchronized (serverConfigurationsLock) {
            this.serverConfigurations = serverConfigurations;
            save();
        }
    }

    /**
     * Fetch the server configurations.
     * @return Review Board server configurations
     */
    public List<ReviewBoardServerConfiguration> getServerConfigurations() {
        return serverConfigurations;
    }

    /**
     * Fetch the server configuration that matches the given name, returning
     * null if one is not found.
     * @param serverURL Review Board server URL
     * @return server configuration or null
     */
    public ReviewBoardServerConfiguration getServerConfiguration(
        final URL serverURL) {
        synchronized (serverConfigurationsLock) {
            for (ReviewBoardServerConfiguration config : serverConfigurations) {
                try {
                    if (new URI(config.getReviewBoardURL()).
                        equals(serverURL.toURI())) {
                        return config;
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }
}
