package com.tautech.cclapp.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
@Entity
data class Driver(
    @PrimaryKey
    var id: Long,
    @ColumnInfo(name = "identificationType")
    var identificationType: String?,
    @ColumnInfo(name = "identificationNumber")
    var identificationNumber: Long?,
    @ColumnInfo(name = "name")
    var name: String?,
    @ColumnInfo(name = "email")
    var email: String?,
    @ColumnInfo(name = "phone")
    var phone: Long,
    @ColumnInfo(name = "contactName")
    var contactName: String?,
    @ColumnInfo(name = "contactPhone")
    var contactPhone: Long,
    @ColumnInfo(name = "contactEmail")
    var contactEmail: String?,
    @ColumnInfo(name = "arl")
    var arl: String?,
    @ColumnInfo(name = "eps")
    var eps: String?,
    @ColumnInfo(name = "licenseNumber")
    var licenseNumber: String?,
    @ColumnInfo(name = "contactType")
    var contractType: String?): Serializable