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

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.EncodeStrategy
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import java.io.InputStream

@Suppress("unused")
@Excludes(OkHttpLibraryGlideModule::class)
@GlideModule
class CustomAppGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        /*
         * ModelLoader#handles が Model を見て扱える・扱えないを返す。
         * ModelLoader#buildLoadData が LoadData（とそれに含まれる DataFetcher）を返し
         * DataFetcher#getDataSource が DataSource を返す。
         * Model       Data                    ModelLoader                        DataSource
         *:--------- |:--------------------- |:-------------------------------- |:----------
         * Bitmap,     Bitmap               -> UnitModelLoader                 -> LOCAL
         * GifDecoder, GifDecoder           -> UnitModelLoader                 -> LOCAL
         * File,       ByteBuffer           -> ByteBufferFileLoader            -> LOCAL
         * File,       InputStream          -> FileLoader                      -> LOCAL
         * File,       ParcelFileDescriptor -> FileLoader                      -> LOCAL
         * File,       File                 -> UnitModelLoader                 -> LOCAL
         * int,        InputStream          -> DirectResourceLoader            -> LOCAL
         * Integer,    InputStream          -> DirectResourceLoader            -> LOCAL
         * int,        AssetFileDescriptor  -> DirectResourceLoader            -> LOCAL
         * Integer,    AssetFileDescriptor  -> DirectResourceLoader            -> LOCAL
         * int,        Drawable             -> DirectResourceLoader            -> LOCAL
         * Integer,    Drawable             -> DirectResourceLoader            -> LOCAL
         * Uri,        InputStream          -> ResourceUriLoader               -> LOCAL (Integer, InputStream) ("content://")
         * Uri,        AssetFileDescriptor  -> ResourceUriLoader               -> LOCAL (Integer, AssetFileDescriptor) ("content://")
         * Integer,    Uri                  -> ResourceLoader(UnitModelLoader) -> LOCAL
         * int,        Uri                  -> ResourceLoader(UnitModelLoader) -> LOCAL
         * Integer,    AssetFileDescriptor  -> ResourceLoader                  -> LOCAL (Uri, AssetFileDescriptor)
         * int,        AssetFileDescriptor  -> ResourceLoader                  -> LOCAL (Uri, AssetFileDescriptor)
         * Integer,    InputStream          -> ResourceLoader                  -> LOCAL (Uri, InputStream) ("content://")
         * int,        InputStream          -> ResourceLoader                  -> LOCAL (Uri, InputStream) ("content://")
         * String,     InputStream          -> DataUrlLoader                   -> LOCAL
         * Uri,        InputStream          -> DataUrlLoader                   -> LOCAL
         * String,     InputStream          -> StringLoader                    -> LOCAL/REMOTE (Uri, InputStream)
         * String,     ParcelFileDescriptor -> StringLoader                    -> LOCAL (Uri, ParcelFileDescriptor)
         * String,     AssetFileDescriptor  -> StringLoader                    -> LOCAL (Uri, AssetFileDescriptor)
         * Uri,        InputStream          -> AssetUriLoader                  -> LOCAL
         * Uri,        AssetFileDescriptor  -> AssetUriLoader                  -> LOCAL
         * Uri,        InputStream          -> MediaStoreImageThumbLoader      -> LOCAL
         * Uri,        InputStream          -> MediaStoreVideoThumbLoader      -> LOCAL
         * Uri,        InputStream          -> QMediaStoreUriLoader            -> LOCAL
         * Uri,        ParcelFileDescriptor -> QMediaStoreUriLoader            -> LOCAL
         * Uri,        InputStream          -> UriLoader                       -> LOCAL
         * Uri,        ParcelFileDescriptor -> UriLoader                       -> LOCAL
         * Uri,        AssetFileDescriptor  -> UriLoader                       -> LOCAL
         * Uri,        InputStream          -> UrlUriLoader                    -> REMOTE (GlideUrl, InputStream)
         * URL,        InputStream          -> UrlLoader                       -> REMOTE (GlideUrl, InputStream)
         * Uri,        File                 -> MediaStoreFileLoader            -> LOCAL
         * GlideUrl,   InputStream          -> HttpGlideUrlLoader              -> REMOTE
         * byte[],     ByteBuffer           -> ByteArrayLoader                 -> LOCAL
         * byte[],     InputStream          -> ByteArrayLoader                 -> LOCAL
         * Uri,        Uri                  -> UnitModelLoader                 -> LOCAL
         * Drawable,   Drawable             -> UnitModelLoader                 -> LOCAL
         */

        // GlideUrl,   InputStream          -> OkHttpUrlLoader                 -> REMOTE
        // GlideUrl, InputStream -> HttpGlideUrlLoader の Loader を OkHttpUrlLoader に置換。
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            // Factory が持つ DataFetcher が DataSource を返す。
            OkHttpUrlLoader.Factory((context.applicationContext as App).okHttpClient),
        )
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val app = context.applicationContext as App

        builder
            .setLogLevel(Log.VERBOSE)
            .setImageDecoderEnabledForBitmaps((context.applicationContext as App).useImageDecoder)
            .setDefaultRequestOptions {
                RequestOptions().diskCacheStrategy(CustomDiskCacheStrategy)
            }
            .setIsActiveResourceRetentionAllowed(app.isActiveResourceRetentionAllowed)
            .setBitmapPool(
                app.bitmapPoolBytes.let { bitmapPoolBytes ->
                    if (bitmapPoolBytes == 0L) {
                        BitmapPoolAdapter()
                    } else {
                        LruBitmapPool(bitmapPoolBytes)
                    }
                }
            )
    }
}

