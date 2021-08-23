package com.adriencadet.photoframe

data class PhotoFrameState(
    val desiredWidth: Int,
    val desiredHeight: Int,
    val currentPictureResult: PictureResult?,
    val isRunning: Boolean
)
