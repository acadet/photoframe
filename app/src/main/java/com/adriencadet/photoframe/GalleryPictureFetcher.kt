package com.adriencadet.photoframe

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers


class GalleryPictureFetcher {

    private companion object {
        val TARGET_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val PATH_COLUMN = MediaStore.MediaColumns.DATA
        val FOLDER_NAME_COLUMN = MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    }

    fun fetchPaths(context: Context): Single<GalleryPictureFetcherResult> {
        return Single
            .fromCallable { fetch(context) }
            .subscribeOn(Schedulers.io())
    }

    private fun fetch(context: Context): GalleryPictureFetcherResult {
        val fileCursor = context.toFileCursor() ?: return GalleryPictureFetcherResult.InvalidCursor

        val pathIndex = fileCursor.getColumnIndex(PATH_COLUMN)
        val folderNameIndex = fileCursor.getColumnIndex(FOLDER_NAME_COLUMN)

        if (!fileCursor.moveToFirst()) return GalleryPictureFetcherResult.Success(emptySequence())

        val pictureFiles = ArrayList<PictureFile>()
        try {
            do {
                /**
                 * I spent a lot of time investigating here but it is possible some files are
                 * missing an absolute path for some reason (might be corrupted?).
                 *
                 * Let's ignore them when adding them to the list.
                 *
                 * I spent some time playing with URIs as well but that did not help, not worth
                 * investigating more.
                 */

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

                if (absolutePath.isNullOrEmpty()) continue
                folderName ?: continue

                pictureFiles.add(PictureFile(absolutePath = absolutePath, folderName = folderName))
            } while (fileCursor.moveToNext())
        } finally {
            fileCursor.close()
        }

        return GalleryPictureFetcherResult.Success(pictureFiles.asSequence())
    }

    private fun Context.toFileCursor(): Cursor? {
        return contentResolver.query(
            TARGET_URI,
            listOf(
                PATH_COLUMN,
                FOLDER_NAME_COLUMN
            )
                .toTypedArray(),
            null,
            null,
            null
        )
    }
}