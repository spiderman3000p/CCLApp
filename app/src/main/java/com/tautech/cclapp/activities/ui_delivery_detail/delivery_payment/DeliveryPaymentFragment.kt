package com.tautech.cclapp.activities.ui_delivery_detail.delivery_payment

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.ManageDeliveryActivity
import com.tautech.cclapp.activities.ManageDeliveryActivityViewModel
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.fragment_delivery_payment.*
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DeliveryPaymentFragment : Fragment() {
  private val TAKE_PHOTO: Int = 5
  private val REQUEST_LOCATION_PERMISSION: Int = 2
  private val REQUEST_CAMERA_PERMISSION: Int = 1
  var photoFile: File? = null
  val TAG = "DELIVERY_PAYMENT_METHOD_FRAGMENT"
  private var retrofitClient: Retrofit? = null
  private var mStateManager: AuthStateManager? = null
  private var spinnerAdapter: ArrayAdapter<String>? = null
  private var paymentMethodsList = mutableListOf<String>()
  private var paymentMethodSelected: PaymentMethod? = null
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    val root = inflater.inflate(R.layout.fragment_delivery_payment, container, false)
    Log.i(TAG, "onCreateView DeliveryFormFragment")
    retrofitClient = CclClient.getInstance()
    mStateManager = AuthStateManager.getInstance(requireContext())
    if (!mStateManager!!.current.isAuthorized) {
      showAlert("Error", "Su sesion ha expirado", this::signOut)
    }
    spinnerAdapter = ArrayAdapter<String>(requireContext(),
      android.R.layout.simple_dropdown_item_1line,
      paymentMethodsList
    )
    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
    val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
    viewModel.showPaymentMethod.observe(viewLifecycleOwner, Observer { show ->
      Log.i(TAG, "show payment method observada: $show")
      if (show == false) {
        showEmptyFormMessage("No se requiere metodo de pago")
      } else {
        hideEmptyFormMessage()
      }
    })
    viewModel.paymentMethods.observe(viewLifecycleOwner, Observer{methods ->
      Log.i(TAG, "metodos de pago observados $methods")
      paymentMethodsList.clear()
      if(!methods.isNullOrEmpty()) {
        paymentMethodsList.addAll(methods.map {
          it.description!!
        }.toMutableList())
        paymentMethodSelected = methods.get(0)
      }
      spinnerAdapter?.notifyDataSetChanged()
    })
    viewModel.selectedPaymentMethod.observe(viewLifecycleOwner, Observer{selectedPaymentMethod ->
      Log.i(TAG, "metodo de pago seleccionado $selectedPaymentMethod")
      paymentMethodSelected = selectedPaymentMethod
      redrawForm()
    })
    photoBtn.setOnClickListener {
      val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
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
                  showAlert(getString(R.string.important),
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
    paymentMethodSp?.adapter = spinnerAdapter
    paymentMethodSp?.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selectedPaymentMethod = viewModel.paymentMethods.value?.find {
          paymentMethodsList.get(position) == it.description
        }
        viewModel.selectedPaymentMethod.setValue(selectedPaymentMethod)
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {
      }

    }
    redrawForm()
  }

  private fun redrawForm(){
    Log.i(TAG, "drawing payment form...")
    if(paymentMethodSelected != null){
      if(paymentMethodSelected?.requiresPhoto == true){
        photoContainer.visibility = View.VISIBLE
        photoBtn.visibility = View.VISIBLE
      } else {
        photoContainer.visibility = View.GONE
        photoBtn.visibility = View.GONE
      }
      if(paymentMethodSelected?.requiresTransactionNumber == true){
        transactionNumberContainer.visibility = View.VISIBLE
      } else {
        transactionNumberContainer.visibility = View.GONE
      }
    } else {
      transactionNumberContainer.visibility = View.GONE
      photoContainer.visibility = View.GONE
      photoBtn.visibility = View.GONE
    }
  }

  private fun showEmptyFormMessage(message: String) {
    activity?.runOnUiThread{
      formSv.visibility = View.GONE
      message2Tv.text = message
      message2Tv.visibility = View.VISIBLE
    }
  }

  private fun hideEmptyFormMessage() {
    activity?.runOnUiThread{
      message2Tv.visibility = View.GONE
      formSv.visibility = View.VISIBLE
    }
  }

  private fun showForm() {
    Log.i(TAG, "mostrando formulario")
    activity?.runOnUiThread{
      message2Tv.visibility = View.GONE
      formSv.visibility = View.VISIBLE
    }
  }

  private fun hideForm() {
    Log.i(TAG, "ocultando formulario")
    activity?.runOnUiThread{
      formSv.visibility = View.GONE
    }
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
        Log.i(TAG, "obteniendo foto seleccionada")
        try {
          Log.i(TAG, "photo file: ${photoFile?.absolutePath} before compression ${photoFile?.length() ?: 0 / 1024}kb")
          val byteArray = photoFile?.readBytes()
          if (byteArray != null) {
            val bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            if (bmp != null && photoFile != null) {
              photoFile.also {
                try {
                  FileOutputStream(it).use { out ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                      bmp.compress(Bitmap.CompressFormat.WEBP_LOSSLESS,
                        100,
                        out)
                    } else {
                      bmp.compress(Bitmap.CompressFormat.PNG,
                        100,
                        out)
                    }
                    photoIv.setImageBitmap(bmp)
                    val photoURI: Uri = FileProvider.getUriForFile(
                      requireContext(),
                      "com.tautech.cclapp.fileprovider",
                      it!!
                    )
                    photoIv.setOnClickListener {
                        val intent = Intent()
                        intent.action = Intent.ACTION_VIEW
                        intent.setDataAndType(photoURI,
                          "image/*")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent)
                      }
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
        } catch (e: Exception) {
          Log.e(TAG, "Excepcion:", e)
        }
      }
    }
  }

  fun isValidForm(): Boolean {
    if (paymentMethodSelected == null) {
      paymentMethodLabelTv?.error = "Este campo es obligatorio"
      Toast.makeText(requireContext(), "Metodo de pago es obligatorio", Toast.LENGTH_SHORT).show()
      return false
    } else {
      paymentMethodLabelTv?.error = null
    }
    val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
    if(amountEt.text.isEmpty() || (amountEt.text.isNotEmpty() && amountEt.text.toString().toDouble() > viewModel.delivery.value?.totalValue ?: 0.00)){
      amountEt?.error = "El monto no debe ser superior a ${viewModel.delivery.value?.totalValue}"
      Toast.makeText(requireContext(), "Monto de pago invalido", Toast.LENGTH_SHORT).show()
      return false
    } else {
      amountEt.error = null
    }
    val deliveredAmount = viewModel.deliveryLines.value?.sumOf {
      if (it.delivered > 0){
        it.price
      } else {
        0.00
      }
    } ?: 0.00
    if(amountEt.text.isNotEmpty() && amountEt.text.toString().toDouble() < deliveredAmount && amountEt.text.toString().toDouble() > deliveredAmount){
      amountEt?.error = "El monto debe ser igual a $deliveredAmount"
      Toast.makeText(requireContext(), "Monto de pago invalido", Toast.LENGTH_SHORT).show()
      return false
    } else {
      amountEt.error = null
    }
    if(paymentMethodSelected?.requiresPhoto == true){
      if(photoFile == null){
        photoLabelTv?.error = "Este campo es obligatorio"
        Toast.makeText(requireContext(), "Foto es obligatoria", Toast.LENGTH_SHORT).show()
        return false
      } else {
        photoLabelTv?.error = null
      }
    }
    if(paymentMethodSelected?.requiresTransactionNumber == true){
      if(transactionNumberEt?.text.isNullOrEmpty()){
        transactionNumberEt?.error = "Este campo es obligatorio"
        Toast.makeText(requireContext(), "Numero de transaccion es obligatorio", Toast.LENGTH_SHORT).show()
        return false
      } else {
        transactionNumberEt?.error = null
      }
    } else {
      transactionNumberEt?.error = null
    }
    return true
  }

  fun generateDeliveryPaymentDetail(): DeliveryPaymentDetail?{
    var paymentDetail: DeliveryPaymentDetail? = null
    if (isValidForm()){
      val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
      paymentDetail = DeliveryPaymentDetail()
      paymentDetail.amount = amountEt.text.toString().toDouble()
      paymentDetail.deliveryId = viewModel.delivery.value?.deliveryId
      paymentDetail.notes = notesEt.text.toString()
      paymentDetail.transactionNumber = transactionNumberEt.text.toString()
      val selectedPaymentMethod = viewModel.paymentMethods.value?.find {
        paymentMethodSp?.selectedItem as String == it.description
      }
      paymentDetail.paymentMethod = "${CclClient.BASE_URL}paymentMethods/${selectedPaymentMethod?.id}"
    } else {
      Toast.makeText(requireContext(), "Formulario de pago invalido", Toast.LENGTH_SHORT).show()
    }
    return paymentDetail
  }

  private fun signOut() {
    mStateManager?.signOut(requireContext())
    //activity?.finish()
  }

  fun showAlert(title: String, message: String) {
    activity?.runOnUiThread {
      val builder = AlertDialog.Builder(requireContext())
      builder.setTitle(title)
      builder.setMessage(message)
      builder.setPositiveButton("Aceptar", null)
      val dialog: AlertDialog = builder.create();
      if(activity?.isDestroyed == false && activity?.isFinishing == false) {
        dialog.show()
      }
    }
  }

  fun showAlert(title: String, message: String, positiveCallback: (() -> Unit)? = null, negativeCallback: (() -> Unit)? = null) {
    val builder = AlertDialog.Builder(requireContext())
    builder.setTitle(title)
    builder.setMessage(message)
    builder.setPositiveButton("Aceptar", DialogInterface.OnClickListener{ dialog, id ->
      if (positiveCallback != null) {
        positiveCallback()
      }
      dialog.dismiss()
    })
    builder.setNegativeButton("Cancelar", DialogInterface.OnClickListener{ dialog, id ->
      if (negativeCallback != null) {
        negativeCallback()
      }
      dialog.dismiss()
    })
    if(activity?.isDestroyed == false && activity?.isFinishing == false) {
      val dialog: AlertDialog = builder.create()
      dialog.show()
    }
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
              showAlert(getString(R.string.important),
                  getString(R.string.should_show_rationale_camera_permission_message))
            }
        }
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
          if (grantResults.any {
              it == PackageManager.PERMISSION_DENIED
            }){
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
              showAlert(getString(R.string.important),
                getString(R.string.should_show_rationale_location_permission_message))
            }
          }
        }
    }

  companion object{
    var mInstance: DeliveryPaymentFragment? = null
    fun getInstance(): DeliveryPaymentFragment?{
      if(mInstance == null){
        Log.i("DELIVERY_PAYMENT_METHOD_FRAGMENT", "instancia de DeliveryPaymentFragment es null, creando una nueva")
        mInstance = DeliveryPaymentFragment()
      }
      return mInstance
    }
  }
}