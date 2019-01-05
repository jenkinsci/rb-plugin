package org.reviewboard.rbjenkins.common;

import hudson.model.Action;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

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
}
