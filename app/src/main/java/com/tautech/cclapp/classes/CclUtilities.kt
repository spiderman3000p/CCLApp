package com.tautech.cclapp.classes

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AlertDialog
import org.jetbrains.anko.runOnUiThread
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CclUtilities {
    val TAG = "UTILITIES_CLASS"
    /**
     *  @author Rafael Cardona
     *  @description Esta funcion formatea los numeros grandes a su version resumida usando K para los miles,
     *  M para los millones, B para los billones y T para los trillones
     */
    fun formatQuantity(value: Double): String {
        val sb = StringBuilder()
        val formatter = Formatter(sb, Locale.US)
        return when {
            value > 999999999999 -> {
                formatter.format("%,.2f", value / 1000000000000).toString() + "T"
            }
            value > 999999999 -> {
                formatter.format("%,.2f", value / 1000000000).toString() + "B"
            }
            value > 999999 -> {
                formatter.format("%,.2f", value / 1000000).toString() + "M"
            }
            value <= 999999 -> {
                formatter.format("%,.2f", value / 1000).toString() + "K"
            }
            value <= 999 -> {
                formatter.format("%,.2f", value).toString()
            }
            else -> {
                "0.0"
            }
        }
    }

    fun round(value: Double): String {
        val sb = StringBuilder()
        val formatter = Formatter(sb, Locale.US)
        return formatter.format("%.2f", value).toString()
    }

    fun formatCurrencyNumber(number: Double): String{
        val format: NumberFormat = NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 2
        format.currency = Currency.getInstance("COP")
        return format.format(number)
    }

    @Throws(IOException::class)
    fun createImageFile(context: Context): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        val file = File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
        Log.i(TAG, "image file created: ${file.absolutePath}")
        return file
    }

    @Throws(IOException::class)
    fun createImageFileSignature(extension: String, context: Context): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "${timeStamp}_", /* prefix */
            ".$extension", /* suffix */
            storageDir /* directory */
        )
    }

    fun showAlert(activity: Activity, title: String, message: String) {
        try {
            activity.runOnUiThread {
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(title)
                builder.setMessage(message)
                builder.setPositiveButton("Aceptar", null)
                val dialog: AlertDialog = builder.create();
                if (!activity.isDestroyed && !activity.isFinishing) {
                    dialog.show();
                }
            }
        }catch(e: Exception){
            e.printStackTrace()
            Log.e(TAG, "Excepcion al intentar mostrar un alerta", e)
        }
    }

    fun showAlert(
        activity: Activity,
        title: String,
        message: String,
        positiveCallback: (() -> Unit)? = null,
        negativeCallback: (() -> Unit)? = null,
    ) {
        try{
            activity.runOnUiThread {
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(title)
                builder.setMessage(message)
                builder.setPositiveButton("Aceptar", DialogInterface.OnClickListener { dialog, id ->
                    if (positiveCallback != null) {
                        positiveCallback()
                    }
                    dialog.dismiss()
                })

                builder.setNegativeButton("Cancelar", DialogInterface.OnClickListener { dialog, id ->
                    if (negativeCallback != null) {
                        negativeCallback()
                    }
                    dialog.dismiss()
                })
                if (!activity.isDestroyed && !activity.isFinishing) {
                    val dialog: AlertDialog = builder.create()
                    dialog.show()
                }
            }
        }catch(e: Exception){
            e.printStackTrace()
            Log.e(TAG, "Excepcion al intentar mostrar un alerta", e)
        }
    }

    companion object{
        private var instance: CclUtilities? = null
        fun getInstance(): CclUtilities{
            if (instance == null){
                instance = CclUtilities()
            }
            return instance!!
        }
    }
}