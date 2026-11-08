package com.p2pacademy.data

import com.p2pacademy.database.SaveDao
import com.p2pacademy.database.SaveEntity
import com.p2pacademy.domain.GameState
import kotlinx.serialization.json.Json

interface GameRepository {
    suspend fun load(): GameState?
    suspend fun save(state: GameState)
    suspend fun backup(state: GameState)
    suspend fun loadBackup(): GameState?
    suspend fun reset()
}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeState(s: GameState): String = json.encodeToString(GameState.serializer(), s)
fun decodeState(text: String): GameState = json.decodeFromString(GameState.serializer(), text)

class RoomGameRepository(private val dao: SaveDao) : GameRepository {
    private suspend fun read(slot: String): GameState? =
        dao.get(slot)?.let { runCatching { decodeState(it.json) }.getOrNull() }

    override suspend fun load() = read("main")

    override suspend fun save(state: GameState) {
        dao.put(SaveEntity("main", encodeState(state), System.currentTimeMillis()))
    }

    override suspend fun backup(state: GameState) {
        dao.put(SaveEntity("backup", encodeState(state), System.currentTimeMillis()))
    }

    override suspend fun loadBackup() = read("backup")

    override suspend fun reset() {
        dao.delete("main")
    }
}
