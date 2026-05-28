package org.reviewboard.rbjenkins.steps;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
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
    private static final String VENV_DIR = ".rbtools-venv";

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
    public void perform(
            final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener listener)
            throws InterruptedException, IOException {
        final ReviewRequest reviewRequest;

        try {
            reviewRequest = ReviewBoardUtils.parseReviewRequestFromParameters(run.getActions(ParametersAction.class));
        } catch (MalformedURLException e) {
            listener.error("URL provided in REVIEWBOARD_SERVER is not a valid URL.");
            run.setResult(Result.FAILURE);
            return;
        }

        // Check that we've successfully received all required parameters.
        if (reviewRequest.getReviewId() == -1
                || reviewRequest.getRevision() == -1
                || reviewRequest.getStatusUpdateId() == -1
                || reviewRequest.getServerURL() == null) {
            listener.error("REVIEWBOARD_REVIEW_ID, REVIEWBOARD_DIFF_REVISION or "
                    + "REVIEWBOARD_STATUS_UPDATE_ID, or REVIEWBOARD_SERVER not "
                    + "provided in parameters");
            run.setResult(Result.FAILURE);
            return;
        }

        final ReviewBoardGlobalConfiguration globalConfig =
                GlobalConfiguration.all().get(ReviewBoardGlobalConfiguration.class);
        if (globalConfig == null) {
            listener.error("No Review Board server configurations found.");
            run.setResult(Result.FAILURE);
            return;
        }

        final ReviewBoardServerConfiguration serverConfig =
                globalConfig.getServerConfiguration(reviewRequest.getServerURL());
        if (serverConfig == null) {
            listener.error(String.format(
                    "No Review Board server configuration found " + "for server URL '%s'.",
                    reviewRequest.getServerURL().toString()));
            run.setResult(Result.FAILURE);
            return;
        }

        final EnvVars env = run.getEnvironment(listener);

        // Determine which rbt executable to use. If rbtools is not already
        // available on the PATH, install it into a virtualenv in the workspace
        // and use the rbt from there.
        final ArrayList<List<String>> commands = new ArrayList<List<String>>();
        String rbtExecutable = "rbt";

        if (installRBTools && !isRBToolsAvailable(launcher, workspace, env, rbtExecutable)) {
            // Python virtualenvs use a different layout on Windows. The
            // executables live in "Scripts" with a ".exe" suffix rather than
            // in "bin". Use the agent's OS to pick the right paths.
            final boolean isUnix = launcher.isUnix();
            final String binDir = isUnix ? "bin" : "Scripts";
            final String exeSuffix = isUnix ? "" : ".exe";

            final FilePath venvDir = workspace.child(VENV_DIR);
            final String venvRbt =
                    venvDir.child(binDir).child("rbt" + exeSuffix).getRemote();
            rbtExecutable = venvRbt;

            if (!isRBToolsAvailable(launcher, workspace, env, venvRbt)) {
                // No existing virtualenv to reuse, so create one and install
                // rbtools into it.
                final String venvPip =
                        venvDir.child(binDir).child("pip" + exeSuffix).getRemote();
                commands.add(List.of("python3", "-m", "venv", venvDir.getRemote()));
                commands.add(List.of(venvPip, "install", "rbtools"));
            }
        }

        // Construct the command to use rbtools to apply the patch. Each
        // argument is passed separately so that values such as the server URL
        // are never split on whitespace.
        final List<String> rbtCommand = new ArrayList<String>();
        rbtCommand.add(rbtExecutable);
        rbtCommand.add("patch");
        rbtCommand.add("--api-token");
        final int apiTokenIndex = rbtCommand.size();
        rbtCommand.add(serverConfig.getReviewBoardAPIToken());
        rbtCommand.add("--server");
        rbtCommand.add(serverConfig.getReviewBoardURL());
        rbtCommand.add("--diff-revision");
        rbtCommand.add(Integer.toString(reviewRequest.getRevision()));
        if (downloadOnly) {
            rbtCommand.add("--write");
            rbtCommand.add("patch.diff");
        }
        rbtCommand.add(Integer.toString(reviewRequest.getReviewId()));
        commands.add(rbtCommand);

        // Mask the API token value so it is hidden from the console output.
        final boolean[] rbtCommandMask = new boolean[rbtCommand.size()];
        rbtCommandMask[apiTokenIndex] = true;

        for (int i = 0; i < commands.size(); i++) {
            final List<String> command = commands.get(i);
            final Launcher.ProcStarter args = launcher.launch()
                    .cmds(command)
                    .stdout(listener)
                    .pwd(workspace)
                    .envs(env);

            // The patch command is the final entry and is the only one that
            // carries a value to mask.
            if (i == commands.size() - 1) {
                args.masks(rbtCommandMask);
            }

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
            listener.error("Unable to notify Review Board of the build: " + e.getMessage());
        }
    }

    /**
     * Returns whether the given rbt executable is available and runnable.
     *
     * This runs the executable with --version and checks for a successful
     * exit code. A missing executable raises an IOException, which is treated
     * as rbtools not being available.
     * @param launcher Process launcher
     * @param workspace Active workspace
     * @param env Build environment
     * @param rbtExecutable Path to or name of the rbt executable to probe
     * @return true if the executable ran successfully
     */
    private boolean isRBToolsAvailable(
            final Launcher launcher, final FilePath workspace, final EnvVars env, final String rbtExecutable)
            throws InterruptedException {
        try {
            final Proc process = launcher.launch()
                    .cmds(rbtExecutable, "--version")
                    .pwd(workspace)
                    .envs(env)
                    .quiet(true)
                    .start();
            return process.join() == 0;
        } catch (final IOException e) {
            return false;
        }
    }

    /**
     * Provides the description of the setup build step and validation
     * functions for fields in its form.
     */
    @Symbol("publishReview")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * This validates the Review Board server configuration name. Mostly it
         * checks if there has been a server configuration created.
         * @param value Review Board server config name
         * @return FormValidation
         */
        public FormValidation doCheckReviewBoardServer(final @QueryParameter String value) {
            final ReviewBoardGlobalConfiguration globalConfig =
                    GlobalConfiguration.all().get(ReviewBoardGlobalConfiguration.class);

            if (globalConfig == null || globalConfig.getServerConfigurations().isEmpty()) {
                return FormValidation.error(Messages.ReviewBoard_Error_NoServers());
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
        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
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
