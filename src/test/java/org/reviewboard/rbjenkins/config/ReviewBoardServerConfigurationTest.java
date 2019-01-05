package org.reviewboard.rbjenkins.config;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ReviewBoardServerConfigurationTest {
    private static final String REVIEWBOARD_URL = "http://localhost";
    private static final String REVIEWBOARD_CREDENTIALS = "credentials_id";
    private static final String REVIEWBOARD_API_TOKEN = "api_token";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @After
    public void resetTest() {
        // Ensure that each test has a clean global config
        GlobalConfiguration.all().get(ReviewBoardGlobalConfiguration.class).
            getServerConfigurations().clear();
        SystemCredentialsProvider.getInstance().getCredentials().clear();
    }

    @Test
    public void testMissingCredentials() throws Exception {
        ReviewBoardGlobalConfiguration globalConfig =
            GlobalConfiguration.all().get(
                ReviewBoardGlobalConfiguration.class);

        ReviewBoardServerConfiguration serverConfig =
            new ReviewBoardServerConfiguration(REVIEWBOARD_URL,
                                               REVIEWBOARD_CREDENTIALS);

        globalConfig.getServerConfigurations().add(serverConfig);

        assertNotEquals(serverConfig.getReviewBoardAPIToken(),
                        REVIEWBOARD_API_TOKEN);
    }

    @Test
    public void testCredentials() throws Exception {
        StringCredentials credentials = new StringCredentialsImpl(
            CredentialsScope.SYSTEM, REVIEWBOARD_CREDENTIALS, "Description",
            Secret.fromString(REVIEWBOARD_API_TOKEN));

        SystemCredentialsProvider.getInstance().getCredentials().
            add(credentials);

        ReviewBoardGlobalConfiguration globalConfig =
            GlobalConfiguration.all().get(
                ReviewBoardGlobalConfiguration.class);

        ReviewBoardServerConfiguration serverConfig =
            new ReviewBoardServerConfiguration(REVIEWBOARD_URL,
                                               REVIEWBOARD_CREDENTIALS);

        globalConfig.getServerConfigurations().add(serverConfig);

        assertEquals(serverConfig.getReviewBoardAPIToken(),
                     REVIEWBOARD_API_TOKEN);
    }
}
