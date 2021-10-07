package org.reviewboard.rbjenkins.steps;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.net.MalformedURLException;
import jenkins.model.GlobalConfiguration;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.reviewboard.rbjenkins.Messages;
import org.reviewboard.rbjenkins.common.ReviewBoardException;
import org.reviewboard.rbjenkins.common.ReviewBoardUtils;
import org.reviewboard.rbjenkins.common.ReviewRequest;
import org.reviewboard.rbjenkins.config.ReviewBoardGlobalConfiguration;
import org.reviewboard.rbjenkins.config.ReviewBoardServerConfiguration;

/**
 * Creates a build step in Jenkins which will install and use rbtools to apply
 * the patch from Review Board for the given review request.
 */
public class ReviewBoardSetup extends Builder implements SimpleBuildStep {
    private static final String RBT_INSTALL = "pip install --user rbtools";
    private static final String RBT_COMMAND =
        "rbt patch --api-token %s --server %s --diff-revision %d %s%d";

    private boolean downloadOnly = false;
    private boolean installRBTools = true;

    /**
     * Constructs the setup step.
     */
    @DataBoundConstructor
    public ReviewBoardSetup(boolean downloadOnly, boolean installRBTools) {
        this.downloadOnly = downloadOnly;
        this.installRBTools = installRBTools;
    }

    public boolean getDownloadOnly() {
      return downloadOnly;
    }

    public boolean getInstallRBTools() {
      return installRBTools;
    }

    /**
     * This function is called as part of a build when the setup step has been
     * added. This will install rbtools and then use it to apply the patch
     * for the given review request, as specified in the build parameters.
     * @param run Current build
     * @param workspace Active workspace
     * @param launcher Process launcher
     * @param listener Logger
     */
    @Override
    public void perform(final Run<?, ?> run, final FilePath workspace,
                        final Launcher launcher, final TaskListener listener)
        throws InterruptedException,IOException {
        final ReviewRequest reviewRequest;

        try {
            reviewRequest = ReviewBoardUtils.parseReviewRequestFromParameters(
                run.getActions(ParametersAction.class));
        } catch (MalformedURLException e) {
            listener.error("URL provided in REVIEWBOARD_SERVER is not a " +
                           "valid URL.");
            run.setResult(Result.FAILURE);
            return;
        }

        // Check that we've successfully received all required parameters.
        if (reviewRequest.getReviewId() == -1 ||
            reviewRequest.getRevision() == -1 ||
            reviewRequest.getStatusUpdateId() == -1 ||
            reviewRequest.getServerURL() == null) {
            listener.error("REVIEWBOARD_REVIEW_ID, " +
                           "REVIEWBOARD_DIFF_REVISION or " +
                           "REVIEWBOARD_STATUS_UPDATE_ID, or " +
                           "REVIEWBOARD_SERVER not provided in " +
                           "parameters");
            run.setResult(Result.FAILURE);
            return;
        }

        final ReviewBoardGlobalConfiguration globalConfig =
            GlobalConfiguration.all().get(
                ReviewBoardGlobalConfiguration.class);
        if (globalConfig == null) {
            listener.error("No Review Board server configurations found.");
            run.setResult(Result.FAILURE);
            return;
        }

        final ReviewBoardServerConfiguration serverConfig =
            globalConfig.getServerConfiguration(reviewRequest.getServerURL());
        if (serverConfig == null) {
            listener.error(
                String.format("No Review Board server configuration found " +
                              "for server URL '%s'.",
                              reviewRequest.getServerURL().toString()));
            run.setResult(Result.FAILURE);
            return;
        }

        String downloadOnlyFile = "";
        if (downloadOnly) {
            downloadOnlyFile = "--write patch.diff ";
        }

        // Construct commands to install and use rbtools.
        final String rbtCommand = String.format(
            RBT_COMMAND,
            serverConfig.getReviewBoardAPIToken(),
            serverConfig.getReviewBoardURL(),
            reviewRequest.getRevision(),
            downloadOnlyFile,
            reviewRequest.getReviewId());

        // Generate a command mask to hide the API token from the console
        // output.
        final boolean[] rbtCommandMask =
            new boolean[rbtCommand.split(" ").length];

        // Set the 4th entry's mask to true - this is the API token.
        rbtCommandMask[3] = true;

        ArrayList<Launcher.ProcStarter> commands = new ArrayList<Launcher.ProcStarter>();
        if (installRBTools) {
            commands.add(launcher.launch().cmds(RBT_INSTALL.split(" ")));
        }
        commands.add(launcher.launch().cmds(rbtCommand.split(" ")).masks(rbtCommandMask));

        for (Launcher.ProcStarter args : commands) {
            // Run the command in the workspace
            args.stdout(listener).pwd(workspace)
                .envs(run.getEnvironment(listener));
            final Proc process = launcher.launch(args);

            final int result = process.join();
            if (result != 0) {
                run.setResult(Result.FAILURE);
                return;
            }
        }

        // Update the review request with the link to the build.
        try {
            ReviewBoardUtils.updateStatusUpdate(
                reviewRequest,
                ReviewRequest.StatusUpdateState.PENDING_STATE,
                "build running",
                run.getAbsoluteUrl(),
                "See build");
        } catch (final ReviewBoardException e) {
            listener.error("Unable to notify Review Board of the build: " +
                           e.getMessage());
        }
    }

    /**
     * Provides the description of the setup build step and validation
     * functions for fields in its form.
     */
    @Symbol("publishReview")
    @Extension
    public static final class DescriptorImpl
        extends BuildStepDescriptor<Builder> {
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
         * @return Setup build step display name
         */
        @Override
        public String getDisplayName() {
            return Messages.ReviewBoardSetup_DescriptorImpl_DisplayName();
        }
    }
}
