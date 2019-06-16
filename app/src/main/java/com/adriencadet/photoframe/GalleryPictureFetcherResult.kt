package com.adriencadet.photoframe

sealed class GalleryPictureFetcherResult {

    data class Success(val files: Sequence<PictureFile>) : GalleryPictureFetcherResult()

    object InvalidCursor : GalleryPictureFetcherResult()
}

data class PictureFile(
    val absolutePath: String,
    val folderName: String
)