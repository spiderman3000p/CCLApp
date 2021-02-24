package com.tautech.cclapp.activities.ui_national.scan

import android.content.Context
import android.content.DialogInterface
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKManager.FEATURE_TYPE
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.barcode.*
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.CertificateActivity
import com.tautech.cclapp.activities.CertificateActivityViewModel
import com.tautech.cclapp.activities.KEY_DRIVER_INFO
import com.tautech.cclapp.activities.KEY_PROFILE_INFO
import com.tautech.cclapp.adapters.DeliveryLineAdapter
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.DeliveryLine
import com.tautech.cclapp.models.Driver
import com.tautech.cclapp.models.KeycloakUser
import com.tautech.cclapp.models.PendingToUploadCertification
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.MyWorkerManagerService
import kotlinx.android.synthetic.main.fragment_scan.*
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationService
import org.jetbrains.anko.doAsync
import org.json.JSONException

class ScanFragment : Fragment(), EMDKManager.EMDKListener, Scanner.StatusListener, Scanner.DataListener {
    val TAG = "SCAN_FRAGMENT"
    // Variables to hold EMDK related objects
    private var emdkManager: EMDKManager? = null
    private var scanSuccessBeep: MediaPlayer? = null
    private var scanFailBeep: MediaPlayer? = null
    private var scanErrorBeep: MediaPlayer? = null
    private var scanExistsBeep: MediaPlayer? = null
    private var scanTriggerBeep: MediaPlayer? = null
    private var barcodeManager: BarcodeManager? = null
    private var scanner: Scanner? = null
    // Variables to hold handlers of UI controls
    private val viewModel: CertificateActivityViewModel by activityViewModels()
    private var certificatedLinesShort: MutableList<DeliveryLine> = mutableListOf()
    private var mAdapter: DeliveryLineAdapter? = null
    private var mStateManager: AuthStateManager? = null
    private var db: AppDatabase? = null
    var dataService: CclDataService? = null
    //var scannedCounter: Int = 0
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val root = inflater.inflate(R.layout.fragment_scan, container, false)
        Log.i(TAG, "on create view...")
        // TODO obtener planificacion id de shared preferences y de la BD
        db = AppDatabase.getDatabase(requireContext())
        dataService = CclClient.getInstance()?.create(
            CclDataService::class.java)
        mStateManager = AuthStateManager.getInstance(requireContext())
        Log.i(TAG, "Restoring state...")
        val sharedPref = activity?.getSharedPreferences(activity?.packageName, Context.MODE_PRIVATE)
        scanSuccessBeep = MediaPlayer.create(context, R.raw.success)
        scanFailBeep = MediaPlayer.create(context, R.raw.fail)
        scanTriggerBeep = MediaPlayer.create(context, R.raw.trigger)
        scanExistsBeep = MediaPlayer.create(context, R.raw.exists)
        scanErrorBeep = MediaPlayer.create(context, R.raw.error)
        //Log.i(TAG, "loaded user info: ${mStateManager?.userInfo}")
        Log.i(TAG, "loaded user profile: ${mStateManager?.keycloakUser}")
        Log.i(TAG, "loaded driver info: ${mStateManager?.driverInfo}")
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "on view created...")
        (activity as CertificateActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as CertificateActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
        initUI()
        overlayTv.visibility = View.GONE
        viewModel.planification.observe(viewLifecycleOwner, Observer{planification ->
            when(planification.state) {
                "OnGoing" -> {
                    overlayTv.text = getString(R.string.route_started)
                    overlayTv.visibility = View.VISIBLE
                    triggerBtn.isEnabled = false
                    barcodeEt.isEnabled = false
                }
                "Complete" -> {
                    overlayTv.text = getString(R.string.route_completed)
                    overlayTv.visibility = View.VISIBLE
                    triggerBtn.isEnabled = false
                    barcodeEt.isEnabled = false
                }
                "Cancelled" -> {
                    overlayTv.text = getString(R.string.route_cancelled)
                    overlayTv.visibility = View.VISIBLE
                    triggerBtn.isEnabled = false
                    barcodeEt.isEnabled = false
                } else -> {
                    overlayTv.text = ""
                    overlayTv.visibility = View.GONE
                }
            }
            updateCounters()
        })
        viewModel.certifiedDeliveryLines.observe(viewLifecycleOwner, Observer{certifiedDeliveryLines ->
            Log.i(TAG, "actualizando certifiedLinesShort con ${certifiedDeliveryLines}")
            certificatedLinesShort.clear()
            if (certifiedDeliveryLines.size > 5) {
                certificatedLinesShort.addAll(certifiedDeliveryLines.subList(0, 5))
            } else {
                certificatedLinesShort.addAll(certifiedDeliveryLines)
            }
            mAdapter?.notifyDataSetChanged()
            updateCounters()
        })
        triggerBtn.setOnClickListener{_ ->
            if (barcodeEt.text.isNotEmpty()) {
                doAsync {
                    searchData(barcodeEt.text.toString())
                }
            }
        }
        initEMDK()
    }

    fun initEMDK() {
        try {
            // Requests the EMDKManager object. This is an asynchronous call and should be called from the main thread.
            // The callback also will receive in the main thread without blocking it until the EMDK resources are ready.
            val results = EMDKManager.getEMDKManager(this.requireContext(), this)
            // Check the return status of getEMDKManager() and update the status TextView accordingly.
            if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
                updateStatus("Barcode request failed!")
            } else {
                updateStatus("Barcode reader initialization is in progress...")
            }
        }catch (e: Exception) {
            //updateStatus("Error loading EMDK Manager")
        }
    }

    private fun showSnackbar(message: String) {
        activity?.runOnUiThread {
            constraintLayout?.let {
                Snackbar.make(it,
                    message,
                    Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun updateCounters() {
        activity?.runOnUiThread {
                pendingTv?.text =
                    ((viewModel.planification.value?.totalUnits ?: 0) - (viewModel.certifiedDeliveryLines.value?.size
                        ?: 0)).toString()
                certifiedTv?.text = (viewModel.certifiedDeliveryLines.value?.size ?: 0).toString()
        }
    }

    private fun initUI() {
        updateCounters()
        activity?.runOnUiThread {
            val rv = activity?.findViewById<RecyclerView>(R.id.reciclerView)
            mAdapter = DeliveryLineAdapter(certificatedLinesShort, this.requireContext())
            rv?.layoutManager = LinearLayoutManager(this.requireContext())
            rv?.adapter = mAdapter
        }
        barcodeEt?.setOnEditorActionListener {v, actionId, event ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    triggerBtn?.callOnClick()
                    true
                } else -> false
            }
        }
        barcodeEt?.setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                //Perform Code
                triggerBtn?.callOnClick()
            }
            false
        })
    }

    private fun initBarcodeManager() {
        // Get the feature object such as BarcodeManager object for accessing the feature.
        barcodeManager = emdkManager?.getInstance(FEATURE_TYPE.BARCODE) as BarcodeManager
        // Add external scanner connection listener.
        if (barcodeManager == null) {
            Toast.makeText(this.context, "Barcode scanning is not supported.", Toast.LENGTH_LONG).show()
        }
    }

    private fun initScanner() {
        if (scanner == null) {
            // Get default scanner defined on the device
            scanner = barcodeManager?.getDevice(BarcodeManager.DeviceIdentifier.DEFAULT)
            if (scanner != null) {
                // Implement the DataListener interface and pass the pointer of this object to get the data callbacks.
                scanner?.addDataListener(this)
                // Implement the StatusListener interface and pass the pointer of this object to get the status callbacks.
                scanner?.addStatusListener(this)
                // Hard trigger. When this mode is set, the user has to manually
                // press the trigger on the device after issuing the read call.
                // NOTE: For devices without a hard trigger, use TriggerType.SOFT_ALWAYS.
                scanner?.triggerType = Scanner.TriggerType.HARD
                try {
                    // Enable the scanner
                    // NOTE: After calling enable(), wait for IDLE status before calling other scanner APIs
                    // such as setConfig() or read().
                    scanner?.enable()
                } catch (e: ScannerException) {
                    updateStatus(e.message)
                    deInitScanner()
                }
            } else {
                updateStatus("Failed to initialize the scanner device.")
            }
        }
    }

    private fun deInitScanner() {
        if (scanner != null) {
            try {
                // Release the scanner
                scanner?.release()
            } catch (e: Exception) {
                updateStatus(e.message)
            }
            scanner = null
        }
    }

    override fun onOpened(_emdkManager: EMDKManager?) {
        // Get a reference to EMDKManager
        emdkManager =  _emdkManager;
        // Get a  reference to the BarcodeManager feature object
        initBarcodeManager();
        // Initialize the scanner
        initScanner();
    }

    override fun onClosed() {
        // The EMDK closed unexpectedly. Release all the resources.
        emdkManager?.release();
        emdkManager = null;
        updateStatus("EMDK closed unexpectedly! Please close and restart the application.");
    }

    override fun onStatus(statusData: StatusData?): Unit {
        // The status will be returned on multiple cases. Check the state and take the action.
        // Get the current state of scanner in background
        val state: StatusData.ScannerStates = statusData?.state ?: StatusData.ScannerStates.IDLE
        var statusStr: String = ""
        // Different states of Scanner
        when (state) {
            StatusData.ScannerStates.IDLE -> {
                // Scanner is idle and ready to change configuration and submit read.
                statusStr = statusData?.friendlyName + " is   enabled and idle..."
                // Change scanner configuration. This should be done while the scanner is in IDLE state.
                setConfig()
                try {
                    // Starts an asynchronous Scan. The method will NOT turn ON the scanner beam,
                    //but puts it in a  state in which the scanner can be turned on automatically or by pressing a hardware trigger.
                    scanner?.read()
                } catch (e: ScannerException) {
                    updateStatus(e.message);
                }
            }
            StatusData.ScannerStates.WAITING -> {
                // Scanner is waiting for trigger press to scan...
                statusStr = "Scanner is waiting for trigger press..."
            }
            StatusData.ScannerStates.SCANNING -> {
                // Scanning is in progress...
                statusStr = "Scanning..."
            }
            StatusData.ScannerStates.DISABLED -> {
                // Scanner is disabled
                statusStr = statusData?.friendlyName + " is disabled."
            }
            StatusData.ScannerStates.ERROR -> {
                // Error has occurred during scanning
                statusStr = "An error has occurred."
            }
        }
        // Updates TextView with scanner state on UI thread.
        updateStatus(statusStr);
    }

    override fun onData(scanDataCollection: ScanDataCollection?) {
        // The ScanDataCollection object gives scanning result and the collection of ScanData. Check the data and its status.
        var dataStr: String = ""
        var barcodeData: String
        if ((scanDataCollection != null) && (scanDataCollection.result == ScannerResults.SUCCESS)) {
            val scanData: ArrayList<ScanDataCollection.ScanData> =  scanDataCollection.scanData;
            // Iterate through scanned data and prepare the data.
            for (data: ScanDataCollection.ScanData in  scanData) {
                // Get the scanned dataString
                barcodeData =  data.data;
                Log.i("DATA_LOADED", barcodeData);
                // Get the type of label being scanned
                val labelType: ScanDataCollection.LabelType = data.labelType;
                // Concatenate barcode data and label type
                dataStr = "$barcodeData  $labelType";
            }
            // limpiamos input
            barcodeEt.text.clear()
            // Updates EditText with scanned data and type of label on UI thread.
            doAsync {
                searchData(dataStr)
            }
        }
    }

    fun searchData(barcode: String) {
        // play trigger sound
        /*doAsync {
            scanTriggerBeep?.start()
        }*/
        Log.i(TAG, "barcode readed: $barcode")
        val readedParts: List<String> = barcode.split("-")
        Log.i(TAG, "barcode parts: $readedParts")
        var deliveryLineId: Long = 0
        var deliveryId: Long = 0
        var index: Int = -1
        when (readedParts.size) {
            3 -> {
                deliveryId = readedParts[0].toLong()
                deliveryLineId = readedParts[1].toLong()
                index = readedParts[2].toInt()
            }
            2 -> {
                deliveryId = readedParts[0].toLong()
                deliveryLineId = readedParts[1].toLong()
                index = 0
            }
            1 -> {
                deliveryLineId = readedParts[0].toLong()
                index = 0
            }
            else -> {
                doAsync {
                    scanErrorBeep?.start()
                }
                showSnackbar("Error de lectura")
                return
            }
        }
        Log.i(TAG, "delivery id readed: $deliveryId")
        Log.i(TAG, "delivery line id readed: $deliveryLineId")
        Log.i(TAG, "index readed: $index")
        // primero buscamos si ya fue escaneado
        val exists = db?.deliveryLineDao()?.hasBeenCertified(deliveryLineId.toInt(), index)
        if (exists != null) {
            doAsync {
                scanExistsBeep?.start()
            }
            Log.i(TAG, "delivery line ya se encuentra certificado en la BD local: $exists")
            showSnackbar("Este item ya fue escaneado")
            return
        } else {
            Log.i(TAG, "delivery line no se encuentra certificado en la BD local")
        }
        var foundDeliveryLine: DeliveryLine? = null
        var hasBeenCertified = false
        val howManyCertified = viewModel.certifiedDeliveryLines.value?.count { d ->
            d.deliveryId == deliveryId && d.id == deliveryLineId
        }
        if (deliveryId > 0 && deliveryLineId > 0 && index >= 0) {
            foundDeliveryLine = viewModel.pendingDeliveryLines.value?.find { d ->
                d.deliveryId == deliveryId && d.id == deliveryLineId && d.index == index
            }
            if (foundDeliveryLine == null) {
                hasBeenCertified = (howManyCertified ?: 0) > 0
                if (hasBeenCertified) {
                    doAsync {
                        scanExistsBeep?.start()
                    }
                    showSnackbar("Este item ya fue escaneado")
                }
            }
        } else if (deliveryLineId > 0) {
            foundDeliveryLine = viewModel.pendingDeliveryLines.value?.find { d ->
                d.id == deliveryLineId
            }
            if (foundDeliveryLine == null) {
                hasBeenCertified = viewModel.certifiedDeliveryLines.value?.count { d ->
                    d.id == deliveryLineId
                }!! > 0
                if (hasBeenCertified) {
                    doAsync {
                        scanExistsBeep?.start()
                    }
                    showSnackbar("Este item ya fue escaneado")
                }
            }
        } else {
            doAsync {
                scanErrorBeep?.start()
            }
            Log.e(TAG, "Error con datos de entrada")
            showSnackbar("Error con datos de entrada")
            return
        }
        if (foundDeliveryLine != null) {
            doAsync {
                scanSuccessBeep?.start()
            }
            foundDeliveryLine.scannedOrder = (howManyCertified ?: 0) + 1
            updateStatus("Codigo Encontrado")
            Log.i(TAG, "$foundDeliveryLine")
            Log.i(TAG, "planificaciones certificadas previas ${viewModel.planification.value?.totalCertified}")
            viewModel.pendingDeliveryLines.value?.remove(foundDeliveryLine)
            viewModel.certifiedDeliveryLines.value?.add(0, foundDeliveryLine)
            viewModel.certifiedDeliveryLines.postValue(viewModel.certifiedDeliveryLines.value)
            //saveOnLocalDatabase(foundDeliveryLine)
            viewModel.planification.value?.totalCertified = (viewModel.planification.value?.totalCertified ?: 0) + 1
            doAsync {
                db?.planificationDao()?.update(viewModel.planification.value!!)
                db?.deliveryLineDao()?.update(foundDeliveryLine)
                val certification = PendingToUploadCertification()
                certification.deliveryId = foundDeliveryLine.deliveryId
                certification.deliveryLineId = foundDeliveryLine.id
                certification.index = foundDeliveryLine.index
                certification.planificationId = foundDeliveryLine.planificationId
                certification.quantity = 1
                MyWorkerManagerService.enqueUploadSingleCertificationWork(requireContext(), certification)
            }
        } else if (!hasBeenCertified) {
            doAsync {
                scanFailBeep?.start()
            }
            updateStatus("Codigo No Encontrado")
        }
    }

    private fun setConfig() {
        try {
            // Get scanner config
            val config = scanner?.config
            // Enable haptic feedback
            if (config?.isParamSupported("config.scanParams.decodeHapticFeedback")!!) {
                config.scanParams.decodeHapticFeedback = true
            }
            // Set scanner config
            scanner?.config = config
        } catch (e: ScannerException) {
            updateStatus(e.message)
        }
    }

    private fun updateStatus(status: String?) {
        Log.i(TAG, status ?: "")
        activity?.runOnUiThread(Runnable() {
            run() {
                // Update the status text view on UI thread with current scanner state
                scannerStatusTv?.text = "$status";
            }
        });
    }

    override fun onDestroyView() {
        super.onDestroyView()
        emdkManager?.release(FEATURE_TYPE.BARCODE)
        emdkManager = null
        scanTriggerBeep?.release()
        scanTriggerBeep = null
        scanFailBeep?.release()
        scanFailBeep = null
        scanSuccessBeep?.release()
        scanSuccessBeep = null
    }
}