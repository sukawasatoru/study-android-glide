/*
 * Copyright 2024 sukawasatoru
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.glide

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.util.Log
import androidx.annotation.RawRes
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class AppContentProvider : ContentProvider() {
    private val matcher: UriMatcher by lazy { createUriMatcher() }

    private lateinit var coroutineScope: CoroutineScope

    override fun onCreate(): Boolean {
        coroutineScope = (requireContext().applicationContext as App).let {
            it.coroutineScope + Dispatchers.IO + CoroutineName("Co-AppContentProvider")
        }

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? {
        log("getType uri: $uri")

        return when (matcher.match(uri)) {
            0 -> "image/png"
            1 -> "image/bmp"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun openFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        log("openFile uri: $uri, mode: $mode, signal: $signal")

        if (mode != "r") {
            throw IllegalArgumentException("Invalid mode $mode")
        }

        return when (matcher.match(uri)) {
            0 -> transferRawResourceForOpenFile(R.raw.image_3840x2160, signal)
            1 -> transferRawResourceForOpenFile(R.raw.image_3840x2160_bmp, signal)
            else -> throw FileNotFoundException("File not found $uri")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        log("openAssetFile uri: $uri, mode: $mode")
        return openAssetFile(uri, mode, null)
    }

    override fun openAssetFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        log("openAssetFile uri: $uri, mode: $mode, signal: $signal")

        if (mode != "r") {
            throw IllegalArgumentException("Invalid mode $mode")
        }

        return when (matcher.match(uri)) {
            0 -> requireContext().resources.openRawResourceFd(R.raw.image_3840x2160)
            1 -> requireContext().resources.openRawResourceFd(R.raw.image_3840x2160_bmp)
            else -> {
                AssetFileDescriptor(
                    openFile(uri, mode, signal),
                    0,
                    AssetFileDescriptor.UNKNOWN_LENGTH,
                )
            }
        }
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        log("getStreamTypes uri: $uri, mimeTypeFilter: $mimeTypeFilter")

        return when (matcher.match(uri)) {
            0 -> when (mimeTypeFilter) {
                "image/*",
                "*/png" -> arrayOf("image/png")

                else -> null
            }

            1 -> when (mimeTypeFilter) {
                "image/*",
                "*/bmp" -> arrayOf("image/bmp")

                else -> null
            }

            else -> null
        }
    }

    override fun onLowMemory() {
        log("onLowMemory")

        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        log("onTrimMemory ${trimMemoryLevelToString(level)}")

        super.onTrimMemory(level)
    }

    private fun createUriMatcher(): UriMatcher {
        val packageName = requireContext().packageName

        return UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI("$packageName.provider", "file/image-3840x2160.png", 0)
            addURI("$packageName.provider", "file/image-3840x2160.bmp", 1)
        }
    }

    private fun transferRawResourceForOpenFile(
        @RawRes resId: Int,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val (ifd, ofd) = ParcelFileDescriptor.createPipe()
        coroutineScope.launch {
            val start = System.nanoTime()
            requireContext().resources.openRawResource(resId).use { source ->
                AutoCloseOutputStream(ofd).use { ost ->
                    var total = 0L
                    val buffer = ByteArray(8 * 1024)

                    var read = source.read(buffer)
                    while (0 <= read) {
                        ensureActive()
                        if (signal?.isCanceled == true) {
                            throw CancellationException()
                        }

                        ost.write(buffer, 0, read)
                        total += read
                        read = source.read(buffer)
                    }
                }
            }
            val stop = System.nanoTime()
            log("start to stop: ${stop - start}")
        }
        return ifd
    }

    private fun log(msg: String) = Log.i("AppContentProvider", msg)
}
