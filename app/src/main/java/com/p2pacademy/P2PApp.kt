package com.p2pacademy

import android.app.Application
import androidx.room.Room
import com.p2pacademy.data.GameRepository
import com.p2pacademy.data.RoomGameRepository
import com.p2pacademy.database.AppDatabase

class P2PApp : Application() {
    lateinit var repository: GameRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "p2p_academy.db").build()
        repository = RoomGameRepository(db.saveDao())
    }
}
