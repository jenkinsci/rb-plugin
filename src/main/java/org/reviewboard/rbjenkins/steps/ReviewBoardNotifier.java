package org.reviewboard.rbjenkins.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.*;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.httpclient.HttpStatus;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.reviewboard.rbjenkins.Messages;
import org.reviewboard.rbjenkins.common.ReviewBoardException;
import org.reviewboard.rbjenkins.common.ReviewBoardUtils;
import org.reviewboard.rbjenkins.common.ReviewRequest;
import org.reviewboard.rbjenkins.config.ReviewBoardGlobalConfiguration;
import org.reviewboard.rbjenkins.config.ReviewBoardServerConfiguration;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Creates a post-build step in Jenkins for notifying Review Board of the
 * status of the triggered build.
 */
public class ReviewBoardNotifier extends Notifier {
    /**
     * Constructs the notifier.
     */
    @DataBoundConstructor
    public ReviewBoardNotifier() {
    }

    /**
     * This function will be called as part of the post-build step. It will
     * notify Review Board of the status of the build and update the status
     * update resource.
     * @param build The active Jenkins build
     * @param launcher Process launcher
     * @param listener Logger
     * @return True if build can continue
     * @throws IOException
     */
    @Override
    public boolean perform(final AbstractBuild<?, ?> build,
                           final Launcher launcher,
                           final BuildListener listener)
        throws IOException {
        final ReviewRequest reviewRequest;

        try {
            reviewRequest = ReviewBoardUtils.parseReviewRequestFromParameters(
                build.getActions(ParametersAction.class));
        } catch (MalformedURLException e) {
            listener.error("URL provided in REVIEWBOARD_SERVER is not a " +
                           "valid URL.");

            // Return true so we don't kill the build, as this is a post-build
            // step and isn't essential.
            return true;
        }

        // Check that we've successfully received all parameters.
        if (reviewRequest.getReviewId() == -1 ||
            reviewRequest.getStatusUpdateId() == -1 ||
            reviewRequest.getServerURL() == null) {
            listener.error("REVIEWBOARD_REVIEW_ID, or " +
                           "REVIEWBOARD_STATUS_UPDATE_ID, or " +
                           "REVIEWBOARD_SERVER not provided in parameters");

            // Return true so we don't kill the build, as this is a post-build
            // step and isn't essential.
            return true;
        }

        final Result result = build.getResult();
        final ReviewRequest.StatusUpdateState state;
        final String description;

        if (result == Result.SUCCESS) {
            state = ReviewRequest.StatusUpdateState.SUCCESS_STATE;
            description = Messages.ReviewBoard_Job_Success();
        } else {
            state = ReviewRequest.StatusUpdateState.FAILURE_STATE;
            if (result == Result.ABORTED) {
                description = Messages.ReviewBoard_Job_Aborted();
            } else if (result == Result.NOT_BUILT) {
                description = Messages.ReviewBoard_Job_NotBuilt();
            } else if (result == Result.UNSTABLE) {
                description = Messages.ReviewBoard_Job_Unstable();
            } else {
                description = Messages.ReviewBoard_Job_Failure();
            }
        }

        // Notify review board of the build result
        try {
            updateStatusUpdate(reviewRequest, state, description);
        } catch (final ReviewBoardException e) {
            listener.error("Unable to notify Review Board of the result of " +
                           "the build. Cause: " + e.getMessage());
        }

        return true;
    }

