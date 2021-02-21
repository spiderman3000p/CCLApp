package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.KeycloakUser

@Dao
interface KeycloakUserDao {
    @Query("SELECT * FROM keycloakuser")
    fun getAll(): List<KeycloakUser>

    @Query("SELECT * FROM keycloakuser WHERE username = :username")
    fun getByUsername(username: String): KeycloakUser

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: KeycloakUser)

    @Update
    fun update(user: KeycloakUser?)

    @Delete
    fun delete(user: KeycloakUser)
}