package org.reviewboard.rbjenkins.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.*;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.reviewboard.rbjenkins.Messages;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

/**
 * Stores configuration details for a Review Board server.
 */
public class ReviewBoardServerConfiguration extends
    AbstractDescribableImpl<ReviewBoardServerConfiguration> {
    private final String reviewBoardURL;
    private final String credentialsId;

    /**
     * Constructs the server configuration with the given name, Review Board
     * URL and API token.
     * @param reviewBoardURL Review Board server URL
     * @param credentialsId Credentials identifier
     */
    @DataBoundConstructor
    public ReviewBoardServerConfiguration(final String reviewBoardURL,
                                          final String credentialsId) {
        this.reviewBoardURL = reviewBoardURL;
        this.credentialsId = credentialsId;
    }

    /**
     * Returns the Review Board endpoint. This is required for Jenkins to
     * display the endpoint details in the GUI.
     * @return Review Board endpoint
     */
    public String getReviewBoardURL() {
        return reviewBoardURL;
    }

    /**
     * Returns the credentials ID, which is used to store the API token.
     * @return Credentials ID
     */
    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * Fetch the Review Board API token from the credential provider.
     * @return The API token, or "UNKNOWN" if not found.
     */
    public String getReviewBoardAPIToken() {
        final List<StringCredentials> credentials = CredentialsMatchers.filter(
            CredentialsProvider.lookupCredentials(
                StringCredentials.class,
                Jenkins.getInstance(),
                ACL.SYSTEM,
                URIRequirementBuilder.fromUri(reviewBoardURL).build()),
            CredentialsMatchers.withId(credentialsId));

        if (!credentials.isEmpty()) {
            return credentials.get(0).getSecret().getPlainText();
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Provides the description of the notification build step and validation
     * functions for fields in its configuration form.
     */
    @Extension
    public static final class DescriptorImpl
        extends Descriptor<ReviewBoardServerConfiguration> {
        /**
         * Returns the display name for this configuration, as shown in the
         * Jenkins GUI.
         * @return Notification build step display name
         */
        @Override
        public String getDisplayName() {
            return Messages.
                ReviewBoardServerConfiguration_DescriptorImpl_DisplayName();
        }

        /**
         * Validates the given config name specified in the form.
         * @param value Configuration name
         * @return FormValidation status
         */
        public FormValidation doCheckName(final @QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error(
                    Messages.ReviewBoard_Error_InvalidName());
            } else {
                return FormValidation.ok();
            }
        }

        /**
         * Validates the given Review Board URL specified in the form.
         * @param value Review Board URL
         * @return FormValidation status
         */
        public FormValidation doCheckReviewBoardURL(
            final @QueryParameter String value) {
            try {
                final URL url = new URL(value);
                url.toURI();
            } catch (final MalformedURLException | URISyntaxException e) {
                return FormValidation.error(
                    Messages.ReviewBoard_Error_InvalidURL());
            }
            return FormValidation.ok();
        }

        /**
         * Fills the API token credentials dropdown box with credentials
         * that are valid for the Review Board endpoint.
         * @param reviewBoardURL Review Board server URL
         * @param credentialsId Credentials identifier
         * @return ListBoxModel containing credential IDs
         */
        public ListBoxModel doFillCredentialsIdItems(
            @QueryParameter String reviewBoardURL,
            @QueryParameter String credentialsId) {
            if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().
                    includeCurrentValue(credentialsId);
            }

            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                    ACL.SYSTEM,
                    Jenkins.getInstance(),
                    StringCredentials.class,
                    URIRequirementBuilder.fromUri(reviewBoardURL).build(),
                    CredentialsMatchers.always()
                );
        }
    }
}
