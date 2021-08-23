package com.adriencadet.photoframe

import com.adriencadet.photoframe.statemachine.StateMachine
import io.reactivex.schedulers.Schedulers

class PhotoFrameStateMachine(
    effects: PhotoFrameStateEffects
) {

    private val stateMachine = StateMachine(
        initialState = PhotoFrameState(
            desiredWidth = 0,
            desiredHeight = 0,
            currentPictureResult = null,
            isRunning = false
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
            is StartSlideshow -> reduce(action)
            is IsRunningChanged -> reduce(action)

            is TappedSlideShow,
            is NextPicture -> this
        }

    private fun PhotoFrameState.reduce(action: NewPicture) =
        copy(currentPictureResult = action.result)

    private fun PhotoFrameState.reduce(action: StartSlideshow) =
        copy(
            desiredWidth = action.desiredWidth,
            desiredHeight = action.desiredHeight
        )

    private fun PhotoFrameState.reduce(action: IsRunningChanged) =
        copy(isRunning = action.isRunning)
}