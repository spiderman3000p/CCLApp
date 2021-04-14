package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.StateFormDefinition
import com.tautech.cclapp.models.StateFormField
import java.util.*

@Dao
interface StateFormDefinitionDao {
    @Query("SELECT * FROM stateformdefinition")
    fun getAll(): List<StateFormDefinition>

    @Query("SELECT * FROM stateformdefinition WHERE deliveryState = :state")
    fun getAllByState(state: String): List<StateFormDefinition>

    @Query("SELECT * FROM stateformdefinition WHERE deliveryState = :state AND customerId = CAST(:customerId AS NUMERIC)")
    fun getAllByStateAndCustomer(state: String, customerId: Long): List<StateFormDefinition>

    @Query("SELECT * FROM stateformdefinition WHERE id IN (:ids)")
    fun loadAllByIds(ids: LongArray): List<StateFormDefinition>

    @Query("SELECT * FROM stateformfield WHERE formDefinitionId = CAST(:formDefinitionId AS NUMERIC)")
    fun getFields(formDefinitionId: Long): List<StateFormField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(stateFormDefinition: StateFormDefinition)

    @Update()
    fun update(stateFormDefinition: StateFormDefinition)

    @Delete
    fun delete(stateFormDefinition: StateFormDefinition)

    @Query("DELETE FROM stateformdefinition")
    fun deleteAll()

    @Query("DELETE FROM stateformdefinition WHERE customerId = CAST(:customerId AS NUMERIC)")
    fun deleteAllByCustomer(customerId: Long)

    @Query("SELECT * FROM stateformdefinition WHERE id = CAST(:id AS NUMERIC)")
    fun getById(id: Long?): StateFormDefinition

    @Query("SELECT * FROM stateformdefinition WHERE customerId = CAST(:customerId AS NUMERIC)")
    fun getAllByCustomer(customerId: Long?): List<StateFormDefinition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(definitions: ArrayList<StateFormDefinition>)
}