    /**
     * Updates a review request's status update object. This is what is shown
     * on the review request page detailing the integration's status.
     * @param reviewRequest Review request
     * @param state Status update state
     * @param description Status update description
     * @throws IOException
     * @throws ReviewBoardException
     */
    public void updateStatusUpdate(
        final ReviewRequest reviewRequest,
        final ReviewRequest.StatusUpdateState state,
        final String description) throws IOException, ReviewBoardException {
        final String path = String.format(
            "/api/review-requests/%d/status-updates/%d/",
            reviewRequest.getReviewId(),
            reviewRequest.getStatusUpdateId());

        final ReviewBoardGlobalConfiguration globalConfig =
            GlobalConfiguration.all().get(
                ReviewBoardGlobalConfiguration.class);
        if (globalConfig == null) {
            throw new ReviewBoardException(
                "No Review Board server configurations found.");
        }

        final ReviewBoardServerConfiguration serverConfig =
            globalConfig.getServerConfiguration(reviewRequest.getServerURL());
        if (serverConfig == null) {
            throw new ReviewBoardException(
                String.format("No Review Board server configuration found " +
                              "for server URL '%s'.",
                              reviewRequest.getServerURL().toString()));
        }

        final String token = String.format(
            "token %s", serverConfig.getReviewBoardAPIToken());
        final URL url = new URL(
            new URL(serverConfig.getReviewBoardURL()), path);
        final HttpURLConnection conn =
            (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", token);
        conn.setRequestProperty("Content-Type",
                                "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        // Generate our form content with the state and description of the
        // build result.
        final String encodedState = URLEncoder.encode(
            state.toString(), StandardCharsets.UTF_8.toString());
        final String encodedDescription = URLEncoder.encode(
            description, StandardCharsets.UTF_8.toString());
        final String content = String.format(
            "state=%s&description=%s",
            encodedState,
            encodedDescription);

        try {
            final BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(conn.getOutputStream(),
                                       StandardCharsets.UTF_8));
            writer.write(content);
            writer.flush();
            writer.close();
        } catch (final ConnectException e) {
            throw new ReviewBoardException(
                "Review Board URL could not be reached. Cause: " +
                e.getMessage());
        }

        final int responseCode = conn.getResponseCode();
        switch (responseCode) {
            case HttpStatus.SC_OK:
                break;
            case HttpStatus.SC_NOT_FOUND:
                throw new ReviewBoardException(
                    "Status Update or Review Request not found");
            case HttpStatus.SC_FORBIDDEN:
                throw new ReviewBoardException(
                    "Review Board API token does not have permission to " +
                    "update Status Update");
            case HttpStatus.SC_UNAUTHORIZED:
                throw new ReviewBoardException(
                    "Review Board API token is invalid");
            default:
                throw new ReviewBoardException(
                    String.format("Unhandled response code sent from Review " +
                                  "Board: %d",
                                  responseCode));
        }
    }

    /**
     * Declares the synchronization required for this step, for which we
     * require none.
     * @return BuildStepMonitor.NONE
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Provides the description of the notification build step and validation
     * functions for fields in its configuration form.
     */
    @Symbol("notifyReviewBoard")
    @Extension
    public static final class DescriptorImpl
        extends BuildStepDescriptor<Publisher> {
        /**
         * This validates the Review Board server configuration name. Mostly it
         * checks if there has been a server configuration created.
         * @param value Review Board server config name
         * @return FormValidation
         */
        public FormValidation doCheckReviewBoardServer(
            final @QueryParameter String value) {
            final ReviewBoardGlobalConfiguration globalConfig =
                GlobalConfiguration.all().get(
                    ReviewBoardGlobalConfiguration.class);

            if (globalConfig == null ||
                globalConfig.getServerConfigurations().isEmpty()) {
                return FormValidation.error(
                    Messages.ReviewBoard_Error_NoServers());
            }

            return FormValidation.ok();
        }

        /**
         * Informs Jenkins of whether or not this build step is applicable to
         * the current job, which it always is.
         * @param aClass
         * @return true
         */
        @Override
        public boolean isApplicable(
            final Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * Returns the display name for this build step, as shown in the
         * Jenkins GUI.
         * @return Notification build step display name
         */
        @Override
        public String getDisplayName() {
            return Messages.ReviewBoardNotifier_DescriptorImpl_DisplayName();
        }
    }
}
