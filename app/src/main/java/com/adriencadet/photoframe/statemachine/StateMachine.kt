package com.adriencadet.photoframe.statemachine

import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.Scheduler

class StateMachine<TState>(
    initialState: TState,
    effects: List<Effect<TState>>,
    private val reducer: (TState, Action) -> TState,
    scheduler: Scheduler
) {
    private val currentState = BehaviorRelay.createDefault(initialState)
    private val actions = PublishRelay.create<Action>()

    private val effectStream = Observable
        .fromIterable(
            effects.map { it.run(actions, currentState, { currentState.value!! }) }
        )
        .subscribeOn(scheduler)
        .flatMap { it }
        .doOnNext { actions.accept(it) }


    val states = Observable
        .merge(
            actions, effectStream
        )
        .map { action ->
            reducer(currentState.value!!, action)
        }
        .doOnNext { currentState.accept(it) }
        .distinctUntilChanged()


    fun dispatch(action: Action) {
        actions.accept(action)
    }
}