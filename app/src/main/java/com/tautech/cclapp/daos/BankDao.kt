package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.Bank

@Dao
interface BankDao {
    @Query("SELECT * FROM bank")
    fun getAll(): List<Bank>

    @Query("SELECT * FROM bank WHERE id IN (:bankIds)")
    fun loadAllByIds(bankIds: LongArray): List<Bank>

    @Query("SELECT * FROM bank WHERE id = CAST(:id AS NUMERIC)")
    fun getById(id: Long?): Bank

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bank: Bank)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(banks: ArrayList<Bank>)

    @Update
    fun update(bank: Bank)

    @Delete
    fun delete(bank: Bank)
}