object CustomDiskCacheStrategy : DiskCacheStrategy() {
    // NONE は全てのキャッシュを利用しないし保存しない。
    // DATA は
    //  - isDataCacheable() = dataSource != DataSource.DATA_DISK_CACHE && dataSource != DataSource.MEMORY_CACHE
    //  - decodeCacheData() = true
    //  により LOCAL/REMOTE (ContentProvider や HTTP) を保存し SOURCE のキャッシュをデコードする。
    //  isResourceCacheable() = false により恐らく RESOURCE_DISK_CACHE は来ない。
    // RESOURCE は
    //  - isResourceCacheable() = dataSource != DataSource.RESOURCE_DISK_CACHE && dataSource != DataSource.MEMORY_CACHE
    //  - decodeCacheResource() = true
    //  により最終変換後のデータのみ保存する。
    // ALL は
    //  - isDataCacheable() = dataSource == DataSource.REMOTE
    //  - isResourceCacheable() = dataSource != DataSource.RESOURCE_DISK_CACHE && dataSource != DataSource.MEMORY_CACHE
    //  - decodeCacheResource() = true
    //  - decodeCacheData() = true
    //  により
    //  - HTTP のデータを保存する（ContentProvider は保存しない）
    //  - 最終変換後のデータも保存する。
    // AUTOMATIC は
    //  - isDataCacheable() = dataSource == DataSource.REMOTE
    //  - isResourceCacheable() = ((isFromAlternateCacheKey && dataSource == DataSource.DATA_DISK_CACHE) || dataSource == DataSource.LOCAL) && encodeStrategy == EncodeStrategy.TRANSFORMED
    //  - decodeCacheResource() = true
    //  - decodeCacheData() = true
    //  により
    //  - HTTP のデータを保存する（ContentProvider は保存しない）
    //      - 通常 alternateKeys を使用しないので TRANSFORMED はキャッシュしない
    //          - BaseGlideUrlLoader (alternateKey を提供する) を使った実装はサンプルしかない
    //      - 代替キーを使っているかつディスクキャッシュからロードした場合は最終変換後のデータを保存する
    //  - ContentProvider からロードした場合は最終変換後のデータを保存する
    //  のため ALL と比較すると EncodedStrategy も見ており最終変換時に何かしら変換したもののみに絞って保存する。

    override fun isDataCacheable(dataSource: DataSource): Boolean {
        log("isDataCacheable: $dataSource")

        return when (dataSource) {
            DataSource.LOCAL -> false
            DataSource.REMOTE -> false // use cache of okhttp.
            DataSource.DATA_DISK_CACHE,
            DataSource.RESOURCE_DISK_CACHE,
            DataSource.MEMORY_CACHE -> false
        }
    }

    override fun isResourceCacheable(
        isFromAlternateCacheKey: Boolean,
        dataSource: DataSource,
        encodeStrategy: EncodeStrategy,
    ): Boolean {
        // isFromAlternateCacheKey は LoadData(Key, List<Key>, DataFetcher<Data>) の第二引数。
        log("isResourceCacheable: $isFromAlternateCacheKey, $dataSource, $encodeStrategy")

        when (encodeStrategy) {
            EncodeStrategy.SOURCE,
            EncodeStrategy.NONE -> return false

            EncodeStrategy.TRANSFORMED -> Unit
        }

        return when (dataSource) {
            DataSource.LOCAL,
            DataSource.REMOTE -> true

            DataSource.DATA_DISK_CACHE,
            DataSource.RESOURCE_DISK_CACHE,
            DataSource.MEMORY_CACHE -> false
        }
    }

    override fun decodeCachedResource(): Boolean {
        log("decodeCachedResource")
        return true
    }

    override fun decodeCachedData(): Boolean {
        log("decodeCachedData")
        return false
    }

    private fun log(msg: String) = Log.i("CustomAppGlideModule", msg)
}
