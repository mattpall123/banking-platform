package com.bank.backend.etransfer.service;

/**
 * Thrown when the recipient submits the wrong security answer but still
 * has retries left. The frontend can show "Wrong answer, X attempts left."
 *
 * Mapped to 422 (UNPROCESSABLE_ENTITY) by the global handler — it's a
 * valid request that we're refusing because of business state, not a 400
 * (which would say the request was malformed).
 */
public class SecurityAnswerWrongException extends RuntimeException {
    private final int attemptsRemaining;

    public SecurityAnswerWrongException(int attemptsRemaining) {
        super("Wrong security answer. " + attemptsRemaining + " attempt(s) remaining.");
        this.attemptsRemaining = attemptsRemaining;
    }

    public int getAttemptsRemaining() {
        return attemptsRemaining;
    }
}