package com.authmod;

import java.util.HashMap;
import java.util.Map;

/**
 * Защита от перебора пароля: после maxLoginAttempts неудачных попыток
 * вход для аккаунта блокируется на lockoutSeconds секунд.
 */
public final class LoginLimiter {
    private static final Map<String, State> STATES = new HashMap<>();

    private static final class State {
        int failedAttempts;
        long lockedUntil;
    }

    private LoginLimiter() {
    }

    public static boolean isLocked(String username) {
        State state = STATES.get(username);
        return state != null && System.currentTimeMillis() < state.lockedUntil;
    }

    public static long remainingSeconds(String username) {
        State state = STATES.get(username);
        if (state == null) return 0;
        long remaining = (state.lockedUntil - System.currentTimeMillis() + 999) / 1000;
        return Math.max(remaining, 1);
    }

    public static void recordFailure(String username) {
        State state = STATES.computeIfAbsent(username, k -> new State());
        state.failedAttempts++;
        if (state.failedAttempts >= AuthConfig.maxLoginAttempts()) {
            state.lockedUntil = System.currentTimeMillis() + AuthConfig.lockoutSeconds() * 1000L;
            state.failedAttempts = 0;
        }
    }

    public static void reset(String username) {
        STATES.remove(username);
    }
}
