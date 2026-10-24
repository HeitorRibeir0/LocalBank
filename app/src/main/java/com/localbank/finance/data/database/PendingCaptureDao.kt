package com.localbank.finance.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localbank.finance.data.model.PendingCapture
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingCaptureDao {

    /** IGNORE: a mesma compra notificada duas vezes não vira dois itens (fingerprint é único). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(capture: PendingCapture): Long

    @Query("SELECT * FROM pending_captures ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<PendingCapture>>

    @Query("DELETE FROM pending_captures WHERE id = :id")
    suspend fun delete(id: String)
}
