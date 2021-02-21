package com.tautech.cclapp.daos

import androidx.room.*
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.PlanificationLine

@Dao
interface DeliveryLineDao {
    @Query("SELECT * FROM deliveryline")
    fun getAll(): List<DeliveryLine>

    @Query("SELECT * FROM deliveryline WHERE deliveryId = CAST(:deliveryId AS NUMERIC)")
    fun getByDelivery(deliveryId: Long): List<DeliveryLine>

    @Query("SELECT * FROM deliveryline where uploaded = 0")
    fun getAllToUpload(): List<DeliveryLine>

    @Query("SELECT * FROM deliveryline WHERE deliveryLineId IN (:deliveryLineIds)")
    fun loadAllByIds(deliveryLineIds: IntArray): List<DeliveryLine>

    @Query("SELECT COUNT(*) > 0 FROM deliveryline WHERE deliveryLineId = CAST(:deliveryLineId AS NUMERIC) AND `index` = CAST(:index AS NUMERIC)")
    fun exists(deliveryLineId: Int, index: Int): Boolean

    @Query("SELECT COUNT(*) FROM deliveryline AS A WHERE planificationId = CAST(:deliveryLineId AS NUMERIC) AND A.deliveryLineId IN (SELECT deliveryLineId FROM certification WHERE planificationId = A.planificationId AND `index` = A.`index` AND deliveryLineId = A.deliveryLineId)")
    fun countCertified(deliveryLineId: Int): Int

    @Query("SELECT * FROM deliveryline WHERE deliveryLineId = CAST(:deliveryLineId AS NUMERIC)")
    fun get(deliveryLineId: Int): DeliveryLine

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(deliveryLine: DeliveryLine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(deliveryLines: MutableList<DeliveryLine>)

    @Update
    fun update(deliveryLine: DeliveryLine)

    @Delete
    fun delete(deliveryLine: DeliveryLine)

    @Query("DELETE FROM deliveryLine")
    fun deleteAll()

    @Query("SELECT * FROM deliveryline WHERE planificationId = CAST(:planificationId AS NUMERIC)")
    fun getAllByPlanification(planificationId: Int): List<DeliveryLine>

    @Query("SELECT A.* FROM deliveryline AS A WHERE A.deliveryLineId = CAST(:deliveryLineId AS NUMERIC) AND A.`index` = CAST(:index AS NUMERIC) AND A.deliveryLineId IN (SELECT deliveryLineId FROM certification WHERE planificationId = A.planificationId AND `index` = CAST(:index AS NUMERIC) AND deliveryLineId = CAST(:deliveryLineId AS NUMERIC))")
    fun hasBeenCertified(deliveryLineId: Int, index: Int?): DeliveryLine

    @Query("SELECT A.* FROM deliveryline AS A WHERE A.planificationId = CAST(:planificationId AS NUMERIC) AND A.deliveryLineId IN (SELECT B.deliveryLineId FROM certification AS B WHERE B.planificationId = CAST(:planificationId AS NUMERIC) AND B.`index` = A.`index` AND B.deliveryLineId = A.deliveryLineId)")
    fun getAllCertifiedByPlanification(planificationId: Int): List<DeliveryLine>

    @Query("SELECT * FROM deliveryline where uploaded = 0 AND planificationId = CAST(:planificationId AS NUMERIC)")
    fun getAllToUploadByPlanification(planificationId: Int): List<DeliveryLine>

    @Query("SELECT A.* FROM deliveryline AS A WHERE A.planificationId = CAST(:planificationId AS NUMERIC) AND A.deliveryLineId NOT IN (SELECT deliveryLineId FROM certification WHERE planificationId = CAST(:planificationId AS NUMERIC) AND `index` = A.`index` AND deliveryLineId = A.deliveryLineId)")
    fun getAllPendingByPlanification(planificationId: Int): List<DeliveryLine>

    @Query("SELECT * FROM deliveryline WHERE deliveryLineId IN (:ids)")
    fun getAllByIds(ids: IntArray): List<DeliveryLine>

    /*@Query("UPDATE deliveryline SET certified=1 WHERE deliveryLineId IN (:ids)")
    fun setCertifiedWhereIn(ids: List<Int>): Int*/
/*
    @Query("SELECT * FROM deliveryline WHERE planificationId = CAST(:planificationId AS NUMERIC) AND certified = 1")
    suspend fun getAllCertifiedByPlanificationLiveData(planificationId: Int): LiveData<List<deliveryline>>
 */
}