package com.adriencadet.photoframe.statemachine

import io.reactivex.Observable

interface Effect<TState> {

    fun run(
        actions: Observable<out Action>,
        state: Observable<TState>,
        currentState: () -> TState
    ): Observable<out Action>
}

interface ActionEffect<TState> : Effect<TState> {

    override fun run(
        actions: Observable<out Action>,
        state: Observable<TState>,
        currentState: () -> TState
    ) = run(actions)

    fun run(actions: Observable<out Action>): Observable<out Action>
}

interface StateEffect<TState> : Effect<TState> {

    override fun run(
        actions: Observable<out Action>,
        state: Observable<TState>,
        currentState: () -> TState
    ) = run(state)

    fun run(state: Observable<TState>): Observable<out Action>
}

interface ActionWithStateEffect<TState> : Effect<TState> {

    override fun run(
        actions: Observable<out Action>,
        state: Observable<TState>,
        currentState: () -> TState
    ) = run(actions, currentState)

    fun run(actions: Observable<out Action>, currentState: () -> TState): Observable<out Action>
}