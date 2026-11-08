package com.p2pacademy.domain

/** Результат операции над состоянием игры. */
data class Res(val state: GameState, val msg: String, val ok: Boolean = true, val tradeId: Int? = null)
