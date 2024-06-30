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

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.google.android.material.color.DynamicColors
import java.nio.file.Paths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class App : Application() {
    val coroutineScope: CoroutineScope by lazy { MainScope() }

    var useImageDecoder = false
    var isActiveResourceRetentionAllowed = false

    lateinit var okHttpClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()

        okHttpClient = OkHttpClient.Builder()
            .cache(
                Cache(
                    Paths.get(cacheDir.path, "okhttp").toFile(),
                    200 * 1024 * 1024,
                )
            )
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun onLowMemory() {
        log("onLowMemory")

        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        log("onTrimMemory: ${trimMemoryLevelToString(level)}")

        super.onTrimMemory(level)
    }

    private fun log(msg: String) = Log.i("App", msg)
}

fun trimMemoryLevelToString(level: Int): String = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "TRIM_MEMORY_RUNNING_MODERATE($level)"
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW($level)"
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL($level)"
    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "TRIM_MEMORY_UI_HIDDEN($level)"
    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "TRIM_MEMORY_BACKGROUND($level)"
    ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "TRIM_MEMORY_MODERATE($level)"
    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE($level)"
    else -> "UNKNOWN_LEVEL($level)"
}
