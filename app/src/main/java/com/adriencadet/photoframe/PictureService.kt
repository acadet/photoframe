package com.adriencadet.photoframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers

class PictureService(val fetcher: GalleryPictureFetcher) {

    private val requestRelay = BehaviorRelay.create<Unit>()
    private var shuffledIndexes = emptyList<Int>()

    fun observeImages(
        context: Context,
        desiredWidth: Int,
        desiredHeight: Int
    ): Observable<PictureResult> {
        return fetcher
            .fetchPaths(context)
            .flatMapObservable { result ->
                when (result) {
                    is GalleryPictureFetcherResult.Success -> emitPictures(
                        result.files,
                        desiredWidth,
                        desiredHeight
                    )
                    is GalleryPictureFetcherResult.InvalidCursor -> Observable.just(PictureResult.StorageFailure)
                }
            }
    }

    fun requestNextPicture() = requestRelay.accept(Unit)

    private fun emitPictures(
        pictureFiles: Sequence<PictureFile>,
        desiredWidth: Int,
        desiredHeight: Int
    ): Observable<PictureResult> {
        val count = pictureFiles.count()

        if (count == 0) return Observable.empty()

        shuffledIndexes = (0 until count).shuffled()
        val latestIndexRelay = BehaviorRelay.createDefault(0)

        return requestRelay
            .withLatestFrom(latestIndexRelay)
            .map { (_, index) ->
                val shuffledIndex = shuffledIndexes[index]
                index to pictureFiles.elementAt(shuffledIndex)
            }
            .switchMapSingle { (index, file) ->
                retrievePicture(file, desiredWidth = desiredWidth, desiredHeight = desiredHeight)
                    .doOnSuccess {
                        when {
                            index + 1 == count -> {
                                shuffledIndexes = shuffledIndexes.shuffled()
                                latestIndexRelay.accept(0)
                            }
                            else -> latestIndexRelay.accept(index + 1)
                        }
                    }
            }
    }

    private fun retrievePicture(
        file: PictureFile,
        desiredWidth: Int,
        desiredHeight: Int
    ): Single<PictureResult> {
        return Single
            .fromCallable {
                val (path, folderName) = file

                val bitmap =
                    buildResizedBitmap(path, desiredWidth, desiredHeight)?.rotateIfNeeded(path)

                when (bitmap) {
                    null -> PictureResult.BitmapOperationFailure
                    else -> PictureResult.Success(bitmap, folderName)
                }
            }
            .subscribeOn(Schedulers.io())
    }

    private fun buildResizedBitmap(
        absolutePath: String,
        desiredWidth: Int,
        desiredHeight: Int
    ): Bitmap? {
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

    private fun Bitmap.rotateIfNeeded(absolutePath: String): Bitmap? {
        val exifInterface = ExifInterface(absolutePath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val transformation = getTransformation(orientation)

        return Bitmap.createBitmap(this, 0, 0, width, height, transformation, false)
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