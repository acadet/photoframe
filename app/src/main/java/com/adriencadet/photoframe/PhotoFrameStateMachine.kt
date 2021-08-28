package com.adriencadet.photoframe

import com.adriencadet.photoframe.statemachine.StateMachine
import io.reactivex.schedulers.Schedulers

class PhotoFrameStateMachine(
    effects: PhotoFrameStateEffects
) {

    private val stateMachine = StateMachine(
        initialState = PhotoFrameState(
            currentPictureResult = null,
            isRunning = false,
            isPausedForTheNight = false
        ),
        effects = effects.effects(),
        reducer = { state, action ->
            when (action) {
                is PhotoFrameAction -> state.reduce(action)
                else -> state
            }
        },
        scheduler = Schedulers.newThread()
    )

    fun observe() = stateMachine.states

    fun dispatch(action: PhotoFrameAction) = stateMachine.dispatch(action)

    private fun PhotoFrameState.reduce(action: PhotoFrameAction) =
        when (action) {
            is NewPicture -> reduce(action)
            is IsRunningChanged -> reduce(action)
            is IsPausedForTheNight -> reduce(action)

            is StartSlideshow,
            is TappedSlideshow,
            is NextPicture -> this
        }

    private fun PhotoFrameState.reduce(action: NewPicture) =
        copy(currentPictureResult = action.result)

    private fun PhotoFrameState.reduce(action: IsRunningChanged) =
        copy(
            isRunning = action.isRunning
        )

    private fun PhotoFrameState.reduce(action: IsPausedForTheNight) =
        copy(
            isPausedForTheNight = action.isPausedForTheNight
        )
}