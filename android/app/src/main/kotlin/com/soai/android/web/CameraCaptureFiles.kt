// SPDX-License-Identifier: MIT

package com.soai.android.web

import java.io.File

internal object CameraCaptureFiles {
    const val DIRECTORY = "captures"
    private const val PREFIX = "soai_capture_"
    private const val SUFFIX = ".jpg"
    private const val STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L

    fun create(directory: File): File = File.createTempFile(PREFIX, SUFFIX, directory)

    fun pruneStale(directory: File, nowMillis: Long) {
        directory.listFiles { file ->
            file.isFile &&
                file.name.startsWith(PREFIX) &&
                file.name.endsWith(SUFFIX) &&
                nowMillis - file.lastModified() > STALE_AFTER_MILLIS
        }?.forEach { file -> file.delete() }
    }
}
