package org.reviewboard.rbjenkins.steps;

import hudson.model.*;
import jenkins.model.GlobalConfiguration;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockBuilder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.reviewboard.rbjenkins.Messages;
import org.reviewboard.rbjenkins.common.ReviewBoardException;
import org.reviewboard.rbjenkins.common.ReviewRequest;
import org.reviewboard.rbjenkins.config.ReviewBoardGlobalConfiguration;
import org.reviewboard.rbjenkins.config.ReviewBoardServerConfiguration;

import java.io.IOException;

public class ReviewBoardNotifierTest {
    private static final String REVIEWBOARD_URL = "http://localhost";
    private static final String REVIEWBOARD_API_TOKEN = "api_token";
    private static final int REVIEW_ID = 1;
    private static final int STATUS_UPDATE_ID = 2;

    @Rule
    final public JenkinsRule jenkins = new JenkinsRule();

    @After
    public void resetGlobalConfig() {
        // Ensure that each test has a clean global config
        GlobalConfiguration.all().get(ReviewBoardGlobalConfiguration.class).
            getServerConfigurations().clear();
    }

    public void setupGlobalConfig() {
        resetGlobalConfig();
        ReviewBoardGlobalConfiguration globalConfig =
            GlobalConfiguration.all().get(
                ReviewBoardGlobalConfiguration.class);

        ReviewBoardServerConfiguration serverConfig =
            new ReviewBoardServerConfiguration(REVIEWBOARD_URL,
                                               REVIEWBOARD_API_TOKEN);

        globalConfig.getServerConfigurations().add(serverConfig);
    }

    public void addBuildParameters(final FreeStyleProject project)
        throws IOException {
        final StringParameterDefinition serverURL =
            new StringParameterDefinition("REVIEWBOARD_SERVER",
                                          REVIEWBOARD_URL);
        final StringParameterDefinition reviewId =
            new StringParameterDefinition("REVIEWBOARD_REVIEW_ID",
                                          String.valueOf(REVIEW_ID));
        final StringParameterDefinition statusUpdateId =
            new StringParameterDefinition("REVIEWBOARD_STATUS_UPDATE_ID",
                                          String.valueOf(STATUS_UPDATE_ID));

        project.addProperty(new ParametersDefinitionProperty(reviewId,
                                                             statusUpdateId,
                                                             serverURL));
    }

    @Test
    public void testConfigRoundtrip() throws Exception {
        setupGlobalConfig();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getPublishersList().add(new ReviewBoardNotifier());
        project = jenkins.configRoundtrip(project);
        jenkins.assertEqualDataBoundBeans(new ReviewBoardNotifier(),
                                          project.getPublishersList().get(0));
    }

    @Test
    public void testBuildNoParameters() throws Exception {
        setupGlobalConfig();
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final ReviewBoardNotifier publisher = new ReviewBoardNotifier();
        project.getPublishersList().add(publisher);

        final StringParameterDefinition invalidURL =
            new StringParameterDefinition("REVIEWBOARD_SERVER",
                                          "htp?:/invalidurl?/.");

        project.addProperty(new ParametersDefinitionProperty(invalidURL));

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        jenkins.assertLogContains(
            "URL provided in REVIEWBOARD_SERVER is not a valid URL.", build);
    }

    @Test
    public void testBuildInvalidURL() throws Exception {
        setupGlobalConfig();
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final ReviewBoardNotifier publisher = new ReviewBoardNotifier();
        project.getPublishersList().add(publisher);
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        jenkins.assertLogContains(
            "REVIEWBOARD_REVIEW_ID, or REVIEWBOARD_STATUS_UPDATE_ID, or " +
            "REVIEWBOARD_SERVER not provided in parameters", build);
    }

    @Ignore
    @Test
    public void testBuildWithStatuses() throws Exception {
        testBuildStatus(Result.SUCCESS,
                        ReviewRequest.StatusUpdateState.SUCCESS_STATE,
                        org.reviewboard.rbjenkins.Messages.
                            ReviewBoard_Job_Success());

        testBuildStatus(Result.ABORTED,
                        ReviewRequest.StatusUpdateState.ERROR_STATE,
                        org.reviewboard.rbjenkins.Messages.
                            ReviewBoard_Job_Aborted());

        testBuildStatus(Result.NOT_BUILT,
                        ReviewRequest.StatusUpdateState.ERROR_STATE,
                        org.reviewboard.rbjenkins.Messages.
                            ReviewBoard_Job_NotBuilt());

        testBuildStatus(Result.UNSTABLE,
                        ReviewRequest.StatusUpdateState.FAILURE_STATE,
                        org.reviewboard.rbjenkins.Messages.
                            ReviewBoard_Job_Unstable());

        testBuildStatus(Result.FAILURE,
                        ReviewRequest.StatusUpdateState.FAILURE_STATE,
                        org.reviewboard.rbjenkins.Messages.
                            ReviewBoard_Job_Failure());
    }

    private void testBuildStatus(Result result,
                                 ReviewRequest.StatusUpdateState status,
                                 String message) throws Exception {
        setupGlobalConfig();
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        addBuildParameters(project);

        ReviewBoardNotifier publisher = new ReviewBoardNotifier();
        publisher = Mockito.spy(publisher);
        Mockito.doNothing().when(publisher).updateStatusUpdate(
            ArgumentMatchers.any(ReviewRequest.class),
            ArgumentMatchers.eq(status),
            ArgumentMatchers.eq(message));

        // Force build result
        project.getBuildersList().add(new MockBuilder(result));
        project.getPublishersList().add(publisher);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(result, build);

        Mockito.verify(publisher, Mockito.times(1)).updateStatusUpdate(
            ArgumentMatchers.any(ReviewRequest.class),
            ArgumentMatchers.eq(status),
            ArgumentMatchers.eq(message));
    }

    @Ignore
    @Test
    public void testBuildUpdateError() throws Exception {
        setupGlobalConfig();
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        addBuildParameters(project);

        ReviewBoardNotifier publisher = new ReviewBoardNotifier();
        publisher = Mockito.spy(publisher);
        Mockito.doThrow(new ReviewBoardException("")).when(publisher).
            updateStatusUpdate(
                ArgumentMatchers.any(ReviewRequest.class),
                ArgumentMatchers.eq(
                    ReviewRequest.StatusUpdateState.SUCCESS_STATE),
                ArgumentMatchers.eq(
                    org.reviewboard.rbjenkins.Messages.
                        ReviewBoard_Job_Success()));
        project.getPublishersList().add(publisher);

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);

        Mockito.verify(publisher, Mockito.times(1)).updateStatusUpdate(
            ArgumentMatchers.any(ReviewRequest.class),
            ArgumentMatchers.eq(ReviewRequest.StatusUpdateState.SUCCESS_STATE),
            ArgumentMatchers.eq(Messages.ReviewBoard_Job_Success()));
        jenkins.assertLogContains(
            "Unable to notify Review Board of the result of the build:",
            build);
    }
}
