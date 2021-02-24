package com.tautech.cclapp.models

import android.util.Log
import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import org.json.JSONArray
import org.json.JSONException
import java.io.Serializable

enum class FieldType(val value: String){
    Boolean("Boolean"), Text("Text"), Integer("Integer"), Decimal("Decimal"), Date("Date"), File("File"), Location("Location")
}
enum class FieldSubType(val value: String){
    Photo("Photo"), Document("Document"), Signature("Signature")
}
enum class ControlType(val value: String){
    Input("Input"), Select("Select"), Toggle("Toggle"), Checkbox("Checkbox"), RadioGroup("RadioGroup"), File("File"), Location("Location"), Date("Date");
}
@Entity(indices = [
    Index(value = ["formDefinitionId"], name = "formDefinitionIndex", unique = false)
], foreignKeys = [ForeignKey(entity = StateFormDefinition::class,
    parentColumns = ["id"],
    childColumns = ["formDefinitionId"],
    onDelete = CASCADE,
    onUpdate = CASCADE)]
)
data class StateFormField(
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: Long? = null,
    @ColumnInfo(name = "formDefinitionId")
    var formDefinitionId: Int? = null,
    @ColumnInfo(name = "name")
    var name: String? = "",
    @ColumnInfo(name = "controlType")
    var controlType: String? = null,
    @ColumnInfo(name = "type")
    var type: String? = null,
    @ColumnInfo(name = "subtype")
    var subtype: String? = null,
    @ColumnInfo(name = "label")
    var label: String? = "",
    @ColumnInfo(name = "max")
    var max: Int? = null,
    @ColumnInfo(name = "min")
    var min: Int? = null,
    @ColumnInfo(name = "regex")
    var regex: String? = null,
    @ColumnInfo(name = "invalidRegexMsg")
    var invalidRegexMsg: String? = null,
    @ColumnInfo(name = "regexHint")
    var regexHint: String? = null,
    @ColumnInfo(name = "required")
    var required: Boolean? = null,
    @ColumnInfo(name = "items")
    var items: String? = null,
    @Ignore
    var itemList: JSONArray? = null): Serializable {
    init{
        initItemList()
    }
    fun initItemList(){
        if (!items.isNullOrEmpty()) {
            try {
                itemList = JSONArray(items)
            } catch(ex: JSONException){
                Log.e("StateFormField", "error al convertir string de items a array", ex)
            } catch(ex: JsonIOException){
                Log.e("StateFormField", "error al convertir string de items a array", ex)
            } catch(ex: JsonParseException){
                Log.e("StateFormField", "error al convertir string de items a array", ex)
            } catch(ex: JsonSyntaxException){
                Log.e("StateFormField", "error al convertir string de items a array", ex)
            }
        }
    }
}