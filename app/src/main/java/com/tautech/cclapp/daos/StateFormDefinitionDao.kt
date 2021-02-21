package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.*

@Dao
interface StateFormDefinitionDao {
    @Query("SELECT * FROM stateformdefinition")
    fun getAll(): List<StateFormDefinition>

    @Query("SELECT * FROM stateformdefinition WHERE deliveryState = :state")
    fun getAllByState(state: String): List<StateFormDefinition>

    @Query("SELECT * FROM stateformdefinition WHERE deliveryState = :state AND customerId = CAST(:customerId AS NUMERIC)")
    fun getAllByStateAndCustomer(state: String, customerId: Int): StateFormDefinition

    @Query("SELECT * FROM stateformdefinition WHERE id IN (:ids)")
    fun loadAllByIds(ids: IntArray): List<StateFormDefinition>

    @Query("SELECT * FROM stateformfield WHERE formDefinitionId = CAST(:formDefinitionId AS NUMERIC)")
    fun getFields(formDefinitionId: Int): List<StateFormField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(stateFormDefinition: StateFormDefinition)

    @Update()
    fun update(stateFormDefinition: StateFormDefinition)

    @Delete
    fun delete(stateFormDefinition: StateFormDefinition)

    @Query("SELECT * FROM stateformdefinition WHERE id = CAST(:id AS NUMERIC)")
    fun getById(id: Long?): StateFormDefinition

    @Query("SELECT * FROM stateformdefinition WHERE customerId = CAST(:customerId AS NUMERIC)")
    fun getAllByCustomer(customerId: Long?): List<StateFormField>
}