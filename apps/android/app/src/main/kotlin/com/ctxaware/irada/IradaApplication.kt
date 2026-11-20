package com.ctxaware.irada

import android.app.Application
import com.ctxaware.irada.data.DraftStore

class IradaApplication : Application() {
    val drafts by lazy { DraftStore(this) }
}
