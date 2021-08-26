package org.reviewboard.rbjenkins.common;

import hudson.model.Action;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.httpclient.HttpStatus;
import org.reviewboard.rbjenkins.config.ReviewBoardGlobalConfiguration;
import org.reviewboard.rbjenkins.config.ReviewBoardServerConfiguration;

/**
 * Contains common utility functions.
 */
public class ReviewBoardUtils {
    private static final String REVIEWBOARD_DIFF_REVISION =
        "REVIEWBOARD_DIFF_REVISION";
    private static final String REVIEWBOARD_REVIEW_ID =
        "REVIEWBOARD_REVIEW_ID";
    private static final String REVIEWBOARD_STATUS_UPDATE_ID =
        "REVIEWBOARD_STATUS_UPDATE_ID";
    private static final String REVIEWBOARD_SERVER =
        "REVIEWBOARD_SERVER";

    /**
     * Parse the review request details from the build parameters.
     * @param actions List of ParametersAction actions from the build
     * @return ReviewRequest object
     */
    public static ReviewRequest parseReviewRequestFromParameters(
        final List<ParametersAction> actions) throws MalformedURLException {
        int reviewId = -1;
        int revision = -1;
        int statusUpdateId = -1;
        URL serverURL = null;

        for (Action action : actions) {
            final ParametersAction pAction = (ParametersAction) action;
            for (ParameterValue parameterValue : pAction.getParameters()) {
                final Object value = parameterValue.getValue();
                switch (parameterValue.getName()) {
                    case REVIEWBOARD_REVIEW_ID:
                        reviewId = Integer.parseInt((String) value);
                        break;
                    case REVIEWBOARD_DIFF_REVISION:
                        revision = Integer.parseInt((String) value);
                        break;
                    case REVIEWBOARD_STATUS_UPDATE_ID:
                        statusUpdateId = Integer.parseInt((String) value);
                        break;
                    case REVIEWBOARD_SERVER:
                        serverURL = new URL((String) value);
                        break;
                    default:
                        break;
                }
            }
        }

        return new ReviewRequest(reviewId, revision, statusUpdateId,
                                 serverURL);
    }

    /**
     * Updates a status update on a review request. This is the resource which
     * displays the status of the build within the Review Board UI.
     *
     * @param reviewRequest Review Request
     * @param state Status update state
     * @param description Status update description
     * @param url URL to use for the build link
     * @param urlText Text to use for the build link
     */
    public static void updateStatusUpdate(
        final ReviewRequest reviewRequest,
        final ReviewRequest.StatusUpdateState state,
        final String description,
        final String url,
        final String urlText)
        throws IOException, ReviewBoardException {
        Objects.requireNonNull(reviewRequest, "reviewRequest must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(description, "description must not be null");

        final String path = String.format(
            "/api/review-requests/%d/status-updates/%d/",
            reviewRequest.getReviewId(),
            reviewRequest.getStatusUpdateId());

        final ReviewBoardGlobalConfiguration globalConfig = (
            GlobalConfiguration.all()
            .get(ReviewBoardGlobalConfiguration.class)
        );

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
        final URL serverUrl = new URL(
            new URL(serverConfig.getReviewBoardURL()), path);
        final HttpURLConnection conn =
            (HttpURLConnection)serverUrl.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", token);
        conn.setRequestProperty("Content-Type",
                                "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        final String utf8 = StandardCharsets.UTF_8.toString();
        String content = String.format(
            "state=%s&description=%s",
            URLEncoder.encode(state.toString(), utf8),
            URLEncoder.encode(description, utf8));

        if (url != null) {
            content += String.format("&url=%s",
                                     URLEncoder.encode(url, utf8));
        }

        if (urlText != null) {
            content += String.format("&url_text=%s",
                                     URLEncoder.encode(urlText, utf8));
        }

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
}
