package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.StateFormField

@Dao
interface StateFormFieldDao {
    @Query("SELECT * FROM stateformfield")
    fun getAll(): List<StateFormField>

    @Query("SELECT * FROM stateformfield WHERE formDefinitionId = CAST(:stateFormDefinitionId AS NUMERIC)")
    fun getAllByDefinition(stateFormDefinitionId: Long): List<StateFormField>

    @Query("SELECT * FROM stateformfield WHERE id IN (:ids)")
    fun loadAllByIds(ids: LongArray): List<StateFormField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(stateFormField: StateFormField)

    @Update()
    fun update(stateFormField: StateFormField)

    @Delete
    fun delete(stateFormField: StateFormField)

    @Query("DELETE FROM stateformfield WHERE formDefinitionId IN (:formDefinitionIds)")
    fun deleteAllByFormDefinition(formDefinitionIds: List<Long>)

    @Query("DELETE FROM stateformfield")
    fun deleteAll()

    @Query("SELECT * FROM stateformfield WHERE id = :id")
    fun getById(id: Long?): StateFormField

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(allFields: List<StateFormField>)
}