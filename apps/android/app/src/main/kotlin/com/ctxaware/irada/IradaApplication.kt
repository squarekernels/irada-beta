package com.ctxaware.irada

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ctxaware.irada.data.DraftStore
import com.ctxaware.irada.preview.PreviewClient
import com.ctxaware.irada.preview.PreviewService
import java.io.IOException

class IradaApplication : Application() {
    private val client by lazy { PreviewClient(BuildConfig.PREVIEW_BASE_URL) }
    // Replaced before Activity launch by isolated UI tests.
    var previewService: PreviewService = PreviewService { request ->
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true)
            throw IOException("No network")
        client.generate(request)
    }
    val drafts by lazy { DraftStore(this) }
}
