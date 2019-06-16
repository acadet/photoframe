package com.adriencadet.photoframe

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers


class GalleryPictureFetcher {

    fun fetchPaths(context: Context): Single<GalleryPictureFetcherResult> {
        return Single.fromCallable { fetch(context) }
            .subscribeOn(Schedulers.io())
    }

    private fun fetch(context: Context): GalleryPictureFetcherResult {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = listOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        ).toTypedArray()

        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor ?: return GalleryPictureFetcherResult.InvalidCursor

        val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val folderNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        if (!cursor.moveToFirst()) return GalleryPictureFetcherResult.Success(emptySequence())

        val outcome = ArrayList<PictureFile>()
        do {
            val absolutePath = try {
                cursor.getString(pathIndex).orEmpty()
            } catch (e: Exception) {
                Log.e("PictureFrame", "Could not retrieve absolutePath")
                null
            }
            val folderName = try {
                cursor.getString(folderNameIndex).orEmpty()
            } catch (e: Exception) {
                Log.e("PictureFrame", "Could not retrieve folderName")
                null
            }

            absolutePath ?: continue
            folderName ?: continue

            outcome.add(PictureFile(absolutePath, folderName))
        } while (cursor.moveToNext())

        return GalleryPictureFetcherResult.Success(outcome.asSequence())
    }
}