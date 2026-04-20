package com.sawwere.yoloapp.core.system.camera

import android.graphics.Bitmap

interface AnalyzeFrame {
    operator fun invoke(frame: Bitmap)
}