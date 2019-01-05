package org.reviewboard.rbjenkins.common;

import java.net.URL;

/**
 * Stores information about the Review Request which triggered the Jenkins
 * build.
 */
public class ReviewRequest {
    final private int reviewId;
    final private int revision;
    final private int statusUpdateId;
    final private URL serverURL;

    /**
     * Enumerates the possible states for a status update to be in.
     */
    public enum StatusUpdateState {
        SUCCESS_STATE("done-success"),
        FAILURE_STATE("done-failure"),
        TIMED_OUT_STATE("timed-out"),
        ERROR_STATE("error"),
        PENDING_STATE("pending");

        private String value;

        /**
         * Constructs the status update with the given value.
         * @param value Enum value
         */
        StatusUpdateState(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Construct the ReviewRequest object with information about the review
     * request.
     * @param reviewId Review request ID
     * @param revision Revision of the review request
     * @param statusUpdateId Review request's status update ID
     * @param serverURL Review Board server URL
     */
    public ReviewRequest(final int reviewId,
                         final int revision,
                         final int statusUpdateId,
                         final URL serverURL) {
        this.reviewId = reviewId;
        this.revision = revision;
        this.statusUpdateId = statusUpdateId;
        this.serverURL = serverURL;
    }

    /**
     * Returns the review ID of the review request.
     * @return Review request ID
     */
    public int getReviewId() {
        return reviewId;
    }

    /**
     * Returns the revision of the review request.
     * @return Review request revision
     */
    public int getRevision() {
        return revision;
    }

    /**
     * Returns the status update ID for the review request.
     * @return Status update ID
     */
    public int getStatusUpdateId() {
        return statusUpdateId;
    }

    /**
     * Returns the server URL for the review request.
     * @return Server URL
     */
    public URL getServerURL() {
        return serverURL;
    }
}
