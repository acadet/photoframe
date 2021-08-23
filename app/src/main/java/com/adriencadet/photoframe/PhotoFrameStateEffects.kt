package com.adriencadet.photoframe

import android.content.Context
import com.adriencadet.photoframe.statemachine.*
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import io.reactivex.rxkotlin.ofType

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
                                Constants.DURATION_SEC,
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
                        is StartSlideshow -> startSlideShow()
                        is TappedSlideshow -> toggleSlideShowState(currentState)
                        else -> Observable.empty()
                    }
                }

        private fun startSlideShow() =
            Observable
                .timer(Constants.INIT_DURATION_SEC, TimeUnit.SECONDS, Schedulers.io())
                .map { IsRunningChanged(true) }

        private fun toggleSlideShowState(currentState: () -> PhotoFrameState) =
            when {
                currentState().isRunning -> {
                    Observable.timer(
                        Constants.MAX_PAUSE_DURATION_SEC,
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
    }

    inner class PictureStream: ActionWithStateEffect<PhotoFrameState> {
        override fun run(actions: Observable<out Action>, currentState: () -> PhotoFrameState) =
            Observable
                .defer {
                val state = currentState()

                pictureService.observeImages(context, state.desiredWidth, state.desiredHeight)
            }
                .map { NewPicture(it) }
    }
}