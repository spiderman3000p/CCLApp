package com.tautech.cclapp.activities.legalization

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.*
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivity
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.models.*
import kotlinx.android.synthetic.main.fragment_delivery_payment.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class PaymentValidationDialog(val payment: Payment?, val isEditing: Boolean = false, val paymentType: String? = null): DialogFragment() {
  private val TAKE_PHOTO: Int = 5
  private val REQUEST_CAMERA_PERMISSION: Int = 1
  var photoFile: File? = null
  val TAG = "PAYMENT_VALIDATION_DIALOG"
  private var spinnerAdapter: ArrayAdapter<String>? = null
  private var banksNamesList = mutableListOf<String>()
  private var banksList = mutableListOf<Bank>()
  private var selectedBank: Bank? = null
  private var db: AppDatabase? = null
  lateinit private var viewModel: PlanificationDetailActivityViewModel
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(DialogFragment.STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
    val _viewModel: PlanificationDetailActivityViewModel by activityViewModels()
    viewModel = _viewModel
    db = AppDatabase.getDatabase(requireContext())
  }

  override fun onStart() {
    super.onStart()
    val dialog: Dialog? = dialog
    if (dialog != null) {
      val width = ViewGroup.LayoutParams.MATCH_PARENT
      val height = ViewGroup.LayoutParams.MATCH_PARENT
      dialog.window?.setLayout(width, height)
      dialog.window?.setWindowAnimations(R.style.AppThemeSlide);
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    val root = inflater.inflate(R.layout.fragment_delivery_payment, container, false)
    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as PlanificationDetailActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    (activity as PlanificationDetailActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
    toolbar.setNavigationOnClickListener { v: View? -> dismiss() }
    toolbar.title = getString(R.string.add_cash_validation)
    toolbar.setTitleTextColor(ContextCompat.getColor(requireContext(), R.color.white))
    paymentMethodLabelTv.text = getString(R.string.bank)
    spinnerAdapter = ArrayAdapter<String>(requireContext(),
      android.R.layout.select_dialog_item,
      banksNamesList
    )
    photoBtn.setOnClickListener {
      takePicture()
    }
    doneBtn3.text = getString(R.string.done)
    doneBtn3.setOnClickListener {_ ->
      if (isValidForm()){
        var paymentToSave: Payment?
        payment.let{
          paymentToSave = if(it != null){
            updateDeliveryPaymentDetail()
            payment
          } else {
            Payment(detail = generateDeliveryPaymentDetail(), file = photoFile,
            fileAbsolutePath = photoFile?.absolutePath, fileUri = Uri.fromFile(photoFile),
            fileUriStr = Uri.fromFile(photoFile).toString())
          }
        }
        Log.i(TAG, "payment to save: $paymentToSave")
        viewModel.cashPayments.value?.find {
          it.detail?.code == paymentToSave?.detail?.code
        }.let {
          when {
              it != null -> {
                  it.detail?.amount = paymentToSave?.detail?.amount
                  //it.detail?.paymentMethod = paymentToSave.detail?.paymentMethod
                  it.detail?.bankId = paymentToSave?.detail?.bankId
                  it.file = paymentToSave?.file
                  doAsync {
                    db?.paymentDao()?.update(it)
                  }
              }
              paymentToSave != null -> {
                  viewModel.cashPayments.value?.add(paymentToSave!!)
                  doAsync {
                    db?.paymentDao()?.insert(paymentToSave!!)
                  }
              }
              else -> null
          }
        }
        Log.i(TAG, "payments a enviar: ${viewModel.cashPayments.value}")
        viewModel.cashPayments.postValue(viewModel.cashPayments.value)
        dismiss()
        } else {
        Toast.makeText(requireContext(), "Formulario de pago invalido", Toast.LENGTH_SHORT).show()
      }
    }
    paymentMethodSp?.adapter = spinnerAdapter
    paymentMethodSp?.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        selectedBank = banksList.find {
          banksNamesList[position] == it.name
        }
        if(payment != null){
          payment.detail?.bankId = selectedBank?.id
        }
        redrawForm()
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {
      }
    }
    amountEt.doOnTextChanged { text, start, before, count ->
      try {
        val paymentAmount = if (text.isNullOrEmpty()) 0.0 else text.toString().toDouble()
        Log.i(TAG, "paymentAmount $paymentAmount")
        val totalPayments = (viewModel.cashPayments.value?.fold(0.0, { sum, _payment ->
          // si se trata de una edicion de pago, no lo tomamos en cuenta en el calculo de pagos acumulados
          if (!isEditing || (isEditing && payment?.detail?.code != _payment.detail?.code)) {
            sum + (_payment.detail?.amount ?: 0.0)
          } else {
            0.0
          }
        }) ?: 0.0)
        Log.i(TAG, "total payments $totalPayments")
        val totalToPay = viewModel.paymentDetails.value?.cash?.value ?: 0.0
        Log.i(TAG, "totalToPay $totalToPay")
        val leftAmount = (totalToPay - (totalPayments + paymentAmount))
        Log.i(TAG, "leftAmount $leftAmount")
        if (leftAmount < -0.5) {
          amountEt.error = getString(R.string.amount_error_max,
            CclUtilities.getInstance().round(totalToPay - totalPayments))
        } else {
          amountEt.error = null
        }
      } catch(e: Exception){
        FirebaseCrashlytics.getInstance().recordException(e)
        Log.e(TAG, "Error recibiendo texto")
        e.printStackTrace()
      }
    }
    doAsync {
      db?.bankDao()?.getAll()?.let { allBanks ->
        if (allBanks.isNotEmpty()) {
          banksList.addAll(allBanks.toMutableList())
          banksNamesList.addAll(banksList.map{
            it.name ?: ""
          })
          uiThread {
            spinnerAdapter?.notifyDataSetChanged()
            paymentMethodSp.setSelection(0, true)
          }
        }
      }
    }
  }

  private fun takePicture(){
    Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
      // Ensure that there's a camera activity to handle the intent
      if (activity?.packageManager != null) {
        if (takePictureIntent.resolveActivity(activity?.packageManager!!) != null) {
          Log.i(TAG, "creating image file...")
          // Create the File where the photo should go
          val _photoFile: File? = try {
            createImageFile()
          } catch (ex: IOException) {
            // Error occurred while creating the File
            Toast.makeText(requireContext(), getString(R.string.error_creating_file), Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error al crear imagen de destino", ex)
            null
          }
          // Continue only if the File was successfully created
          if (_photoFile != null) {
            Log.i(TAG, "image file created")
            photoFile = _photoFile
            val photoURI: Uri = FileProvider.getUriForFile(
              requireContext(),
              "com.tautech.cclapp.fileprovider",
              _photoFile
            )
            Log.i(TAG, "photoFile created: ${_photoFile.absolutePath}")
            Log.i(TAG, "photoFile created uri: ${photoURI}")
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
              if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.important),
                  getString(R.string.should_show_rationale_camera_permission_message))
              } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA),
                  REQUEST_CAMERA_PERMISSION)
              }
            } else {
              startActivityForResult(takePictureIntent, TAKE_PHOTO)
            }
          } else {
            Log.e(TAG, "photoFile was not created")
          }
        } else {
          Log.e(TAG, "No hay provider para la funcion de camara")
        }
      } else {
        Log.e(TAG, "package manager es null")
      }
    }
  }

  private fun redrawForm(){
    Log.i(TAG, "drawing payment form. Payment: $payment")
    photoContainer.visibility = View.VISIBLE
    photoBtn.visibility = View.VISIBLE
    photoFile = payment?.file
    loadPhoto()
    transactionNumberContainer.visibility = View.VISIBLE
    transactionNumberEt.setText(payment?.detail?.transactionNumber ?: "")
    val utilities = CclUtilities.getInstance()
    amountEt.setText(utilities.round(payment?.detail?.amount ?: 0.0))
  }

  @Throws(IOException::class)
  private fun createImageFile(): File {
    // Create an image file name
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    val storageDir: File = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
    val file = File.createTempFile(
      "JPEG_${timeStamp}_", /* prefix */
      ".jpg", /* suffix */
      storageDir /* directory */
    )
    Log.i(TAG, "image file created: ${file.absolutePath}")
    return file
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    // check for the results
    Log.i(TAG, "onActivityResults, requestCode: $requestCode, resultCode: $resultCode")
    if (requestCode == TAKE_PHOTO) {
      if (resultCode == Activity.RESULT_OK) {
        /*val imageBitmap = data?.extras?.get("data") as Bitmap?
        photoIv.setImageBitmap(imageBitmap)*/
        Log.i(TAG, "data extras: ${data?.extras}")
        try {
          Log.i(TAG, "photo file: ${photoFile?.absolutePath} before compression ${photoFile?.length() ?: 0 / 1024}kb")
          loadPhoto()
        } catch (e: Exception) {
          FirebaseCrashlytics.getInstance().recordException(e)
          Log.e(TAG, "Excepcion:", e)
        }
      }
    }
  }

  private fun showLoader(){
    activity?.runOnUiThread {
      photoProgressBar.visibility = View.VISIBLE
    }
  }

  private fun hideLoader(){
    activity?.runOnUiThread {
      photoProgressBar.visibility = View.INVISIBLE
    }
  }

  private fun loadPhoto(){
    doAsync {
      if(photoFile != null) {
        try {
          val photoURI: Uri = FileProvider.getUriForFile(
            requireContext(),
            "com.tautech.cclapp.fileprovider",
            photoFile!!
          )
          uiThread {
            photoIv.setOnClickListener {
              val intent = Intent()
              intent.action = Intent.ACTION_VIEW
              intent.setDataAndType(photoURI,
                "image/*")
              intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
              startActivity(intent)
            }
            setPic()
          }
          Log.i(TAG,
            "photo file: ${photoFile?.absolutePath} after compression ${photoFile?.length() ?: 0 / 1024}kb")
        } catch (e: IOException) {
          Log.e(TAG, "Error ocurred while filling the image file", e)
          e.printStackTrace()
        }
      }
    }
  }

  private fun setPic() {
    // Get the dimensions of the View
    val targetW: Int = photoIv.width
    val targetH: Int = photoIv.height

    val bmOptions = BitmapFactory.Options().apply {
      // Get the dimensions of the bitmap
      inJustDecodeBounds = true

      BitmapFactory.decodeFile(photoFile?.absolutePath, this)

      val photoW: Int = outWidth
      val photoH: Int = outHeight

      // Determine how much to scale down the image
      val scaleFactor: Int = Math.max(1, Math.min(photoW / targetW, photoH / targetH))

      // Decode the image file into a Bitmap sized to fill the View
      inJustDecodeBounds = false
      inSampleSize = scaleFactor
      inPurgeable = true
    }
    BitmapFactory.decodeFile(photoFile?.absolutePath, bmOptions)?.also { bitmap ->
      photoIv.setImageBitmap(bitmap)
    }
  }

  fun isValidForm(): Boolean {
    if (selectedBank == null) {
      paymentMethodLabelTv?.error = getString(R.string.required_field)
      Toast.makeText(requireContext(), getString(R.string.required_field_arg,"Metodo de pago"), Toast.LENGTH_SHORT).show()
      return false
    } else {
      paymentMethodLabelTv?.error = null
    }
    val totalToPay = (viewModel.paymentDetails.value?.cash?.value ?: 0.0)
    if(amountEt.text.isEmpty()) {
      amountEt?.error = getString(R.string.amount_error_empty)
      Toast.makeText(requireContext(), getString(R.string.invalid_amount), Toast.LENGTH_SHORT).show()
      return false
    } else if (amountEt.text.isNotEmpty() && (amountEt.text.toString()
        .toDouble() < 0.0 || (amountEt.text.toString()
        .toDouble() > 0 && (((totalToPay - amountEt.text.toString()
        .toDouble()) < -0.5) || ((amountEt.text.toString()
        .toDouble() - totalToPay) > 0.5))))){
      amountEt?.error = getString(R.string.amount_error)
      Toast.makeText(requireContext(), getString(R.string.amount_error), Toast.LENGTH_SHORT).show()
      return false
    } else {
      amountEt.error = null
    }
    if(transactionNumberEt?.text.isNullOrEmpty()){
      transactionNumberEt?.error = getString(R.string.required_field)
      Toast.makeText(requireContext(), getString(R.string.required_field_arg, "Numero de transaccion"), Toast.LENGTH_SHORT).show()
      return false
    } else {
      transactionNumberEt?.error = null
    }

    if(photoFile == null){
      photoLabelTv?.error = getString(R.string.required_field)
      Toast.makeText(requireContext(), getString(R.string.required_payment_photo), Toast.LENGTH_SHORT).show()
      return false
    } else {
      photoLabelTv?.error = null
    }
    return true
  }

  fun generateDeliveryPaymentDetail(): PlanificationPaymentDetail?{
    val paymentDetail = PlanificationPaymentDetail()
    paymentDetail.amount = amountEt.text.toString().toDouble()
    paymentDetail.transactionNumber = transactionNumberEt.text.toString()
    paymentDetail.bankId = selectedBank?.id
    paymentDetail.paymentMethod = paymentType
    paymentDetail.transactionType = paymentType
    paymentDetail.hasPhoto = true
    return paymentDetail
  }

  fun updateDeliveryPaymentDetail() {
    payment?.detail?.amount = amountEt.text.toString().toDouble()
    payment?.detail?.transactionNumber = transactionNumberEt.text.toString()
    payment?.detail?.bankId = selectedBank?.id
    payment?.detail?.paymentMethod = paymentType
    payment?.detail?.transactionType = paymentType
    payment?.detail?.hasPhoto = true
    payment?.file = photoFile
    payment?.fileAbsolutePath = photoFile?.absolutePath
    payment?.fileUri = Uri.fromFile(photoFile)
    payment?.fileUriStr = Uri.fromFile(photoFile).toString()
  }

  override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.any {
                    it == PackageManager.PERMISSION_DENIED
                }){
              CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.important),
                  getString(R.string.should_show_rationale_camera_permission_message))
            } else {
              takePicture()
            }
        }
    }

  companion object {
    fun display(fragmentManager: FragmentManager, payment: Payment? = null, paymentType: String? = null): PaymentValidationDialog? {
      val dialog = PaymentValidationDialog(payment, payment != null, paymentType)
      dialog.show(fragmentManager, "payment_validation_dialog")
      return dialog
    }
  }
}