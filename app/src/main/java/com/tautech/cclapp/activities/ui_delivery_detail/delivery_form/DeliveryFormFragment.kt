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
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.maps.model.LatLng
import com.tautech.cclapp.*
import com.tautech.cclapp.activities.*
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.classes.DatePickerFragmentDialog
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.fragment_delivery_form.*
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
  var fieldValue: Any?
)
class DeliveryFormFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {
  private val REQUEST_LOCATION_PERMISSION: Int = 2
  private val REQUEST_CAMERA_PERMISSION: Int = 1
  val viewModel: ManageDeliveryActivityViewModel by activityViewModels()
  val TAG = "DELIVERY_FORM_FRAGMENT"
  val createdControls = HashMap<Int, FieldValueContainer>()
  private var retrofitClient: Retrofit? = null
  private var mAuthService: AuthorizationService? = null
  private var mStateManager: AuthStateManager? = null
  private var mConfiguration: Configuration? = null
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
    mConfiguration = Configuration.getInstance(requireContext())
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
      Toast.makeText(
        requireContext(),
        "Configuration change detected",
        Toast.LENGTH_SHORT)
        .show()
      signOut()
    }
    mAuthService = AuthorizationService(
      requireContext(),
      AppAuthConfiguration.Builder()
        .setConnectionBuilder(config.connectionBuilder)
        .build())
    if (!mStateManager!!.current.isAuthorized) {
      Log.i(TAG, "No hay autorizacion para el usuario")
      signOut()
    }
    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    (activity as ManageDeliveryActivity).supportActionBar?.setDisplayShowHomeEnabled(true)
    viewModel.stateFormDefinition.observe(viewLifecycleOwner, Observer { definition ->
      Log.i(TAG, "state form definition observada: $definition")
      if (definition != null) {
        loadForm(definition, viewModel.delivery.value)
      } else {
        showEmptyFormMessage(getString(R.string.no_state_form_found))
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
      messageTv3.text = message
      messageTv3.visibility = View.VISIBLE
    }
  }

  private fun hideEmptyFormMessage() {
    activity?.runOnUiThread{
      messageTv3.visibility = View.GONE
    }
  }

  private fun showForm() {
    Log.i(TAG, "mostrando formulario")
    activity?.runOnUiThread{
      messageTv3.visibility = View.GONE
      formContainerLayout.visibility = View.VISIBLE
    }
  }

  private fun hideForm() {
    Log.i(TAG, "ocultando formulario")
    activity?.runOnUiThread{
      formContainerLayout.visibility = View.GONE
    }
  }

  private fun loadForm(stateFormDefinition: StateFormDefinition?, delivery: PlanificationLine?){
    Log.i(TAG, "armando formulario...")
    Log.i(TAG, "state form definition a usar para el estado ${delivery?.deliveryState}: ${stateFormDefinition}")
    formContainerLayout.removeAllViews()
    if (stateFormDefinition != null) {
      var counter = formContainerLayout.childCount
      stateFormDefinition.formFieldList?.forEach { field ->
        var preValue: Any? = null
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
            requestCode = formContainerLayout.childCount
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
                  startActivityForResult(intent, requestCode)
                }
              } else {
                showMessage(getString(R.string.no_action_provided))
              }
            }
            formContainerLayout.addView(viewAux)
          }
          ControlType.Checkbox.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Checkbox.value}")
            val viewAux =
              View.inflate(requireContext(), R.layout.checkbox_control_layout, null) as CheckBox
            viewAux.text = "${field.label} ${if(field.required == true) "*" else "" }"
            formContainerLayout.addView(viewAux)
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
            formContainerLayout.addView(viewAux)
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
            val requestCode = formContainerLayout.childCount
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
                startActivityForResult(intent, requestCode)
              }
            }
            formContainerLayout.addView(viewAux)
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
                  viewAux.addView(radioItem)
                }
              }
            }
            formContainerLayout.addView(viewAux)
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
            formContainerLayout.addView(viewAux)
          }
          ControlType.Toggle.value -> {
            Log.i(TAG, "control ${field.name} es de tipo ${ControlType.Toggle.value}")
            val viewAux = View.inflate(requireContext(),
              R.layout.toggle_control_layout,
              null) as SwitchCompat
            viewAux.text = "${field.label} ${if(field.required == true) "*" else "" }"
            formContainerLayout.addView(viewAux)
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
            val index = formContainerLayout.childCount
            (viewAux.getChildAt(1) as EditText).setOnClickListener { view ->
              val dialog = DatePickerFragmentDialog(index)
              dialog.setTargetFragment(this, index)
              Log.i(TAG, "abriendo datepicker, envio indice $index")
              dialog.show(requireFragmentManager(), "datePicker")
            }
            formContainerLayout.addView(viewAux)
          }
          else -> {
            Log.e(TAG, "No se encontro un control para el campo ${field.name}")
          }
        }
        val childCount = formContainerLayout.childCount
        if (childCount > counter) {
          Log.i(TAG, "preValue es $preValue")
          createdControls.put(counter, FieldValueContainer(field, preValue))
          counter++
          Log.i(TAG, "control ${field.label} agregado satisfactoriamente")
        } else {
          Log.e(TAG, "error al agregar control ${field.label}")
        }
      }
      Log.i(TAG,
        "formulario creado tiene ${formContainerLayout.childCount}/${stateFormDefinition.formFieldList?.size} campos")
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
          if (formContainerLayout.getChildAt(controlContainer.key) != null) {
            ((formContainerLayout.getChildAt(controlContainer.key) as LinearLayout).getChildAt(
              1) as EditText).setText(
              selectedDate)
            controlContainer.value.fieldValue = selectedDate
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
                  val file: File = File(uri.path!!)
                  controlContainer.value.fieldValue = file
                  ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                    3) as ImageButton).setOnClickListener {
                    // TODO: abrir documento con intent
                    val intent = Intent()
                    intent.action = Intent.ACTION_VIEW
                    intent.setDataAndType(uri,
                      "image/*")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent)
                  }
                  ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                    3) as ImageButton).visibility = View.VISIBLE
                  Log.i(TAG, "documento seleccionado: ${file.nameWithoutExtension}")
                  ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                    1) as TextView).setText(
                    file.nameWithoutExtension)
                } else {
                  Log.e(TAG, "Archivo seleccionado invalido")
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
                  val photoFile: File? = try {
                    createImageFileSignature("png")
                  } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Log.e(TAG, "Error ocurred while creating the image file", ex)
                    null
                  }
                  photoFile.also {
                    try {
                      FileOutputStream(it).use { out ->
                        bmp?.compress(Bitmap.CompressFormat.PNG,
                          100,
                          out)
                      }
                    } catch (e: IOException) {
                      Log.e(TAG, "Error ocurred while filling the image file", e)
                      e.printStackTrace()
                    }
                  }
                  /* opcion 2 */
                  /*val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                  val fileOutputStream: FileOutputStream =
                    requireContext().openFileOutput(timeStamp, Context.MODE_PRIVATE)
                  bmp?.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                  fileOutputStream.close()*/
                  ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                    4) as ImageView).setImageBitmap(
                    bmp)
                  controlContainer.value.fieldValue = photoFile
                  ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                    4) as ImageView).visibility =
                    View.VISIBLE
                  Log.i(TAG,
                    "obteniendo firma, ancho: ${bmp.width}, index: ${controlContainer.key}...")
                }
              }
            }
            FieldSubType.Photo.value -> {
              Log.i(TAG, "obteniendo foto seleccionada")
              try {
                var bmp: Bitmap? = null
                if (data?.extras?.containsKey("data") == true) {
                  bmp = data.extras?.get("data") as Bitmap
                }
                Log.i(TAG, "thumb bmp width: ${bmp?.width}, index: ${controlContainer.key}")
                controlContainer.apply {
                  if (data?.extras?.containsKey("image") == true) {
                    this.value.fieldValue = data.extras?.get("image") as File
                    Log.i(TAG, "usando datos de blank activity con el archivo: ${(this.value.fieldValue as File).absolutePath}")
                    bmp = BitmapFactory.decodeFile((this.value.fieldValue as File).absolutePath)
                  } else if(this.value.fieldValue != null) {
                    Log.i(TAG, "Se usaran los datos de camera activity")

                    (this.value.fieldValue as File).also { file ->
                      Log.i(TAG, "photo file: ${file.absolutePath}")
                      val photoURI: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.tautech.cclapp.fileprovider",
                        file
                      )
                      /*((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                      4) as ImageView).setImageBitmap(bmp)
                    ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                      4) as ImageView).visibility = View.VISIBLE*/
                      ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                        3) as ImageButton).visibility = View.VISIBLE
                      ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                        3) as ImageButton).setOnClickListener {
                        // TODO: abrir foto con intent
                        val intent = Intent()
                        intent.action = Intent.ACTION_VIEW
                        intent.setDataAndType(photoURI,
                          "image/*")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent)
                      }
                    }
                  }
                    /*try {
                      bmp = BitmapFactory.decodeFile(this.value.fieldValue as String)
                    } catch (e: FileNotFoundException) {
                      Log.e(TAG, "El directorio del archivo de la foto, no existe", e)
                      Toast.makeText(requireContext(),
                        "Error al crear directorio de la foto",
                        Toast.LENGTH_SHORT).show()
                    } catch (e: IOException) {
                      Log.e(TAG, "El directorio del archivo de la foto, no existe", e)
                      Toast.makeText(requireContext(),
                        "Error al crear archivo de la foto",
                        Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                      Toast.makeText(requireContext(),
                        "Error desconocido al crear archivo de la foto",
                        Toast.LENGTH_SHORT).show()
                    }
                    if (bmp != null) {
                      //this.value.fieldValue = bmp

                    } else {
                      Log.e(TAG, "Error al crear archivo de la foto")
                      Toast.makeText(requireContext(),
                        "Error al crear archivo de la foto",
                        Toast.LENGTH_SHORT).show()
                    }*/
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
              if (formContainerLayout.getChildAt(controlContainer.key) != null) {
                ((formContainerLayout.getChildAt(controlContainer.key) as ConstraintLayout).getChildAt(
                  1) as TextView).setText(
                  selectedLocation.toString())
                controlContainer.value.fieldValue = "${selectedLocation.latitude},${selectedLocation.longitude}"
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
            "No se encontro acciones a realizar para el control ${controlContainer.value.field.label}")
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
      if (valueContainer.field.required == true && formContainerLayout.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} es requerido")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input = (formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
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
          val lastChild = (formContainerLayout.getChildAt(key) as RadioGroup).childCount - 1
          if ((formContainerLayout.getChildAt(key) as RadioGroup).checkedRadioButtonId == -1) {
            ((formContainerLayout.getChildAt(key) as RadioGroup).getChildAt(lastChild) as RadioButton).setError(
              getString(R.string.required_field))
            showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
            //isValid = false
            //return@forEach
            return false
          } else {
            ((formContainerLayout.getChildAt(key) as RadioGroup).getChildAt(lastChild) as RadioButton).error =
              null
          }
        }
        /*if (valueContainer.field.controlType == ControlType.Toggle.value &&
          ((formContainerLayout.getChildAt(key) as SwitchCompat).isChecked)){
          isValid = false
          return@forEach
        }*/
        if (valueContainer.field.controlType == ControlType.Select.value) {
          val spinner =
            (formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as Spinner
          if (spinner.selectedItem == null) {
            spinner.requestFocus()
            showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
            //isValid = false
            //return@forEach
            return false
          }
        }
        /*if (valueContainer.field.controlType == ControlType.Checkbox.value &&
          ((formContainerLayout.getChildAt(key) as CheckBox).isChecked)){
          isValid = false
          return@forEach
        }*/
        if (valueContainer.field.controlType == ControlType.Location.value && valueContainer.fieldValue == null){
          //isValid = false
          ((formContainerLayout.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).error = getString(R.string.required_field_arg, valueContainer.field.label)
          ((formContainerLayout.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).requestFocus()
          showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
          //return@forEach
          return false
        }
        if (valueContainer.field.controlType == ControlType.File.value && valueContainer.fieldValue == null){
          //isValid = false
          ((formContainerLayout.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).error = getString(R.string.required_field_arg, valueContainer.field.label)
          ((formContainerLayout.getChildAt(key) as ConstraintLayout).getChildAt(1) as TextView).requestFocus()
          showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
          //return@forEach
          return false
        }
        if (valueContainer.field.controlType == ControlType.Date.value && valueContainer.fieldValue == null){
          //isValid = false
          ((formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as TextView).error = getString(R.string.required_field_arg, valueContainer.field.label)
          ((formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as TextView).requestFocus()
          showMessage(getString(R.string.required_field_arg, valueContainer.field.label))
          //return@forEach
          return false
        }
      }
      if (valueContainer.field.max != null && formContainerLayout.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} tiene un limite maximo de ${valueContainer.field.max}")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input =
            (formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
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
      if (valueContainer.field.min != null && formContainerLayout.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} tiene un limite minimo de ${valueContainer.field.min}")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input =
            (formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
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
      if (!valueContainer.field.regex.isNullOrEmpty() && formContainerLayout.getChildAt(key) != null) {
        Log.i(TAG, "control $key:${valueContainer.field.name} tiene un regex de ${valueContainer.field.regex}")
        if (valueContainer.field.controlType == ControlType.Input.value) {
          val input =
            (formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
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

  fun generateFormDataWithoutFiles(): StateForm? {
    var stateForm: StateForm? = null
    try {
      stateForm = StateForm(null, getFinalFutureState())
      stateForm.data = arrayListOf()
      createdControls.forEach { (key, valueContainer) ->
        if (formContainerLayout.getChildAt(key) != null) {
          if (valueContainer.field.controlType == ControlType.Input.value) {
            val input =
              (formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as EditText
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
            val input = formContainerLayout.getChildAt(key) as RadioGroup
            if (input.checkedRadioButtonId > -1) {
              val checkedVal = input.findViewById<RadioButton>(input.checkedRadioButtonId).tag
              if (checkedVal != null) {
                stateForm.data?.add(Item(valueContainer.field.name ?: "", checkedVal))
              }
            }
          }
          if (valueContainer.field.controlType == ControlType.Toggle.value) {
            val input = formContainerLayout.getChildAt(key) as SwitchCompat
            val value = input.isChecked
            stateForm.data?.add(Item(valueContainer.field.name ?: "", value))
          }
          if (valueContainer.field.controlType == ControlType.Select.value) {
            val select =
              (formContainerLayout.getChildAt(key) as LinearLayout).getChildAt(1) as Spinner
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
            val value = (formContainerLayout.getChildAt(key) as CheckBox).isChecked
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

  fun generateFormDataWithFiles(): StateForm {
    val formData = StateForm(null, viewModel.state.value ?: "")
    formData.data = arrayListOf()
    createdControls.forEach { (key, valueContainer) ->
      if (formContainerLayout.getChildAt(key) != null) {
        if (valueContainer.field.controlType == ControlType.File.value) {
          Log.i(TAG, "archivo encontrado para ${valueContainer.field.label}")
          val value = valueContainer.fieldValue
          if (value != null && value is File) {
            Log.i(TAG, "valor de ${valueContainer.field.label} es tipo archivo")
            val fileUri: Uri = FileProvider.getUriForFile(
              requireContext(),
              "com.tautech.cclapp.fileprovider",
              value
            )
            val mediaTypeStr = activity?.contentResolver?.getType(fileUri)
            if (mediaTypeStr != null) {
              val mimeType = MediaType.parse(mediaTypeStr)
              Log.i(TAG,
                "media type de ${valueContainer.field.label}:, mediaTypeStr: $mediaTypeStr, mimeType: $mimeType")
              //val requestFile = RequestBody.create(mimeType, value)
              val requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), value)
              val body = MultipartBody.Part.createFormData("file", value.name, requestFile)
              formData.data?.add(Item(valueContainer.field.name ?: "unknown", body))
            } else {
              Log.e(TAG, "mimetype de ${valueContainer.field.label} es invalido")
            }
          } else {
            Log.e(TAG, "valor de ${valueContainer.field.label} no es tipo archivo")
          }
        }
      }
    }
    return formData
  }

  @kotlin.jvm.Throws(IOException::class)
  fun getFinalFutureState(): String{
    val totalDeliveredItems = viewModel.delivery.value?.detail?.fold(0, { totalDelivered, deliveryLine ->
      totalDelivered + deliveryLine.deliveredQuantity
    })
    if (totalDeliveredItems != null) {
      if (totalDeliveredItems == viewModel.delivery.value?.totalQuantity) {
        return "Delivered"
      }
      if (totalDeliveredItems == 0) {
        return "UnDelivered"
      }
      if (totalDeliveredItems > 0 && totalDeliveredItems < viewModel.delivery.value?.totalQuantity ?: 0) {
        return "Partial"
      }
    }
    throw (IOException("Error: Unknown final state for delivery ${viewModel.delivery.value?.id}"))
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
      Log.e(TAG, "Token refresh failed when finalizing planification load", ex)
      AuthStateManager.driverInfo = null
      if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
        showAlert("Error", "Sesion Expirada", this::signOut)
      }
      return
    }
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
              for (def in response) {
                Log.i(TAG, "guardando en DB local definicion de ${def.deliveryState}")
                db?.stateFormDefinitionDao()?.insert(def)
                Log.i(TAG, "definicion de ${def.deliveryState} tiene ${def.formFieldList?.size ?: 0} fields")
                if (!def.formFieldList.isNullOrEmpty()) {
                  for (field in def.formFieldList!!) {
                    Log.i(TAG, "guardando field: $field")
                    field.formDefinitionId = def.id?.toInt()
                    db?.stateFormFieldDao()?.insert(field)
                  }
                }
              }
              if (viewModel.state.value != null) {
                Log.i(TAG,
                  "buscando definiciones en la BD local para el estado ${viewModel.state.value}...")
                val foundStateFormDefinition = db?.stateFormDefinitionDao()
                  ?.getAllByStateAndCustomer(viewModel.state.value!!,
                    viewModel.planification.value?.customerId?.toInt()!!)
                if (foundStateFormDefinition != null) {
                  Log.i(TAG, "found state form definition: $foundStateFormDefinition")
                  foundStateFormDefinition.formFieldList =
                    db?.stateFormDefinitionDao()?.getFields(foundStateFormDefinition.id!!.toInt())
                  Log.i(TAG, "found fieldList: ${foundStateFormDefinition.formFieldList}")
                  viewModel.stateFormDefinition.postValue(foundStateFormDefinition)
                } else {
                  Log.e(TAG,
                    "form definitions not found for state ${viewModel.state.value} and customer ${viewModel.planification.value?.customerId?.toInt()}")
                }
              }
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
            showEmptyFormMessage(getString(R.string.empty_form_message))
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

  private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
    Log.i(TAG, "Fetching data...$callback")
    try {
      mStateManager?.current?.performActionWithFreshTokens(mAuthService!!,
        callback)
    }catch (ex: AuthorizationException) {
      Log.e(TAG, "error fetching data", ex)
    }
  }

  private fun signOut() {
    mStateManager?.signOut(requireContext())
    val mainIntent = Intent(requireContext(), LoginActivity::class.java)
    mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
    startActivity(mainIntent)
    activity?.finish()
  }

  fun showAlert(title: String, message: String) {
    activity?.runOnUiThread {
      val builder = AlertDialog.Builder(requireContext())
      builder.setTitle(title)
      builder.setMessage(message)
      builder.setPositiveButton("Aceptar", null)
      val dialog: AlertDialog = builder.create();
      dialog.show();
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
    if(this.activity?.isDestroyed == false && this.activity?.isFinishing == false) {
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
}