package com.adriencadet.photoframe

import android.content.Context
import com.adriencadet.photoframe.statemachine.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.schedulers.Schedulers
import java.util.*
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
            PictureStream(),
            SlideshowScheduler()
        )
    }

    inner class SlideshowTimer : StateEffect<PhotoFrameState> {
        override fun run(state: Observable<PhotoFrameState>) =
            state
                .map { it.isRunning && !it.isPausedForTheNight }
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

    inner class SlideshowScheduler : ActionEffect<PhotoFrameState> {
        override fun run(actions: Observable<out Action>) =
            Observable.merge(observeFirstEvent(actions), observeSubsequentEvents(actions))

        private val Calendar.hourOfDay: Int
            get() = get(Calendar.HOUR_OF_DAY)

        private val Calendar.minutes: Int
            get() = get(Calendar.MINUTE)

        private fun observeFirstEvent(actions: Observable<out Action>) =
            actions
                .ofType<StartSlideshow>()
                .switchMapSingle {
                    Calendar.getInstance().scheduleFirst()
                }

        private fun observeSubsequentEvents(actions: Observable<out Action>) =
            actions
                .ofType<IsPausedForTheNight>()
                .map { it.isPausedForTheNight }
                .distinctUntilChanged()
                .switchMapSingle { isPaused ->
                    when {
                        isPaused -> Calendar.getInstance().scheduleTurnOn()
                        else -> Calendar.getInstance().scheduleTurnOff()
                    }
                }

        private fun Calendar.scheduleFirst(): Single<PhotoFrameAction> {
            return when {
                hourOfDay in Constants.START_TURN_ON_HOUR_OF_DAY until Constants.START_TURN_OFF_HOUR_OF_DAY -> scheduleTurnOff()
                else -> Single.just(IsPausedForTheNight(true))
            }
        }

        private fun Calendar.scheduleTurnOff(): Single<PhotoFrameAction> {
            return Single
                .timer(
                    minutesTo(
                        hourOfDay = Constants.START_TURN_OFF_HOUR_OF_DAY,
                        minutes = Constants.START_TURN_OFF_MINUTES
                    )
                        .toLong(),
                    TimeUnit.MINUTES,
                    Schedulers.io()
                )
                .map { IsPausedForTheNight(true) }
        }

        private fun Calendar.scheduleTurnOn(): Single<PhotoFrameAction> {
            return Single
                .timer(
                    minutesTo(
                        hourOfDay = Constants.START_TURN_ON_HOUR_OF_DAY,
                        minutes = Constants.START_TURN_ON_MINUTES
                    )
                        .toLong(),
                    TimeUnit.MINUTES,
                    Schedulers.io()
                )
                .map { IsPausedForTheNight(false) }
        }

        private fun Calendar.minutesTo(hourOfDay: Int, minutes: Int = 0): Int {
            val hourDiff = (hourOfDay - this.hourOfDay).modulo(24) - 1
            val minutesDiff = (minutes - this.minutes).modulo(60)

            return hourDiff * 60 + minutesDiff
        }

        private fun Int.modulo(other: Int): Int {
            val r = this % other
            return when {
                r <= 0 -> r + other
                else -> r
            }
        }
    }
}