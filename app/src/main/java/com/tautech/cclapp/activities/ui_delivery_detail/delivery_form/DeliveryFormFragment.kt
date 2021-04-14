package com.tautech.cclapp.activities.ui_delivery_detail.delivery_form

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.sqlite.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.maps.model.LatLng
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.*
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.classes.DatePickerFragmentDialog
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.fragment_delivery_form.*
import net.openid.appauth.AuthorizationException
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

data class FieldValueContainer(
  val field: StateFormField,
  var fieldValue: Any?,
  var viewId: Int?,
  var dirty: Boolean = false,
)
class DeliveryFormFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {
  private val REQUEST_LOCATION_PERMISSION: Int = 2
  private val REQUEST_CAMERA_PERMISSION: Int = 1
  val TAG = "DELIVERY_FORM_FRAGMENT"
  val createdControls = HashMap<Int, FieldValueContainer>()
  private var retrofitClient: Retrofit? = null
  private var mStateManager: AuthStateManager? = null
  var db: AppDatabase? = null
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    val root = inflater.inflate(R.layout.fragment_delivery_form, container, false)
    Log.i(TAG, "onCreateView DeliveryFormFragment")
    retrofitClient = CclClient.getInstance()
    mStateManager = AuthStateManager.getInstance(requireContext())
    val config = Configuration.getInstance(requireContext())
    try {
      db = AppDatabase.getDatabase(requireContext())
    } catch(ex: SQLiteDatabaseLockedException) {
      Log.e(TAG, "Database error found", ex)
    } catch (ex: SQLiteAccessPermException) {
      Log.e(TAG, "Database error found", ex)
    } catch (ex: SQLiteCantOpenDatabaseException) {
      Log.e(TAG, "Database error found", ex)
    }
    if (config.hasConfigurationChanged()) {
      showAlert("Error", "La configuracion de sesion ha cambiado. Se cerrara su sesion", this::signOut)
    }
    if (!mStateManager!!.current.isAuthorized) {
      showAlert("Error", "Su sesion ha expirado", this::signOut)
    }
    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
    val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
    viewModel.stateFormDefinition.observe(viewLifecycleOwner, Observer { definition ->
      Log.i(TAG, "state form definition observada: $definition")
      Log.i(TAG, "numero de controles dentro del formulario: ${formContainerLayout?.childCount}")
      if (definition != null/* && createdControls.isNullOrEmpty()*/) {
        loadForm(definition, viewModel.delivery.value)/*
      } else if (definition != null && !createdControls.isNullOrEmpty()) {
        showForm()*/
      } else {
        showEmptyFormMessage(getString(R.string.no_state_form_found_arg, viewModel.state.value))
        formContainerLayout?.removeAllViews()
        createdControls.clear()
      }
    })
    viewModel.state.observe(viewLifecycleOwner, Observer {state ->
      if(state != null) {
        loadFormDefinitionFromLocalDB()
      }
    })
    swipeRefresh2.setOnRefreshListener {
      Log.i(TAG, "swipe refresh lister...")
      loadStateFormDefinitions()
    }
  }

  private fun showMessage(message: String) {
    activity?.runOnUiThread{
      /*messageTv3.text = message
      messageTv3.visibility = View.VISIBLE*/
      Toast.makeText(requireContext(),
        message,
        Toast.LENGTH_SHORT).show()
    }
  }

  private fun showEmptyFormMessage(message: String) {
    activity?.runOnUiThread{
      formContainerLayout?.visibility = View.GONE
      messageTv3?.text = message
      messageTv3?.visibility = View.VISIBLE
    }
  }

  private fun hideEmptyFormMessage() {
    activity?.runOnUiThread{
      messageTv3?.visibility = View.GONE
      formContainerLayout?.visibility = View.VISIBLE
    }
  }

  private fun showForm() {
    Log.i(TAG, "mostrando formulario")
    activity?.runOnUiThread{
      messageTv3?.visibility = View.GONE
      formContainerLayout?.visibility = View.VISIBLE
    }
  }

  private fun hideForm() {
    Log.i(TAG, "ocultando formulario")
    activity?.runOnUiThread{
      formContainerLayout?.visibility = View.GONE
    }
  }

  private fun loadForm(stateFormDefinition: StateFormDefinition?, delivery: Delivery?){
    Log.i(TAG, "armando formulario...")
    Log.i(TAG, "state form definition a usar para el estado ${delivery?.deliveryState}: ${stateFormDefinition}")
    formContainerLayout?.removeAllViews()
    if (stateFormDefinition != null && formContainerLayout != null) {
      var counter = formContainerLayout.childCount
      stateFormDefinition.formFieldList?.forEach { field ->
        var preValue: Any? = null
        var viewId: Int? = null
        Log.i(TAG, "armando form control para ${field.name}")
        when (field.controlType) {
          ControlType.File.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.File.value}")
            val viewAux = View.inflate(requireContext(),
              R.layout.file_control_layout,
              null) as ConstraintLayout
            (viewAux.getChildAt(0) as TextView).text = "${field.label} ${if(field.required == true) "*" else "" }"
            (viewAux.getChildAt(1) as TextView).text = ""
            (viewAux.getChildAt(3) as ImageButton).setImageDrawable(ContextCompat.getDrawable(
              requireContext(),
              R.drawable.ic_view_simple___815_))
            (viewAux.getChildAt(3) as ImageButton).imageTintList = ColorStateList.valueOf(
              requireContext().getColor(
                R.color.black))
            (viewAux.getChildAt(2) as ImageButton).imageTintList = ColorStateList.valueOf(
              requireContext().getColor(
                R.color.black))
            var intent: Intent? = null
            var requestCode: Int? = null
            when (field.subtype) {
              FieldSubType.Document.value -> {
                Log.i(TAG, "el subtipo es documento")
                (viewAux.getChildAt(2) as ImageButton).setImageDrawable(ContextCompat.getDrawable(
                  requireContext(),
                  R.drawable.ic_file_pdf___1754_))
                intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                  addCategory(Intent.CATEGORY_OPENABLE)
                  type = "application/pdf"
                }
              }
              FieldSubType.Photo.value -> {
                Log.i(TAG, "el subtipo es foto")
                //intent = Intent(requireContext(), TakePhotoActivity::class.java)
                intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                  // Ensure that there's a camera activity to handle the intent
                  if (activity?.packageManager != null) {
                    if (takePictureIntent.resolveActivity(activity?.packageManager!!) != null) {
                      Log.i(TAG, "creating image file...")
                      // Create the File where the photo should go
                      val photoFile: File? = try {
                        createImageFile()
                      } catch (ex: IOException) {
                        // Error occurred while creating the File
                        showMessage(getString(R.string.error_creating_file))
                        Log.e(TAG, "Error al crear imagen de destino", ex)
                        null
                      }
                      // Continue only if the File was successfully created
                      if (photoFile != null) {
                        Log.i(TAG, "image file created")
                        preValue = photoFile
                        val photoURI: Uri = FileProvider.getUriForFile(
                          requireContext(),
                          "com.tautech.cclapp.fileprovider",
                          photoFile
                        )
                        Log.i(TAG, "photoFile created: ${photoFile.absolutePath}")
                        Log.i(TAG, "photoFile created uri: ${photoURI}")
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
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
                (viewAux.getChildAt(2) as ImageButton).setImageDrawable(ContextCompat.getDrawable(
                  requireContext(),
                  R.drawable.ic_camera___936_))
              }
              FieldSubType.Signature.value -> {
                Log.i(TAG, "el subtipo es firma")
                (viewAux.getChildAt(2) as ImageButton).setImageDrawable(ContextCompat.getDrawable(
                  requireContext(),
                  R.drawable.ic_edit___1479_))
                intent = Intent(requireContext(), CreateSignatureActivity::class.java)
              }
              else -> Log.e(TAG,
                "No se encontro el subtipo ${field.subtype} del campo ${field.label}")
            }
            requestCode = formContainerLayout?.childCount
            (viewAux.getChildAt(2) as ImageButton).setOnClickListener { view ->
              if (intent != null) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                  if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                    showAlert(getString(R.string.important),
                      getString(R.string.should_show_rationale_camera_permission_message))
                  } else {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA),
                      REQUEST_CAMERA_PERMISSION)
                  }
                } else {
                  startActivityForResult(intent, requestCode!!)
                }
              } else {
                showMessage(getString(R.string.no_action_provided))
              }
            }
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          ControlType.Checkbox.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Checkbox.value}")
            val viewAux =
              View.inflate(requireContext(), R.layout.checkbox_control_layout, null) as CheckBox
            viewAux.text = "${field.label} ${if(field.required == true) "*" else "" }"
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          ControlType.Input.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Input.value}")
            val viewAux = View.inflate(requireContext(),
              R.layout.input_text_control_layout,
              null) as LinearLayout
            when (field.type) {
              FieldType.Text.value -> {
                (viewAux.getChildAt(1) as TextView).inputType =
                  InputType.TYPE_CLASS_TEXT
              }
              FieldType.Decimal.value -> {
                (viewAux.getChildAt(1) as TextView).inputType =
                  InputType.TYPE_NUMBER_FLAG_DECIMAL
              }
              FieldType.Integer.value -> {
                (viewAux.getChildAt(1) as TextView).inputType =
                  InputType.TYPE_CLASS_NUMBER
              }
            }
            (viewAux.getChildAt(0) as TextView).text = "${field.label} ${if(field.required == true) "*" else "" }"
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          ControlType.Location.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Location.value}")
            val viewAux =
              View.inflate(requireContext(), R.layout.file_control_layout, null) as ConstraintLayout
            (viewAux.getChildAt(2) as ImageButton).setImageDrawable(ContextCompat.getDrawable(
              requireContext(),
              R.drawable.ic_pin_sharp_plus___627_))
            (viewAux.getChildAt(3) as ImageButton).setImageDrawable(ContextCompat.getDrawable(
              requireContext(),
              R.drawable.ic_view_simple___815_))
            (viewAux.getChildAt(3) as ImageButton).imageTintList = ColorStateList.valueOf(
              requireContext().getColor(
                R.color.black))
            (viewAux.getChildAt(2) as ImageButton).imageTintList = ColorStateList.valueOf(
              requireContext().getColor(
                R.color.black))
            (viewAux.getChildAt(0) as TextView).text = "${field.label} ${if(field.required == true) "*" else "" }"
            val requestCode = formContainerLayout?.childCount
            (viewAux.getChildAt(2) as ImageButton).setOnClickListener {
              // TODO: abrir mapa con la posicion del movil
              val intent = Intent(requireContext(), MapsActivity::class.java)
              if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){
                  showAlert(getString(R.string.important),
                    getString(R.string.should_show_rationale_location_permission_message))
                } else {
                  requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION)
                }
              } else {
                startActivityForResult(intent, requestCode!!)
              }
            }
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          ControlType.RadioGroup.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.RadioGroup.value}")
            val viewAux = View.inflate(requireContext(),
              R.layout.radio_group_control_layout,
              null) as RadioGroup
            (viewAux.getChildAt(0) as TextView).text = "${field.label} ${if(field.required == true) "*" else "" }"
            if (!field.items.isNullOrEmpty()) {
              if (field.itemList == null) {
                Log.i(TAG, "itemList de ${field.name} esta vacia")
                field.initItemList()
              }
              for(i in 0 until field.itemList?.length()!!) {
                val item = field.itemList?.getJSONObject(i)
                Log.i(TAG, "creando item ${item}")
                if (item != null && item.has("value") && item.has("label")) {
                  val radioItem = RadioButton(requireContext())
                  radioItem.text = item.getString("label")
                  radioItem.tag = item.get("value")
                  viewAux.id = View.generateViewId()
                  viewAux.addView(radioItem)
                }
              }
            }
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          ControlType.Select.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Select.value}")
            val viewAux =
              View.inflate(requireContext(), R.layout.select_control_layout, null) as LinearLayout
            (viewAux.getChildAt(0) as TextView).text = "${field.label} ${if(field.required == true) "*" else "" }"
            if (field.itemList == null) {
              Log.i(TAG, "itemList de ${field.name} esta vacia")
              field.initItemList()
            }
            val itemsMutableList = mutableListOf<String>()
            for(i in 0 until field.itemList?.length()!!) {
              val item = field.itemList?.getJSONObject(i)
              Log.i(TAG, "creando item ${item}")
              if (item != null && item.has("value") && item.has("label")) {
                itemsMutableList.add(item.getString("label"))
              }
            }
            val spinnerAdapter = ArrayAdapter<String>(requireContext(),
              android.R.layout.simple_dropdown_item_1line,
              itemsMutableList
            )
            (viewAux.getChildAt(1) as Spinner).adapter = spinnerAdapter
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          ControlType.Toggle.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Toggle.value}")
            val viewAux = View.inflate(requireContext(),
              R.layout.toggle_control_layout,
              null) as SwitchCompat
            viewAux.text = "${field.label} ${if(field.required == true) "*" else "" }"
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          ControlType.Date.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Date.value}")
            val viewAux = View.inflate(requireContext(),
              R.layout.input_text_control_layout,
              null) as LinearLayout
            (viewAux.getChildAt(1) as EditText).inputType =
              InputType.TYPE_CLASS_DATETIME
            (viewAux.getChildAt(1) as EditText).isFocusable = false
            (viewAux.getChildAt(0) as TextView).text = "${field.label} ${if(field.required == true) "*" else "" }"
            (viewAux.getChildAt(1) as EditText).setCompoundDrawables(null,
              null,
              ContextCompat.getDrawable(requireContext(),
                R.drawable.ic_calendar___1194_),
              null)
            val index = formContainerLayout?.childCount
            (viewAux.getChildAt(1) as EditText).setOnClickListener { view ->
              val dialog = DatePickerFragmentDialog(index!!)
              dialog.setTargetFragment(this, index!!)
              Log.i(TAG, "abriendo datepicker, envio indice $index")
              dialog.show(requireFragmentManager(), "datePicker")
            }
            viewAux.id = View.generateViewId()
            viewId = viewAux.id
            formContainerLayout?.addView(viewAux)
          }
          else -> {
            Log.e(TAG, "No se encontro un control para el campo ${field.name}")
          }
        }
        val childCount = formContainerLayout.childCount
        if (childCount > counter) {
          Log.i(TAG, "preValue es $preValue")
          createdControls.put(counter, FieldValueContainer(field, preValue, viewId))
          counter++
          Log.i(TAG, "control ${field.label} agregado satisfactoriamente")
        } else {
          Log.e(TAG, "error al agregar control ${field.label}")
        }
      }
      Log.i(TAG,
        "formulario creado tiene ${formContainerLayout?.childCount}/${stateFormDefinition.formFieldList?.size} campos")
      showForm()
    } else {
      Log.e(TAG, "state form definition NO econtrada para el state ${delivery?.deliveryState}")
      showMessage(getString(R.string.no_state_form_found))
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

  @Throws(IOException::class)
  private fun createImageFileSignature(extension: String): File {
    // Create an image file name
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    val storageDir: File = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
    return File.createTempFile(
      "${timeStamp}_", /* prefix */
      ".$extension", /* suffix */
      storageDir /* directory */
    )
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    // check for the results
    Log.i(TAG, "createdControls: $createdControls")
    Log.i(TAG, "onActivityResults, requestCode: $requestCode, resultCode: $resultCode")
    createdControls.forEach { controlContainer ->
      Log.i(TAG, "requestCode: $requestCode == controlContainer.key: ${controlContainer.key}")
      if (requestCode == controlContainer.key) {
        Log.i(TAG, "control encontrado: ${controlContainer}")
        if (controlContainer.value.field.controlType == ControlType.Date.value && resultCode == Activity.RESULT_OK) {
          // get date from string
          val selectedDate = data?.getStringExtra("selectedDate").toString()
          Log.i(TAG,
            "obteniendo fecha seleccionada: $selectedDate en el view con index ${controlContainer.key}...")
          // set the value of the editText
          if (formContainerLayout?.getChildAt(controlContainer.key) != null) {
            ((formContainerLayout?.getChildAt(controlContainer.key) as LinearLayout).getChildAt(
              1) as EditText).setText(
              selectedDate)
            ((formContainerLayout?.getChildAt(controlContainer.key) as LinearLayout).getChildAt(1) as EditText).error = null
            controlContainer.value.fieldValue = selectedDate
            controlContainer.value.dirty = true
          } else {
            Log.e(TAG, "La vista en el indice ${controlContainer.key} es nula")
          }
        }
        else if (controlContainer.value.field.controlType == ControlType.File.value && resultCode == Activity.RESULT_OK) {
          when (controlContainer.value.field.subtype) {
            FieldSubType.Document.value -> {
              Log.i(TAG, "obteniendo documento seleccionado")
              Log.i(TAG, "data: ${data?.type}")
              Log.i(TAG, "data.data: ${data?.data}")
              data?.data?.also { uri ->
                if (uri.path != null) {
                  // Perform operations on the document using its URI.
                  val file = File(uri.path!!)
                  if(file != null) {
                    Log.i(TAG, "document file exists in: ${file.absolutePath}")
                    ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                      1) as TextView).error = null
                    controlContainer.value.fieldValue = file
                    controlContainer.value.dirty = true
                    ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                      3) as ImageButton).setOnClickListener {
                      val intent = Intent()
                      intent.action = Intent.ACTION_VIEW
                      intent.setDataAndType(uri,
                        "application/*")
                      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                      startActivity(intent)
                    }
                    ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                      3) as ImageButton).visibility = View.VISIBLE
                    Log.i(TAG, "documento seleccionado: ${file.name}")
                    ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                      1) as TextView).text = file.name
                  } else {
                    Log.e(TAG, "document doesnt exists")
                  }
                } else {
                  Log.e(TAG, "Documento seleccionado invalido")
                  showAlert(getString(R.string.error), getString(R.string.selected_document_error))
                }
              }
            }
            FieldSubType.Signature.value -> {
              var bmp: Bitmap? = null
              val byteArray = data?.getByteArrayExtra("image")
              if (byteArray != null) {
                bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                /* opcion 1 */
                if (bmp != null) {
                  // Create the File where the photo should go
                  val signatureFile: File? = try {
                    createImageFileSignature("png")
                  } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Log.e(TAG, "Error ocurred while creating the image file", ex)
                    null
                  }
                  signatureFile.also {
                    if (it != null) {
                      try {
                        FileOutputStream(it).use { out ->
                          bmp.compress(Bitmap.CompressFormat.PNG,
                            100,
                            out)
                        }
                        ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                          4) as ImageView).setImageBitmap(
                          bmp)
                        controlContainer.value.fieldValue = signatureFile
                        controlContainer.value.dirty = true
                        ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                          4) as ImageView).visibility =
                          View.VISIBLE
                        Log.i(TAG,
                          "obteniendo firma, ancho: ${bmp.width}, index: ${controlContainer.key}...")
                      } catch (e: IOException) {
                        Log.e(TAG, "Error ocurred while filling the image file", e)
                        e.printStackTrace()
                      }
                    }
                  }
                }
              }
            }
            FieldSubType.Photo.value -> {
              Log.i(TAG, "obteniendo foto seleccionada")
              try {
                controlContainer.apply {
                  if (this.value.fieldValue != null) {
                    Log.i(TAG, "Se usaran los datos de camera activity")
                    (this.value.fieldValue as File).also { file ->
                      Log.i(TAG, "photo file: ${file.absolutePath} before compression ${file.length() / 1024}kb")
                      val photoURI: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.tautech.cclapp.fileprovider",
                        file
                      )
                      controlContainer.value.dirty = true
                      ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                        1) as TextView).error = null
                      ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                        3) as ImageButton).visibility = View.VISIBLE
                      ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                        3) as ImageButton).setOnClickListener {
                        val intent = Intent()
                        intent.action = Intent.ACTION_VIEW
                        intent.setDataAndType(photoURI,
                          "image/*")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent)
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
        else if (controlContainer.value.field.controlType == ControlType.Location.value && resultCode == Activity.RESULT_OK) {
          if (data != null && data.hasExtra("position")) {
            val selectedLocation = data.getParcelableExtra<LatLng>("position")
            Log.i(TAG,
              "ubicacion seleccionada: $selectedLocation")
            // set the value of the editText
            if (selectedLocation != null) {
              if (formContainerLayout?.getChildAt(controlContainer.key) != null) {
                ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                  1) as TextView).setText(
                  selectedLocation.toString())
                ((formContainerLayout?.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                  1) as TextView).error = null
                controlContainer.value.fieldValue = "${selectedLocation.latitude},${selectedLocation.longitude}"
                controlContainer.value.dirty = true
              } else {
                Log.e(TAG, "La vista en el indice ${controlContainer.key} es nula")
              }
            } else {
              showMessage(getString(R.string.invalid_selected_position))
            }
          } else {
            showMessage(getString(R.string.invalid_selected_position))
          }
        } else {
          Log.e(TAG,
            "No se encontro acciones a realizar para el control ${controlContainer.value.field.label} con result code: $resultCode")
        }
      } else {
        Log.e(TAG, "No se encontro control en el indice $requestCode")
      }
    }
  }

  fun isValidForm(): Boolean {
    //var isValid = true
    createdControls.forEach { (key, valueContainer) ->
      Log.i(TAG, "validando control $key:${valueContainer}...")
      if (valueContainer.field.required == true && formContainerLayout?.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} es requerido")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input = (formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
          if (input.text.isNullOrEmpty()) {
            input.setError(getString(R.string.required_field))
            showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
            //isValid = false
            return false
          } else {
            input.error = null
          }
        }
        if (valueContainer.field.controlType == ControlType.RadioGroup.value) {
          val lastChild = (formContainerLayout?.getChildAt(key) as RadioGroup).childCount - 1
          if ((formContainerLayout?.getChildAt(key) as RadioGroup).checkedRadioButtonId == -1) {
            ((formContainerLayout?.getChildAt(key) as RadioGroup).getChildAt(lastChild) as RadioButton).setError(
              getString(R.string.required_field))
            showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
            //isValid = false
            //return@forEach
            return false
          } else {
            ((formContainerLayout?.getChildAt(key) as RadioGroup).getChildAt(lastChild) as RadioButton).error =
              null
          }
        }
        if (valueContainer.field.controlType == ControlType.Select.value) {
          val spinner =
            (formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as Spinner
          if (spinner.selectedItem == null) {
            spinner.requestFocus()
            showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
            //isValid = false
            //return@forEach
            return false
          }
        }
        if (valueContainer.field.controlType == ControlType.Location.value && valueContainer.fieldValue == null){
          //isValid = false
          ((formContainerLayout?.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).error = getString(R.string.required_field_arg, valueContainer.field.label)
          ((formContainerLayout?.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).requestFocus()
          showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
          //return@forEach
          return false
        }
        if (valueContainer.field.controlType == ControlType.File.value && (valueContainer.fieldValue == null || !valueContainer.dirty)){
          //isValid = false
          ((formContainerLayout?.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).error = getString(R.string.required_field_arg, valueContainer.field.label)
          ((formContainerLayout?.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).requestFocus()
          showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
          //return@forEach
          return false
        }
        if (valueContainer.field.controlType == ControlType.Date.value && valueContainer.fieldValue == null){
          //isValid = false
          ((formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as TextView).error = getString(R.string.required_field_arg, valueContainer.field.label)
          ((formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as TextView).requestFocus()
          showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
          //return@forEach
          return false
        }
      }
      if (valueContainer.field.max != null && formContainerLayout?.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} tiene un limite maximo de ${valueContainer.field.max}")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input =
            (formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
          if (!input.text.isNullOrEmpty() &&
            (valueContainer.field.type == FieldType.Text.value && input.text.length > valueContainer.field.max!!) ||
            (valueContainer.field.type == FieldType.Decimal.value && !input.text.isNullOrEmpty() && input.text.toString()
              .toDouble() > valueContainer.field.max!!) ||
            (valueContainer.field.type == FieldType.Integer.value && !input.text.isNullOrEmpty() && input.text.toString()
              .toLong() > valueContainer.field.max!!)
          ) {
            if (valueContainer.field.type == FieldType.Text.value) {
              input.setError(getString(R.string.max_length_exceed, valueContainer.field.max))
            } else {
              input.setError(getString(R.string.max_value_exceed, valueContainer.field.max))
            }
            showMessage(getString(R.string.max_value_exceed, valueContainer.field.max))
            //isValid = false
            //return@forEach
            return false
          } else {
            input.error = null
          }
        }
      }
      if (valueContainer.field.min != null && formContainerLayout?.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} tiene un limite minimo de ${valueContainer.field.min}")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input =
            (formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
          if (!input.text.isNullOrEmpty() &&
            (valueContainer.field.type == FieldType.Text.value && input.text.length < valueContainer.field.min!!) ||
            (valueContainer.field.type == FieldType.Decimal.value && !input.text.isNullOrEmpty() && input.text.toString()
              .toDouble() < valueContainer.field.min!!) ||
            (valueContainer.field.type == FieldType.Integer.value && !input.text.isNullOrEmpty() && input.text.toString().toLong() < valueContainer.field.min!!)
          ) {
            if (valueContainer.field.type == FieldType.Text.value) {
              input.setError(getString(R.string.min_length_exceed, valueContainer.field.min))
            } else {
              input.setError(getString(R.string.min_value_exceed, valueContainer.field.min))
            }
            showMessage(getString(R.string.min_value_exceed, valueContainer.field.min))
            //isValid = false
            //return@forEach
            return false
          } else {
            input.error = null
          }
        }
      }
      if (!valueContainer.field.regex.isNullOrEmpty() && formContainerLayout?.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} tiene un regex de ${valueContainer.field.regex}")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input =
            (formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
          if (!input.text.isNullOrEmpty() &&
            valueContainer.field.type == FieldType.Text.value && Regex(valueContainer.field.regex!!).matches(input.text)
          ) {
            input.setError(valueContainer.field.invalidRegexMsg)
            showMessage(valueContainer.field.invalidRegexMsg ?: getString(R.string.invalid_form))
            //isValid = false
            //return@forEach
            return false
          } else {
            input.error = null
          }
        }
      }
    }
    return true
  }

  @kotlin.jvm.Throws(IOException::class)
  fun getFinalFutureState(): String{
    val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
    val totalDeliveredItems = viewModel.deliveryLines.value?.fold(0, { totalDelivered, deliveryLine ->
      totalDelivered + deliveryLine.delivered
    })  ?: 0
    Log.i(TAG, "totalDeliveredItems: $totalDeliveredItems")
    Log.i(TAG, "filteredData: ${ManageDeliveryItemsFragment.getInstance()?.filteredData}")
    if (totalDeliveredItems == viewModel.delivery.value?.totalQuantity) {
      return "Delivered"
    }
    if (totalDeliveredItems == 0) {
      return "UnDelivered"
    }
    if (totalDeliveredItems > 0 && totalDeliveredItems < viewModel.delivery.value?.totalQuantity ?: 0) {
      return "Partial"
    }
    throw (IOException("Error: Unknown final state for delivery ${viewModel.delivery.value?.deliveryId}"))
  }

  fun generateFormDataWithoutFiles(): StateForm? {
    var stateForm: StateForm? = null
    try {
      stateForm = StateForm(null, getFinalFutureState())
      stateForm.data = arrayListOf()
      createdControls.forEach { (key, valueContainer) ->
        if (formContainerLayout?.getChildAt(key) != null) {
          if (valueContainer.field.controlType == ControlType.Input.value) {
            val input =
              (formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
            val inputVal = input.text.toString()
            val value: Any = when (valueContainer.field.type) {
              FieldType.Text.value -> inputVal
              FieldType.Decimal.value -> if (!inputVal.isEmpty()) inputVal.toDouble() else 0.00
              FieldType.Integer.value -> if (!inputVal.isEmpty()) inputVal.toLong() else 0
              else -> inputVal
            }
            stateForm.data?.add(Item(valueContainer.field.name ?: "", value))
          }
          if (valueContainer.field.controlType == ControlType.RadioGroup.value) {
            val input = formContainerLayout?.getChildAt(key) as RadioGroup
            if (input.checkedRadioButtonId > -1) {
              val checkedVal = input.findViewById<RadioButton>(input.checkedRadioButtonId).tag
              if (checkedVal != null) {
                stateForm.data?.add(Item(valueContainer.field.name ?: "", checkedVal))
              }
            }
          }
          if (valueContainer.field.controlType == ControlType.Toggle.value) {
            val input = formContainerLayout?.getChildAt(key) as SwitchCompat
            val value = input.isChecked
            stateForm.data?.add(Item(valueContainer.field.name ?: "", value))
          }
          if (valueContainer.field.controlType == ControlType.Select.value) {
            val select =
              (formContainerLayout?.getChildAt(key) as LinearLayout).getChildAt(1) as Spinner
            val selectedPos = select.selectedItemPosition
            val selectedVal =
              valueContainer.field.itemList?.getJSONObject(selectedPos)?.get("value")
            if (selectedVal != null) {
              stateForm.data?.add(Item(valueContainer.field.name ?: "", selectedVal))
            } else {
              Log.e(TAG, "el valor para ${valueContainer.field.name} es null")
            }
          }
          if (valueContainer.field.controlType == ControlType.Checkbox.value) {
            val value = (formContainerLayout?.getChildAt(key) as CheckBox).isChecked
            stateForm.data?.add(Item(valueContainer.field.name ?: "", value))
          }
          if (valueContainer.field.controlType == ControlType.Location.value || valueContainer.field.controlType == ControlType.Date.value) {
            val value = valueContainer.fieldValue
            if (value != null) {
              stateForm.data?.add(Item(valueContainer.field.name ?: "", value))
            }
          }
        }
      }
    } catch (ex: IOException){
      Log.e(TAG, "exception found!!", ex)
    }
    return stateForm
  }

  fun generateFormDataWithFiles(): ArrayList<Item> {
    val items: ArrayList<Item> = arrayListOf()
    createdControls.forEach { (key, valueContainer) ->
      if (formContainerLayout?.getChildAt(key) != null) {
        if (valueContainer.field.controlType == ControlType.File.value) {
          Log.i(TAG, "archivo encontrado para ${valueContainer.field.label}")
          val value = valueContainer.fieldValue
          if (value != null && value is File) {
            Log.i(TAG, "valor de ${valueContainer.field.label} es tipo archivo")
            val mediaTypeStr = when(valueContainer.field.subtype) {
              FieldSubType.Photo.value -> {
                val fileUri: Uri = FileProvider.getUriForFile(
                  requireContext(),
                  "com.tautech.cclapp.fileprovider",
                  value
                )
                activity?.contentResolver?.getType(fileUri)
              }
              FieldSubType.Signature.value -> {
                val fileUri: Uri = FileProvider.getUriForFile(
                  requireContext(),
                  "com.tautech.cclapp.fileprovider",
                  value
                )
                activity?.contentResolver?.getType(fileUri)
              }
              FieldSubType.Document.value -> {
                "application/pdf"
              }
              else -> null
            }
            if (mediaTypeStr != null) {
                items.add(Item(valueContainer.field.name ?: "unknown", value))
            } else {
              Log.e(TAG, "mimetype de ${valueContainer.field.label} es invalido")
            }
          } else {
            Log.e(TAG, "valor de ${valueContainer.field.label} no es tipo archivo")
          }
        }
      }
    }
    return items
  }

  override fun onRefresh() {
    Log.i(TAG, "onRefresh()...")
    //loadStateFormDefinitions()
  }

  fun loadStateFormDefinitions() {
    fetchData(this::loadStateFormDefinitions)
  }

  private fun loadStateFormDefinitions(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
    if (ex != null) {
      Log.e(TAG, "ocurrio una excepcion mientras se recuperaban lineas de planificacion", ex)
      if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
        showAlert("Sesion expirada", "Su sesion ha expirado", this::signOut)
      }
      return
    }
    val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
    Log.i(TAG, "loading form definitions...")
    //https://{{hostname}}/stateFormDefinitions/search/findByCustomerId?customerId=2
    //val url = "stateFormDefinitions/search/findByCustomerId?customerId=${viewModel.planification.value?.customerId}"
    val url = "api/customers/stateConfing?customer-id=${viewModel.planification.value?.customerId}"
    Log.i(TAG, "built endpoint: $url")
    val dataService: CclDataService? = CclClient.getInstance()?.create(
      CclDataService::class.java)
    if (dataService != null && accessToken != null) {
      swipeRefresh2.isRefreshing = true
      doAsync {
        try {
          val call = dataService.getStateFormDefinitions(url,
            "Bearer $accessToken")
            .execute()
          val response = call.body()
          swipeRefresh2.isRefreshing = false
          Log.i(TAG, "respuesta al cargar form definitions: ${response}")
          if (response != null && response.size > 0) {
            try {
              Log.i(TAG, "guardando en DB local definiciones en la BD local")
              if(viewModel.planification.value?.customerId != null) {
                db?.stateFormDefinitionDao()
                  ?.deleteAllByCustomer(viewModel.planification.value?.customerId!!)
              }
              db?.stateFormDefinitionDao()?.insertAll(response)
              val allFields = response.flatMap { def ->
                def.formFieldList?.forEach { field ->
                  field.formDefinitionId = def.id
                }
                def.formFieldList ?: listOf()
              }
              db?.stateFormFieldDao()?.deleteAll()
              if(!allFields.isNullOrEmpty()) {
                Log.i(TAG, "guardando en la BD local los items de formulario: $allFields")
                db?.stateFormFieldDao()?.insertAll(allFields)
              }
              loadFormDefinitionFromLocalDB()
            } catch (ex: SQLiteException) {
              Log.e(TAG,
                "Error guardando state form definitions en la BD local",
                ex)
              showAlert(getString(R.string.database_error),
                getString(R.string.database_error_saving_state_form_definitions))
            } catch (ex: SQLiteConstraintException) {
              Log.e(TAG,
                "Error guardando state form definitions en la BD local",
                ex)
              showAlert(getString(R.string.database_error),
                getString(R.string.database_error_saving_state_form_definitions))
            } catch (ex: Exception) {
              Log.e(TAG,
                "Error guardando state form definitions en la BD local",
                ex)
              showAlert(getString(R.string.database_error),
                getString(R.string.database_error_saving_state_form_definitions))
            }
          } else {
            showEmptyFormMessage(getString(R.string.forms_not_found_message))
          }
        } catch(toe: SocketTimeoutException) {
          swipeRefresh2.isRefreshing = false
          Log.e(TAG, "Error de red cargando state form definitions", toe)
          showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
        } catch (ioEx: IOException) {
          swipeRefresh2.isRefreshing = false
          Log.e(TAG,
            "Error de red cargando state form definitions",
            ioEx)
          showAlert(getString(R.string.network_error_title), getString(R.string.network_error))
        }
      }
    }
  }

  private fun loadFormDefinitionFromLocalDB(){
    val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
    if (viewModel.state.value != null) {
      Log.i(TAG,
        "buscando definiciones en la BD local para el estado ${viewModel.state.value}...")
      doAsync {
        val foundStateFormDefinition = db?.stateFormDefinitionDao()
          ?.getAllByStateAndCustomer(viewModel.state.value!!,
            viewModel.planification.value?.customerId!!)
        if (!foundStateFormDefinition.isNullOrEmpty()) {
          hideEmptyFormMessage()
          Log.i(TAG, "found state form definition: $foundStateFormDefinition")
          foundStateFormDefinition[0].formFieldList =
            db?.stateFormDefinitionDao()?.getFields(foundStateFormDefinition[0].id!!)
          Log.i(TAG, "found fieldList: ${foundStateFormDefinition[0].formFieldList}")
          viewModel.stateFormDefinition.postValue(foundStateFormDefinition[0])
        } else {
          Log.e(TAG,
            "form definitions not found for state ${viewModel.state.value} and customer ${viewModel.planification.value?.customerId}")
          viewModel.stateFormDefinition.postValue(null)
        }
      }
    }
  }

  private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
    Log.i(TAG, "Fetching data...$callback")
    try {
      mStateManager?.current?.performActionWithFreshTokens(mStateManager?.mAuthService!!,
        callback)
    }catch (ex: AuthorizationException) {
      Log.e(TAG, "error fetching data", ex)
    }
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
  ){
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

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    Log.i(TAG, "onSaveInstanceState()...")
  }

  override fun onPause() {
    super.onPause()
    Log.i(TAG, "onPause()...")
  }

  override fun onStop() {
    super.onStop()
    Log.i(TAG, "onStop()...")
    //guardamos los valores del formulario
    createdControls.forEach { (key, valueContainer) ->
      //activity?.findViewById(valueContainer.viewId!!)
    }
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "onResume()...")
    //restauramos los valores del formulario
  }

  companion object{
    var mInstance: DeliveryFormFragment? = null
    fun getInstance(): DeliveryFormFragment?{
      if(mInstance == null){
        Log.i("DELIVERY_FORM_FRAGMENT", "instancia de DeliveryFormFragment es null, creando una nueva")
        mInstance = DeliveryFormFragment()
      }
      return mInstance
    }
  }
}