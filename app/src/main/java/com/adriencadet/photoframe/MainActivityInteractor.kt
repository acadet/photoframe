package com.adriencadet.photoframe

import android.content.Context
import com.gojuno.koptional.rxjava2.filterSome
import com.gojuno.koptional.toOptional
import io.reactivex.Observable

class MainActivityInteractor(private val applicationContext: Context) {

    private val stateMachine = PhotoFrameStateMachineFactory.build(applicationContext)
    private val notificationService: NotificationService = NotificationService()

    fun observeIsRunning(): Observable<Pair<Boolean, String>> {
        return stateMachine
            .observe()
            .distinctUntilChanged { it -> it.isRunning }
            .map {
                it.isRunning to it.folderName
            }
    }

    fun observeIsPausedForTheNight(): Observable<Boolean> {
        return stateMachine
            .observe()
            .map { it.isPausedForTheNight }
            .distinctUntilChanged()
            .skipWhile { isPaused -> !isPaused }
    }

    fun observePictureResult(): Observable<PictureResult> {
        return stateMachine
            .observe()
            .map { it.currentPictureResult.toOptional() }
            .distinctUntilChanged()
            .filterSome()
    }

    fun onSlideshowTapped() {
        stateMachine.dispatch(TappedSlideshow)
    }

    fun startSlideshow(desiredWidth: Int, desiredHeight: Int) {
        stateMachine.dispatch(StartSlideshow(desiredWidth, desiredHeight))
    }

    fun startNotification() {
        notificationService.start(applicationContext)
    }

    fun hideNotification() {
        notificationService.stop(applicationContext)
    }

    private val PhotoFrameState.folderName: String
        get() = (currentPictureResult as? PictureResult.Success)?.folderName.orEmpty()
}