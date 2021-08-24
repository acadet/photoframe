package com.adriencadet.photoframe

import android.content.Context
import com.adriencadet.photoframe.statemachine.*
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class PhotoFrameStateEffects(
    private val context: Context,
    private val pictureService: PictureService
) {

    fun effects(): List<Effect<PhotoFrameState>> {
        return listOf(
            SlideshowTimer(),
            SlideshowLifecycle(),
            PictureFetcher(),
            PictureStream()
        )
    }

    inner class SlideshowTimer : StateEffect<PhotoFrameState> {
        override fun run(state: Observable<PhotoFrameState>) =
            state
                .map { it.isRunning }
                .distinctUntilChanged()
                .switchMap { isRunning ->
                    when {
                        isRunning -> {
                            Observable.interval(
                                0,
                                Constants.PHOTO_DURATION_SEC,
                                TimeUnit.SECONDS,
                                Schedulers.io()
                            )
                                .map { NextPicture }
                        }
                        else -> Observable.empty()
                    }
                }
    }

    inner class SlideshowLifecycle : ActionWithStateEffect<PhotoFrameState> {
        override fun run(actions: Observable<out Action>, currentState: () -> PhotoFrameState) =
            actions
                .filter { it is StartSlideshow || it is TappedSlideshow }
                .switchMap {
                    when (it) {
                        is StartSlideshow -> Observable.just(IsRunningChanged(true))
                        is TappedSlideshow -> toggleSlideShowState(currentState)
                        else -> Observable.empty()
                    }
                }

        private fun toggleSlideShowState(currentState: () -> PhotoFrameState) =
            when {
                currentState().isRunning -> {
                    Observable.timer(
                        Constants.PAUSE_TIMEOUT_SEC,
                        TimeUnit.SECONDS,
                        Schedulers.io()
                    )
                        .map { IsRunningChanged(true) }
                        .startWith(IsRunningChanged(false))
                }
                else -> Observable.just(IsRunningChanged(true))
            }
    }

    inner class PictureFetcher : ActionEffect<PhotoFrameState> {
        override fun run(actions: Observable<out Action>) =
            actions
                .ofType<NextPicture>()
                .doOnNext {
                    pictureService.requestNextPicture()
                }
                .ignoreElements()
                .toObservable<PhotoFrameAction>()
    }

    inner class PictureStream : ActionEffect<PhotoFrameState> {
        override fun run(actions: Observable<out Action>) =
            actions
                .ofType<StartSlideshow>()
                .switchMap { action ->
                    pictureService
                        .observeImages(
                            context,
                            action.desiredWidth,
                            action.desiredHeight
                        )
                }
                .map { NewPicture(it) }
    }
}