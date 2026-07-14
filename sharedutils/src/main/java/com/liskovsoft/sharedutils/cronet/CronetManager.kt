package com.liskovsoft.sharedutils.cronet

import android.content.Context
import com.liskovsoft.sharedutils.BuildConfig
import com.liskovsoft.sharedutils.mylogger.Log
import java.io.File
import java.util.concurrent.Executors
import org.chromium.net.CronetEngine
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.impl.NativeCronetProvider

object CronetManager {
    private val TAG = CronetManager::class.java.simpleName

    /** Logcat tag shared with the app-level open-path milestones (common NetPath). */
    private const val NETPATH_TAG = "NetPath"

    /**
     * Dedicated subdir for Cronet's disk prefs. With HTTP_CACHE_DISK_NO_HTTP this stores ONLY
     * QUIC server configs + network-quality estimates - NOT HTTP response bodies - so the first
     * media request after a cold start can attempt QUIC 0-RTT instead of paying the full
     * TCP+TLS(+QUIC discovery) handshake. This is a different mechanism from the media/subtitle
     * response caching that SharedModules PR#11 rejected (that concern was about body caching;
     * media bodies stay in ExoPlayer's CacheDataSource tier).
     */
    private const val STORAGE_DIR_NAME = "cronet-prefs"
    private const val DISK_PREFS_BYTES = 1024L * 1024 // 1 MB - server configs are tiny

    private var engine: CronetEngine? = null

    @JvmStatic
    fun getEngine(context: Context): CronetEngine? {
        if (engine == null) {
            try {
                val builder = NativeCronetProvider(context).createBuilder()

                builder
                    .enableQuic(true)
                    .enableHttp2(true)
                    .enableBrotli(true)

                // QUIC config persistence (see STORAGE_DIR_NAME doc). The storage path must exist
                // before build(); failure to create it just skips persistence.
                try {
                    val storageDir = File(context.cacheDir, STORAGE_DIR_NAME)
                    storageDir.mkdirs()
                    if (storageDir.isDirectory) {
                        builder
                            .setStoragePath(storageDir.absolutePath)
                            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, DISK_PREFS_BYTES)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "cronet storage path setup failed: ${e.message}")
                }

                val built = builder.build()

                // Debug-only transport observability: one dense line per finished request under
                // the NetPath tag - negotiated protocol (h3/h2/http1.1), status, ttfb, total,
                // bytes, socket reuse. Answers "is googlevideo actually on QUIC / are connections
                // reused across videos / did the cold start pay a handshake" without a debugger.
                // Same debug detection as OkHttpCommons.debugSetup (sharedutils BuildConfig).
                if (BuildConfig.DEBUG) {
                    val listenerExecutor = Executors.newSingleThreadExecutor { r ->
                        Thread(r, "CronetNetPath").apply { isDaemon = true }
                    }
                    built.addRequestFinishedListener(object : RequestFinishedInfo.Listener(listenerExecutor) {
                        override fun onRequestFinished(info: RequestFinishedInfo) {
                            try {
                                val response = info.responseInfo
                                val metrics = info.metrics
                                val url = info.url
                                // Host + first path segment is enough to tell media/innertube/warm apart.
                                val shortUrl = url.substringAfter("://").let {
                                    if (it.length > 64) it.substring(0, 64) else it
                                }
                                android.util.Log.d(NETPATH_TAG, "cronet " +
                                        (response?.negotiatedProtocol ?: "?") + " " +
                                        (response?.httpStatusCode ?: -1) +
                                        " ttfb=" + (metrics?.ttfbMs ?: -1) +
                                        "ms total=" + (metrics?.totalTimeMs ?: -1) +
                                        "ms rx=" + (metrics?.receivedByteCount ?: -1) +
                                        " reused=" + (if (metrics?.socketReused == true) "y" else "n") +
                                        // googlevideo PO-token forensics: pot-less media URLs die
                                        // at a ~60s-of-served-media grace wall on pot-enforcing
                                        // (carrier CGNAT) networks - one glance tells whether the
                                        // dying request even carried the token. Query form (pot=)
                                        // on VOD format URLs; path form (/pot/) on live manifest +
                                        // segment URLs.
                                        " pot=" + (if (url.contains("pot=") || url.contains("/pot/")) "y" else "n") +
                                        " " + shortUrl)
                            } catch (e: Exception) {
                                // observability must never break the transport
                            }
                        }
                    })
                }

                engine = built
            } catch (e: UnsatisfiedLinkError) {
                // Fatal Exception: java.lang.UnsatisfiedLinkError
                // Cannot load library: soinfo_relocate(linker.cpp:982): cannot locate symbol "getauxval" referenced by "libcronet.101.0.4951.41.so".
                e.printStackTrace()
                Log.e(TAG, e.message)
            }
        }

        return engine
    }
}
