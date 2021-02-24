package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.StateFormField

@Dao
interface StateFormFieldDao {
    @Query("SELECT * FROM stateformfield")
    fun getAll(): List<StateFormField>

    @Query("SELECT * FROM stateformfield WHERE formDefinitionId = CAST(:stateFormDefinitionId AS NUMERIC)")
    fun getAllByDefinition(stateFormDefinitionId: Int): List<StateFormField>

    @Query("SELECT * FROM stateformfield WHERE id IN (:ids)")
    fun loadAllByIds(ids: IntArray): List<StateFormField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(stateFormField: StateFormField)

    @Update()
    fun update(stateFormField: StateFormField)

    @Delete
    fun delete(stateFormField: StateFormField)

    @Query("SELECT * FROM stateformfield WHERE id = :id")
    fun getById(id: Long?): StateFormField

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(allFields: List<StateFormField>)
}