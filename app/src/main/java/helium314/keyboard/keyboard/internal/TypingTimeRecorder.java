/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

public final class TypingTimeRecorder {
    // M2Y Board: the former gesture parameter, now fixed
    private static final int STATIC_TIME_THRESHOLD_AFTER_FAST_TYPING = 500; // msec
    private long mLastLetterTypingTime;

    public boolean isInFastTyping(final long eventTime) {
        final long elapsedTimeSinceLastLetterTyping = eventTime - mLastLetterTypingTime;
        return elapsedTimeSinceLastLetterTyping < STATIC_TIME_THRESHOLD_AFTER_FAST_TYPING;
    }

    public void onCodeInput(final int code, final long eventTime) {
        // Record the letter typing time when
        // 1. A letter key is typed.
        // 2. A non-letter key is typed within the threshold time since the last letter key typing.
        if (Character.isLetter(code)
                || eventTime - mLastLetterTypingTime < STATIC_TIME_THRESHOLD_AFTER_FAST_TYPING) {
            mLastLetterTypingTime = eventTime;
        }
    }
}
