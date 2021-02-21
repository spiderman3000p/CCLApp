package com.tautech.cclapp.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.UploadFailedCertificationsWorker
import com.tautech.cclapp.classes.UploadSingleCertificationWorker
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.models.CertificationToUpload
import com.tautech.cclapp.models.PendingToUploadCertification
import net.openid.appauth.AuthorizationService
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class MyWorkerManagerService {
    companion object{
        private val TAG = "MY_WORKER_MANAGER_SERVICE"
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
                .enqueueUniqueWork("uploadCertificationsRequest-${certification.deliveryId}-${certification.deliveryLineId}-${certification.index}", ExistingWorkPolicy.KEEP, uploadWorkRequest)
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
                .enqueueUniquePeriodicWork("uploadFailedCertificationsRequest", ExistingPeriodicWorkPolicy.KEEP, uploadWorkRequest)
        }
    }
}