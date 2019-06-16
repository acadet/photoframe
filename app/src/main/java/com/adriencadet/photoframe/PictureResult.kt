package com.adriencadet.photoframe

import android.graphics.Bitmap

sealed class PictureResult {

    data class Success(
        val bitmap: Bitmap,
        val folderName: String
    ) : PictureResult()

    object BitmapOperationFailure : PictureResult()

    object StorageFailure : PictureResult()
}