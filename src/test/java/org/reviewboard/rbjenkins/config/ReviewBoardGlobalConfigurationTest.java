package org.reviewboard.rbjenkins.config;

import org.junit.Test;
import org.mockito.Mockito;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ReviewBoardGlobalConfigurationTest {
    private static final String REVIEWBOARD_URL = "http://localhost";
    private static final String REVIEWBOARD_CREDENTIALS = "api_token";

    @Test
    public void testConstructorAndSetter() throws Exception {
        List<ReviewBoardServerConfiguration> serverConfigs = new ArrayList<>();

        ReviewBoardGlobalConfiguration config =
            new ReviewBoardGlobalConfiguration(serverConfigs);

        config = Mockito.spy(config);
        // save() will otherwise throw an exception due to not being properly
        // setup.
        Mockito.doNothing().when(config).save();

        config.setServerConfigurations(serverConfigs);
    }

    @Test
    public void testExistingServer() throws Exception {
        List<ReviewBoardServerConfiguration> serverConfigs = new ArrayList<>();
        serverConfigs.add(
            new ReviewBoardServerConfiguration(REVIEWBOARD_URL,
                                               REVIEWBOARD_CREDENTIALS));

        ReviewBoardGlobalConfiguration config =
            new ReviewBoardGlobalConfiguration(serverConfigs);

        ReviewBoardServerConfiguration server =
            config.getServerConfiguration(new URL(REVIEWBOARD_URL));

        assertNotNull(server);
    }

    @Test
    public void testNonExistentServer() throws Exception {
        List<ReviewBoardServerConfiguration> serverConfigs = new ArrayList<>();
        serverConfigs.add(
            new ReviewBoardServerConfiguration(REVIEWBOARD_URL,
                                               REVIEWBOARD_CREDENTIALS));

        ReviewBoardGlobalConfiguration config =
            new ReviewBoardGlobalConfiguration(serverConfigs);

        ReviewBoardServerConfiguration server =
            config.getServerConfiguration(new URL("http://nonexistent"));

        assertNull(server);
    }
}
