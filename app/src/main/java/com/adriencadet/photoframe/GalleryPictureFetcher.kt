package com.adriencadet.photoframe

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers


class GalleryPictureFetcher {

    fun fetchPaths(context: Context): Single<GalleryPictureFetcherResult> {
        return Single
            .fromCallable { fetch(context) }
            .subscribeOn(Schedulers.io())
    }

    private fun fetch(context: Context): GalleryPictureFetcherResult {
        val fileCursor = context.toFileCursor() ?: return GalleryPictureFetcherResult.InvalidCursor

        val pathIndex = fileCursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val folderNameIndex = fileCursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        if (!fileCursor.moveToFirst()) return GalleryPictureFetcherResult.Success(emptySequence())

        val pictureFiles = ArrayList<PictureFile>()
        do {
            val absolutePath = try {
                fileCursor.getString(pathIndex).orEmpty()
            } catch (e: Exception) {
                Log.e("PictureFrame", "Could not retrieve absolutePath")
                null
            }
            val folderName = try {
                fileCursor.getString(folderNameIndex).orEmpty()
            } catch (e: Exception) {
                Log.e("PictureFrame", "Could not retrieve folderName")
                null
            }

            absolutePath ?: continue
            folderName ?: continue

            pictureFiles.add(PictureFile(absolutePath, folderName))
        } while (fileCursor.moveToNext())

        return GalleryPictureFetcherResult.Success(pictureFiles.asSequence())
    }

    private fun Context.toFileCursor(): Cursor? {
        return contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            listOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
                .toTypedArray(),
            null,
            null,
            null
        )
    }
}