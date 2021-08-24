package com.adriencadet.photoframe.statemachine

import android.util.Log
import com.jakewharton.rx.ReplayingShare
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

    private val effectObservables = effects.map {
        it.run(
            actions,
            currentState.distinctUntilChanged(),
            { currentState.value!! })
    }

    private val effectStream = Observable
        .mergeArray(*effectObservables.toTypedArray())
        .subscribeOn(scheduler)
        .observeOn(scheduler)
        .doOnNext {
            Log.d("StateMachine", "Effect emitting ${it.javaClass.name}")
            actions.accept(it)
        }


    val states = Observable
        .merge(
            actions,
            effectStream.ignoreElements().toObservable()
        )
        .observeOn(scheduler)
        .map { action ->
            Log.d("StateMachine", "New state to be reduced further to ${action.javaClass.name}")
            reducer(currentState.value!!, action)
        }
        .doOnNext { currentState.accept(it) }
        .startWith(currentState.value!!)
        .distinctUntilChanged()
        .compose(ReplayingShare.instance())


    fun dispatch(action: Action) {
        Log.d("StateMachine", "Dispatching ${action.javaClass.name}")
        actions.accept(action)
    }
}