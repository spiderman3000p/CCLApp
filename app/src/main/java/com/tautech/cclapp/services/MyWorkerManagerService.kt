package com.tautech.cclapp.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.*
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.tautech.cclapp.classes.*
import com.tautech.cclapp.models.Item
import com.tautech.cclapp.models.Payment
import com.tautech.cclapp.models.PendingToUploadCertification
import java.io.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KFunction0

class MyWorkerManagerService {
    companion object{
        private val TAG = "MY_WORKER_MANAGER_SERVICE"
        val filesToUpload: MutableMap<String, File> = mutableMapOf()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueUploadSingleCertificationWork(context: Context, certification: PendingToUploadCertification){
            Log.i(TAG, "encolando work para subir certification $certification")
            val data = workDataOf(
                "deliveryId" to certification.deliveryId,
                "planificationId" to certification.planificationId,
                "deliveryLineId" to certification.deliveryLineId,
                "index" to certification.index,
                "quantity" to certification.quantity
            )
            val uploadWorkRequest =
                OneTimeWorkRequestBuilder<UploadSingleCertificationWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadCertificationsRequest-${certification.deliveryId}-${certification.deliveryLineId}-${certification.index}")
                    .setInputData(data)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("uploadCertificationsRequest-${certification.deliveryId}-${certification.deliveryLineId}-${certification.index}", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        }

        fun uploadFailedCertifications(context: Context){
            Log.i(TAG, "iniciando trabajo para buscar subidas de certificaciones fallidas")
            val uploadWorkRequest =
                PeriodicWorkRequestBuilder<UploadFailedCertificationsWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadFailedCertificationsRequest")
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork("uploadFailedCertificationsRequest", ExistingPeriodicWorkPolicy.REPLACE, uploadWorkRequest)
        }

        fun enqueSingleFileUpload(context: Context, item: Item, type: String, savedFormId: Long?, customerId: Long?) {
            Log.i(TAG, "encolando work para subir archivo ${item.name}")
            val fileTag = "uploadFormFile-${savedFormId}-${item.name}"
            // leer bitmap desde el path
            val fullSizeBitmap = BitmapFactory.decodeFile((item.value as File).absolutePath)
            // escalar la imagen
            val reducedImage = ImageResizer.reduceBitmapSize(fullSizeBitmap, ImageResizer.MAX_SIZE)
            filesToUpload[fileTag] = getBitmapFile(reducedImage, context)//item.value as File
            val data = workDataOf(
                "itemName" to item.name,
                "fileTag" to fileTag,
                "savedFormId" to savedFormId,
                "customerId" to customerId,
                "type" to type
            )
            val uploadWorkRequest =
                OneTimeWorkRequestBuilder<UploadFilesWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadFormFile-${savedFormId}-${item.name}")
                    .setInputData(data)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("uploadFormFile-${savedFormId}-${item.name}", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        }

        private fun getBitmapFile(reducedImage: Bitmap, context: Context): File {
            val file = CclUtilities.getInstance().createImageFile(context)
            try {
                file.createNewFile()
                val outputStream = ByteArrayOutputStream()
                reducedImage.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                val bitmapData = outputStream.toByteArray()
                val fileOutputStream = FileOutputStream(file)
                fileOutputStream.write(bitmapData)
                fileOutputStream.flush()
                fileOutputStream.close()
            } catch (ex: Exception){
                FirebaseCrashlytics.getInstance().recordException(ex)
                ex.printStackTrace()
            }
            return file
        }

        fun enqueUploadSinglePaymentWork(context: Context, payment: Payment, deliveryId: Long, planificationId: Long){
            Log.i(TAG, "encolando work para subir payment $payment")
            val data = workDataOf(
                "deliveryId" to deliveryId,
                "code" to payment.detail?.code,
                "amount" to payment.detail?.amount,
                "transactionNumber" to payment.detail?.transactionNumber,
                "paymentMethodId" to payment.detail?.paymentMethodId,
                "hasPhoto" to payment.detail?.hasPhoto
            )
            val uploadWorkRequest =
                OneTimeWorkRequestBuilder<UploadSinglePaymentWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadPaymentsRequest-$deliveryId-${payment.detail?.code}")
                    .setInputData(data)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("uploadPaymentsRequest-$deliveryId-${payment.detail?.code}", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        }

        fun enqueUploadSinglePaymentArrayWork(context: Context, payments: List<Payment>, deliveryId: Long){
            Log.i(TAG, "encolando work para subir payments $payments")
            Log.i(TAG, "deliveryId $deliveryId")
            payments.forEach {payment ->
                payment.file?.let {
                    Log.i(TAG, "encontrado una foto del pago ${payment.detail?.code}")
                    val fileTag = payment.detail?.code ?: ""
                    // leer bitmap desde el path
                    val fullSizeBitmap = BitmapFactory.decodeFile((it).absolutePath)
                    // escalar la imagen
                    val reducedImage = ImageResizer.reduceBitmapSize(fullSizeBitmap, ImageResizer.MAX_SIZE)
                    filesToUpload[fileTag] = getBitmapFile(reducedImage, context)
                    //filesToUpload[fileTag] = it
                }
            }
            val paymentDetailsJSONArray = Gson().toJson(payments.map {
                it.detail!!
            }).toString()
            Log.i(TAG, "paymentDetailsJSONArray $paymentDetailsJSONArray")
            val data = workDataOf(
                "deliveryId" to deliveryId,
                "paymentDetailsJSONArray" to paymentDetailsJSONArray
            )
            val uploadWorkRequest =
                OneTimeWorkRequestBuilder<UploadSinglePaymentArrayWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadPaymentArrayRequest-$deliveryId")
                    .setInputData(data)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("uploadPaymentArrayRequest-$deliveryId", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        }

        fun enquePaymentPhotoUpload(context: Context, code: String, savedPaymentId: Long, customerId: Long) {
            Log.i(TAG, "archivos para ser subidos: $filesToUpload")
            if(filesToUpload[code] == null){
                Log.i(TAG, "el archivo de foto recibido para el pago $code es invalido")
                return
            }
            Log.i(TAG, "encolando work para subir foto del pago $code")
            val data = workDataOf(
                "savedPaymentId" to savedPaymentId,
                "customerId" to customerId,
                "code" to code
            )
            val uploadWorkRequest =
                OneTimeWorkRequestBuilder<UploadPaymentPhotoWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadPaymentPhoto-$code")
                    .setInputData(data)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("uploadPaymentPhoto-$code", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        }

        fun enqueLegalizationPhotoUpload(context: Context, code: String, savedPaymentId: Long) {
            Log.i(TAG, "archivos para ser subidos: $filesToUpload")
            if(filesToUpload[code] == null){
                Log.i(TAG, "el archivo de foto recibido para la legalizacion $code es invalido")
                return
            }
            Log.i(TAG, "encolando work para subir foto de la legalizacion $code")
            val data = workDataOf(
                "savedPaymentId" to savedPaymentId,
                "code" to code
            )
            val uploadWorkRequest =
                OneTimeWorkRequestBuilder<UploadLegalizationPhotoWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadLegalizationPhoto-$code")
                    .setInputData(data)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("uploadLegalizationPhoto-$code", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        }

        fun enqueUploadSingleLegalizationWork(
            context: Context,
            payments: List<Payment>,
            planificationId: Long
        ){
            Log.i(TAG, "encolando work para subir legalizacion de pagos $payments")
            Log.i(TAG, "planificationId $planificationId")
            payments.forEach {payment ->
                payment.file?.let {
                    Log.i(TAG, "encontrado una foto de legalizacion ${payment.detail?.code}")
                    val fileTag = payment.detail?.code ?: ""
                    // leer bitmap desde el path
                    val fullSizeBitmap = BitmapFactory.decodeFile((it).absolutePath)
                    // escalar la imagen
                    val reducedImage = ImageResizer.reduceBitmapSize(fullSizeBitmap, ImageResizer.MAX_SIZE)
                    filesToUpload[fileTag] = getBitmapFile(reducedImage, context)
                    //filesToUpload[fileTag] = it
                }
            }
            val paymentDetailsJSONArray = Gson().toJson(payments.map {
                it.detail!!
            }).toString()
            Log.i(TAG, "paymentDetailsJSONArray $paymentDetailsJSONArray")
            val data = workDataOf(
                "planificationId" to planificationId,
                "paymentDetailsJSONArray" to paymentDetailsJSONArray
            )
            val uploadWorkRequest =
                OneTimeWorkRequestBuilder<UploadSingleLegalizationArrayWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .addTag("uploadLegalizationRequest-$planificationId")
                    .setInputData(data)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("uploadLegalizationRequest-$planificationId", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        }
    }
}