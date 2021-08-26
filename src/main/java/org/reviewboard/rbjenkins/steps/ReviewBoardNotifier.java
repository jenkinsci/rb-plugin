package org.reviewboard.rbjenkins.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Notifier;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import jenkins.tasks.SimpleBuildStep;
import hudson.util.FormValidation;
import hudson.FilePath;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.reviewboard.rbjenkins.Messages;
import org.reviewboard.rbjenkins.common.ReviewBoardException;
import org.reviewboard.rbjenkins.common.ReviewBoardUtils;
import org.reviewboard.rbjenkins.common.ReviewRequest;
import org.reviewboard.rbjenkins.config.ReviewBoardGlobalConfiguration;

import java.io.IOException;
import java.net.MalformedURLException;
import javax.annotation.Nonnull;

/**
 * Creates a post-build step in Jenkins for notifying Review Board of the
 * status of the triggered build.
 */
public class ReviewBoardNotifier extends Notifier implements SimpleBuildStep {
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
     * @param run The active Jenkins build
     * @param FilePath The path to the build ifle
     * @param launcher Process launcher
     * @param listener Logger
     */
    @Override
    public void perform(
            @Nonnull Run<?, ?> run,
            @Nonnull FilePath filePath,
            @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) throws InterruptedException, IOException {
        final ReviewRequest reviewRequest;

        try {
            reviewRequest = ReviewBoardUtils.parseReviewRequestFromParameters(
                run.getActions(ParametersAction.class));
        } catch (MalformedURLException e) {
            listener.error("URL provided in REVIEWBOARD_SERVER is not a " +
                           "valid URL.");
            return;
        }

        // Check that we've successfully received all parameters.
        if (reviewRequest.getReviewId() == -1 ||
            reviewRequest.getStatusUpdateId() == -1 ||
            reviewRequest.getServerURL() == null) {
            listener.error("REVIEWBOARD_REVIEW_ID, or " +
                           "REVIEWBOARD_STATUS_UPDATE_ID, or " +
                           "REVIEWBOARD_SERVER not provided in parameters");
            return;
        }

        final Result result = run.getResult();
        final ReviewRequest.StatusUpdateState state;
        final String description;

        if (result == Result.SUCCESS) {
            state = ReviewRequest.StatusUpdateState.SUCCESS_STATE;
            description = Messages.ReviewBoard_Job_Success();
        } else {
            if (result == Result.ABORTED) {
                state = ReviewRequest.StatusUpdateState.ERROR_STATE;
                description = Messages.ReviewBoard_Job_Aborted();
            } else if (result == Result.NOT_BUILT) {
                state = ReviewRequest.StatusUpdateState.ERROR_STATE;
                description = Messages.ReviewBoard_Job_NotBuilt();
            } else if (result == Result.UNSTABLE) {
                state = ReviewRequest.StatusUpdateState.FAILURE_STATE;
                description = Messages.ReviewBoard_Job_Unstable();
            } else {
                state = ReviewRequest.StatusUpdateState.FAILURE_STATE;
                description = Messages.ReviewBoard_Job_Failure();
            }
        }

        // Notify review board of the build result
        try {
            updateStatusUpdate(reviewRequest, state, description);
        } catch (final ReviewBoardException e) {
            listener.error("Unable to notify Review Board of the result of " +
                           "the build: " + e.getMessage());
        }

        return;
    }

    public void updateStatusUpdate(
        final ReviewRequest reviewRequest,
        final ReviewRequest.StatusUpdateState state,
        final String description)
        throws IOException, ReviewBoardException {
        ReviewBoardUtils.updateStatusUpdate(
            reviewRequest, state, description, null, null);
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
         * @param aClass The project class
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
