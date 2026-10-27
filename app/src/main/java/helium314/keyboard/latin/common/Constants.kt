// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

object Links {
    const val DICTIONARY_URL = "https://codeberg.org/Helium314/aosp-dictionaries"
    const val DICTIONARY_DOWNLOAD_SUFFIX = "/raw/branch/main/"
    const val DICTIONARY_NORMAL_SUFFIX = "dictionaries/"
    const val DICTIONARY_EMOJI_CLDR_SUFFIX = "emoji_cldr_signal_dictionaries/"
    const val GITHUB = "https://github.com/mehmetmesut/byd-m2y-board"
    const val LICENSE = "$GITHUB/blob/main/LICENSE"
    const val UPSTREAM = "https://github.com/HeliBorg/HeliBoard" // HeliBoard, the project M2Y Board is based on
    const val WIKI_URL = "$UPSTREAM/wiki"
    const val LAYOUT_WIKI_URL = "$WIKI_URL/2.-Layouts"
    const val CUSTOM_LAYOUTS = "$UPSTREAM/discussions/categories/custom-layout"
    const val CUSTOM_COLORS = "$UPSTREAM/discussions/categories/custom-colors"
    const val COMMUNITY_LINKS = "$UPSTREAM#to-community"
}

val combiningRange = 0x300..0x35b
