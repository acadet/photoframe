package com.adriencadet.photoframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import android.media.ExifInterface
import android.graphics.Matrix

data class Picture(
    val bitmap: Bitmap,
    val folderName: String
)

class PictureService(val fetcher: GalleryPictureFetcher) {

    fun observeImages(context: Context, desiredWidth: Int, desiredHeight: Int): Observable<Picture> {
        return fetcher.fetchPaths(context)
            .map { it.shuffled() }
            .flatMapObservable { files ->
                Observable.intervalRange(
                    0,
                    files.size.toLong() - 1,
                    0,
                    Constants.DURATION_SEC,
                    TimeUnit.SECONDS,
                    Schedulers.io()
                )
                    .map { it.toInt() }
                    .map { index -> files[index] }
            }
            .switchMapMaybe { (path, folderName) ->
                Maybe.defer {
                    val bitmap = rotateIfNeeded(path, buildResizedBitmap(path, desiredWidth, desiredHeight))

                    if (bitmap == null) {
                        Maybe.empty()
                    } else {
                        Maybe.just(Picture(bitmap, folderName))
                    }
                }
                    .subscribeOn(Schedulers.io())
            }
    }

    private fun buildResizedBitmap(absolutePath: String, desiredWidth: Int, desiredHeight: Int): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true // DO NOT load bitmap in memory
        BitmapFactory.decodeFile(absolutePath, options)

        val currentWidth = options.outWidth
        val currentHeight = options.outHeight
        val scaleFactor = Math.min(currentWidth / desiredWidth, currentHeight / desiredHeight)
        options.inJustDecodeBounds = false
        options.inSampleSize = scaleFactor

        return BitmapFactory.decodeFile(absolutePath, options)
    }

    private fun rotateIfNeeded(absolutePath: String, bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null

        val exifInterface = ExifInterface(absolutePath)
        val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val transformation = getTransformation(orientation)

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transformation, false)
    }

    private fun getTransformation(orientationExifAttribute: Int): Matrix {
        val transformation = Matrix()

        when (orientationExifAttribute) {
            ExifInterface.ORIENTATION_ROTATE_90 -> transformation.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transformation.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> transformation.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transformation.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transformation.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                transformation.postRotate(90f)
                transformation.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                transformation.postRotate(-90f)
                transformation.postScale(-1f, 1f)
            }
        }
        return transformation
    }
}