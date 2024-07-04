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

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.Downsampler
import com.bumptech.glide.request.target.ViewTarget
import com.example.glide.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @Suppress("DEPRECATION")
    private var viewTarget: ViewTarget<ImageView, out Any>? = null
        set(value) {
            field = value
            isGlideInit = true
        }

    private var isGlideInit = false
        set(value) {
            field = value

            if (value) {
                binding.switchUseImageDecoder.isEnabled = false
                binding.switchActiveResourceRetentionAllowed.isEnabled = false
                binding.menuBitmapPoolSize.isEnabled = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        log("onCreate")

        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT))

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        binding.buttonLoadHttps.setOnClickListener {
            log("Load HTTPS")
            viewTarget = Glide.with(this)
                .load("https://placehold.co/3840x2160.png")
                .let { applySwitchRequest(it) }
                .into(binding.image)
                .let { applySwitchInto(it) }
        }

        binding.buttonLoadCpFile.setOnClickListener {
            log("Load ContentProvider File")
            viewTarget = Glide.with(this)
                .load("content://com.example.glide.provider/file/image-3840x2160.png")
                .let { applySwitchRequest(it) }
                .into(binding.image)
                .let { applySwitchInto(it) }
        }

        binding.buttonLoadRes.setOnClickListener {
            log("Load Android Res")
            viewTarget = Glide.with(this)
                .load("android.resource://com.example.glide/${R.raw.image_3840x2160}")
                .let { applySwitchRequest(it) }
                .into(binding.image)
                .let { applySwitchInto(it) }
        }

        binding.switchUseImageDecoder.setOnCheckedChangeListener { _, isChecked ->
            log("useImageDecoder $isChecked")
            (application as App).useImageDecoder = isChecked
        }

        binding.switchActiveResourceRetentionAllowed.setOnCheckedChangeListener { _, isChecked ->
            log("activeResourceRetentionAllowed $isChecked")
            (application as App).isActiveResourceRetentionAllowed = isChecked
        }

        binding.menuBitmapPoolSizeText.apply {
            val bitmapPoolSizeMap = mapOf(
                "0 MB" to 0L,
                "1 MB" to (1L shl 20),
                "2 MB" to (2L shl 20),
                "4 MB" to (4L shl 20),
                "8 MB" to (8L shl 20),
                "16 MB" to (16L shl 20),
                "32 MB" to (32L shl 20),
                "64 MB" to (64L shl 20),
            )

            setSimpleItems(bitmapPoolSizeMap.keys.toTypedArray())

            val app = application as App
            bitmapPoolSizeMap
                .firstNotNullOf { if (it.key == "32 MB") it else null }
                .let {
                    setText(it.key, false)
                    app.bitmapPoolBytes = it.value
                }

            setOnItemClickListener { _, _, position, _ ->
                app.bitmapPoolBytes = bitmapPoolSizeMap.asSequence().drop(position).first().value
            }
        }

        binding.buttonClearImageView.setOnClickListener {
            log("Set null")
            binding.image.setImageDrawable(null)
        }

        binding.buttonClearViewTarget.setOnClickListener {
            log("Clear Request")
            // `viewTarget?.request?.clear()` cancel load request.
            viewTarget?.request?.clear()
            viewTarget = null
        }

        binding.buttonClearViewTarget.setOnClickListener {
            log("Clear ViewTarget")
            // `RequestManager.clear()` cancel load request and free resource.
            Glide.with(this).clear(binding.image)
            viewTarget = null
        }

        binding.buttonClearGlideDisk.setOnClickListener {
            log("Clear Glide disk")
            lifecycleScope.launch(Dispatchers.IO) {
                Glide.get(this@MainActivity).clearDiskCache()
            }
        }

        binding.buttonClearGlideMemory.setOnClickListener {
            log("Clear Glide memory")
            Glide.get(this).clearMemory()
        }

        binding.buttonClearOkhttp.setOnClickListener {
            log("Clear OkHttp")
            (application as App).okHttpClient.cache?.evictAll()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean("isGlideInit", isGlideInit)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        isGlideInit = savedInstanceState.getBoolean("isGlideInit", false)
    }

    override fun onLowMemory() {
        log("onLowMemory")

        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        log("onTrimMemory ${trimMemoryLevelToString(level)}")

        super.onTrimMemory(level)
    }

    private fun <T> applySwitchRequest(builder: RequestBuilder<T>): RequestBuilder<T> = builder
        .let { if (binding.switchCircle.isChecked) it.circleCrop() else it }
        .let { if (binding.switchSkipMemCache.isChecked) it.skipMemoryCache(true) else it }
        .let {
            if (binding.switchSkipDiskCache.isChecked) {
                it.diskCacheStrategy(DiskCacheStrategy.NONE)
            } else {
                it
            }
        }
        .let {
            if (binding.switchUseHardwareBitmaps.isChecked) {
                it.set(Downsampler.ALLOW_HARDWARE_CONFIG, true)
            } else {
                it
            }
        }

    @Suppress("DEPRECATION")
    private fun <T> applySwitchInto(target: ViewTarget<ImageView, T>): ViewTarget<ImageView, T> {
        return target.also {
            if (binding.switchClearOnDetach.isChecked) {
                it.clearOnDetach()
            }
        }
    }

    private fun log(msg: String) = Log.d("MainActivity", msg)
}
