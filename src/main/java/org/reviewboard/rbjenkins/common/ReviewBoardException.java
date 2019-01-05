package org.reviewboard.rbjenkins.common;

/**
 * A ReviewBoardException is thrown when an error occurs while communicating
 * with a Review Board server.
 */
public class ReviewBoardException extends Exception {
    /**
     * Construct the ReviewBoardException with the given error message.
     * @param message Error message
     */
    public ReviewBoardException(String message) {
        super(message);
    }
}
