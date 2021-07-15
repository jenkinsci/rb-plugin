package org.reviewboard.rbjenkins.steps;

import static org.junit.Assert.assertTrue;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.*;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;
import org.reviewboard.rbjenkins.config.ReviewBoardGlobalConfiguration;
import org.reviewboard.rbjenkins.config.ReviewBoardServerConfiguration;

import java.io.IOException;

public class ReviewBoardSetupTest {
    private static final String REVIEWBOARD_URL = "http://localhost";
    private static final String REVIEWBOARD_CREDENTIALS = "api_token";
    private static final String REVIEW_ID = "1";
    private static final String STATUS_UPDATE_ID = "2";
    private static final String DIFF_REVISION = "3";

    @Rule
    final public JenkinsRule jenkins = new JenkinsRule();

    @After
    public void resetGlobalConfig() {
        // Ensure that each test has a clean global config
        GlobalConfiguration.all().get(ReviewBoardGlobalConfiguration.class).
            getServerConfigurations().clear();
    }

    public void setupGlobalConfig() {
        setupGlobalConfig(true);
    }

    public void setupGlobalConfig(boolean addServerConfig) {
        ReviewBoardGlobalConfiguration globalConfig =
            GlobalConfiguration.all().get(
                ReviewBoardGlobalConfiguration.class);

        if (addServerConfig) {
            ReviewBoardServerConfiguration serverConfig =
                new ReviewBoardServerConfiguration(REVIEWBOARD_URL,
                                                   REVIEWBOARD_CREDENTIALS);

            globalConfig.getServerConfigurations().add(serverConfig);
        }
    }

    public void addBuildParameters(final FreeStyleProject project)
        throws IOException {
        final StringParameterDefinition serverURL =
            new StringParameterDefinition("REVIEWBOARD_SERVER",
                                          REVIEWBOARD_URL);
        final StringParameterDefinition reviewId =
            new StringParameterDefinition("REVIEWBOARD_REVIEW_ID", REVIEW_ID);
        final StringParameterDefinition diffRevision =
            new StringParameterDefinition("REVIEWBOARD_DIFF_REVISION",
                                          DIFF_REVISION);
        final StringParameterDefinition statusUpdateId =
            new StringParameterDefinition("REVIEWBOARD_STATUS_UPDATE_ID",
                                          STATUS_UPDATE_ID);

        project.addProperty(new ParametersDefinitionProperty(reviewId,
                                                             diffRevision,
                                                             statusUpdateId,
                                                             serverURL));
    }

    @Test
    public void testConfigRoundtrip() throws Exception {
        setupGlobalConfig();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new ReviewBoardSetup(false, true));
        project = jenkins.configRoundtrip(project);
        jenkins.assertEqualDataBoundBeans(new ReviewBoardSetup(false, true),
                                          project.getBuildersList().get(0));
    }

    @Test
    public void testConfigRoundtripDownloadOnly() throws Exception {
        setupGlobalConfig();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new ReviewBoardSetup(true, true));
        project = jenkins.configRoundtrip(project);
        jenkins.assertEqualDataBoundBeans(new ReviewBoardSetup(true, true),
                                          project.getBuildersList().get(0));
    }

    @Test
    public void testBuildNoParameters() throws Exception {
        setupGlobalConfig();
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final ReviewBoardSetup builder = new ReviewBoardSetup(false, true);
        project.getBuildersList().add(builder);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains(
            "REVIEWBOARD_REVIEW_ID, REVIEWBOARD_DIFF_REVISION or " +
            "REVIEWBOARD_STATUS_UPDATE_ID, or REVIEWBOARD_SERVER not " +
            "provided in parameters", build);
    }

    @Test
    public void testBuildInvalidURL() throws Exception {
        setupGlobalConfig();
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final ReviewBoardSetup builder = new ReviewBoardSetup(false, true);
        project.getBuildersList().add(builder);

        final StringParameterDefinition invalidURL =
            new StringParameterDefinition("REVIEWBOARD_SERVER",
                                          "htp?:/invalidurl?/.");

        project.addProperty(new ParametersDefinitionProperty(invalidURL));

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains(
            "URL provided in REVIEWBOARD_SERVER is not a valid URL.", build);
    }

    @Test
    public void testBuildWithParameters() throws Exception {
        setupGlobalConfig();
        final String[] commands = {
            "pip install --user rbtools",
            String.format("rbt patch --api-token %s --server %s " +
                          "--diff-revision %s  %s",
                          "UNKNOWN",
                          REVIEWBOARD_URL,
                          DIFF_REVISION,
                          REVIEW_ID)
        };

        final PretendSlave slave = jenkins.createPretendSlave(procStarter -> {
            // Check that we run the correct commands.
            String command = String.join(" ", procStarter.cmds());
            assertTrue(ArrayUtils.contains(commands, command));

            return new FakeLauncher.FinishedProc(0);
        });

        final FreeStyleProject project = jenkins.createFreeStyleProject();
        addBuildParameters(project);

        final ReviewBoardSetup builder = new ReviewBoardSetup(false, true);
        project.getBuildersList().add(builder);
        project.setAssignedNode(slave);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    public void testBuildWithParametersDownloadOnly() throws Exception {
        setupGlobalConfig();
        final String[] commands = {
            "pip install --user rbtools",
            String.format("rbt patch --api-token %s --server %s " +
                          "--diff-revision %s --write patch.diff %s",
                          "UNKNOWN",
                          REVIEWBOARD_URL,
                          DIFF_REVISION,
                          REVIEW_ID)
        };

        final PretendSlave slave = jenkins.createPretendSlave(procStarter -> {
            // Check that we run the correct commands.
            String command = String.join(" ", procStarter.cmds());
            assertTrue(ArrayUtils.contains(commands, command));

            return new FakeLauncher.FinishedProc(0);
        });

        final FreeStyleProject project = jenkins.createFreeStyleProject();
        addBuildParameters(project);

        final ReviewBoardSetup builder = new ReviewBoardSetup(true, true);
        project.getBuildersList().add(builder);
        project.setAssignedNode(slave);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    public void testBuildWithNoGlobalConfig() throws Exception {
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        addBuildParameters(project);

        final ReviewBoardSetup builder = new ReviewBoardSetup(false, true);
        project.getBuildersList().add(builder);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains(
            "No Review Board server configuration found for server URL " +
            "'http://localhost'.", build);
    }

    @Test
    public void testBuildWithNoServerConfig() throws Exception {
        setupGlobalConfig(false);
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        addBuildParameters(project);

        final ReviewBoardSetup builder = new ReviewBoardSetup(false, true);
        project.getBuildersList().add(builder);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains(
            "No Review Board server configuration found for server URL " +
            "'http://localhost'.", build);
    }

    @Test
    public void testBuildCommandFails() throws Exception {
        setupGlobalConfig();
        final PretendSlave slave = jenkins.createPretendSlave(procStarter -> {
            // Here we're testing that a failed command results in a failed
            // build.
            return new FakeLauncher.FinishedProc(1);
        });

        final FreeStyleProject project = jenkins.createFreeStyleProject();
        addBuildParameters(project);

        final ReviewBoardSetup builder = new ReviewBoardSetup(false, true);
        project.getBuildersList().add(builder);
        project.setAssignedNode(slave);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
    }
}
