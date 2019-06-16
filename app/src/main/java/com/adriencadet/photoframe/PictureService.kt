package com.adriencadet.photoframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class PictureService(val fetcher: GalleryPictureFetcher) {

    fun observeImages(context: Context, desiredWidth: Int, desiredHeight: Int): Observable<PictureResult> {
        return fetcher.fetchPaths(context)
            .flatMapObservable { result ->
                when (result) {
                    is GalleryPictureFetcherResult.Success -> emitPictures(result.files, desiredWidth, desiredHeight)
                    is GalleryPictureFetcherResult.InvalidCursor -> Observable.just(PictureResult.StorageFailure)
                }
            }
    }

    private fun emitPictures(
        pictureFiles: Sequence<PictureFile>,
        desiredWidth: Int,
        desiredHeight: Int
    ): Observable<PictureResult> {
        val count = pictureFiles.count()

        if (count == 0) return Observable.empty()

        val shuffledIndexes = (0 until count).shuffled()
        val requestEmitter = BehaviorRelay.createDefault(0)

        return requestEmitter.take(count.toLong())
            .map { index ->
                val shuffledIndex = shuffledIndexes[index]
                index to pictureFiles.elementAt(shuffledIndex)
            }
            .switchMap { (index, file) ->
                val (path, folderName) = file
                Observable.defer {
                    val bitmap = rotateIfNeeded(path, buildResizedBitmap(path, desiredWidth, desiredHeight))

                    if (bitmap == null) {
                        Observable.just(PictureResult.BitmapOperationFailure)
                            .doOnNext { requestEmitter.accept(index + 1) }
                    } else {
                        Single.timer(Constants.DURATION_SEC, TimeUnit.SECONDS, Schedulers.io())
                            .doOnSuccess{ requestEmitter.accept(index + 1) }
                            .ignoreElement()
                            .toObservable<PictureResult>()
                            .startWith(PictureResult.Success(bitmap, folderName))
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
        bitmap ?: return null

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