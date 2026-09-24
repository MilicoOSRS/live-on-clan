package com.liveon;

/** Client-thread-owned recovery state; a transport error is not a membership rejection. */
final class MembershipRecovery
{
    private long retryAt;
    private int failures;
    private boolean rejected;

    boolean due(long now) { return retryAt > 0 && now >= retryAt; }
    boolean rejected() { return rejected; }
    boolean failed() { return failures > 0; }
    void begin() { retryAt = 0; }
    void accountChanged() { rejected = false; failures = 0; retryAt = 0; }
    void stale(long now) { retryAt = now + 5000L; }
    void failed(long now) {
        rejected = false;
        retryAt = now + Math.min(60000L, 5000L << Math.min(failures, 4));
        failures = Math.min(4, failures + 1);
    }
    void resolved(boolean member, long expiresAt) {
        rejected = !member;
        failures = 0;
        retryAt = member ? 0 : expiresAt;
    }
}
