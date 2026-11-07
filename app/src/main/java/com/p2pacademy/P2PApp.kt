package com.p2pacademy

import android.app.Application
import androidx.room.Room
import com.p2pacademy.database.AppDatabase
import com.p2pacademy.wallet.RoomWalletRepository
import com.p2pacademy.wallet.WalletRepository

class P2PApp : Application() {
    lateinit var walletRepository: WalletRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "p2p_academy.db").build()
        walletRepository = RoomWalletRepository(db.walletDao())
    }
}
