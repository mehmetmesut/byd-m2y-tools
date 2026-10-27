/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.define

import helium314.keyboard.latin.BuildConfig

object DebugFlags {
    // M2Y Board: no debug settings screen; debug builds show extra info
    @JvmField
    val DEBUG_ENABLED = BuildConfig.DEBUG
}
