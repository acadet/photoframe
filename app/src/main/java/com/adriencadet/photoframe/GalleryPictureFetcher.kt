package com.adriencadet.photoframe

import android.content.Context
import android.provider.MediaStore
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

data class PictureFile(
    val absolutePath: String,
    val folderName: String
)

class GalleryPictureFetcher {

    fun fetchPaths(context: Context): Single<List<PictureFile>> {
        return Single.fromCallable { fetch(context) }
            .subscribeOn(Schedulers.io())
    }

    private fun fetch(context: Context): List<PictureFile> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        val projection = listOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        ).toTypedArray()

        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val folderNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        if (cursor.moveToFirst() == false) return emptyList()

        val outcome = ArrayList<PictureFile>()
        do {
            outcome.add(PictureFile(cursor.getString(pathIndex), cursor.getString(folderNameIndex)))
        } while (cursor.moveToNext())

        return outcome
    }
}