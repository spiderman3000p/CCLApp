package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.UserInformation

@Dao
interface UserInformationDao {
    @Query("SELECT * FROM userinformation")
    fun getAll(): List<UserInformation>

    @Query("SELECT * FROM userinformation WHERE username = :username")
    fun getByUsername(username: String): UserInformation

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: UserInformation)

    @Update
    fun update(user: UserInformation)

    @Delete
    fun delete(user: UserInformation)
}