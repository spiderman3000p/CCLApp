package com.tautech.cclapp.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.interfaces.KeycloakDataService
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.KeycloakClient
import kotlinx.android.synthetic.main.activity_dashboard.*
import net.openid.appauth.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.joda.time.format.DateTimeFormat
import org.json.JSONException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException

class DashboardActivity: AppCompatActivity() {
    val TAG = "DASHBOARD_ACTIVITY"
    private var retrofitClient: Retrofit? = null
    var db: AppDatabase? = null
    private var mStateManager: AuthStateManager? = null
    private var isLoadingUserProfile: Boolean = false
    private var isLoadingDriverInfo: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this)
        try {
            db = AppDatabase.getDatabase(this)
        } catch (ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        when {
            response?.authorizationCode != null -> {
                // authorization code exchange is required
                Log.i(TAG, "exchanging token...")
                mStateManager?.updateAfterAuthorization(response, ex)
                exchangeAuthorizationCode(response)
            }
            ex != null -> {
                Log.e(TAG, "Authorization flow failed: " + ex.message, ex)
                mStateManager?.refreshAccessToken()
            }
            mStateManager?.current?.isAuthorized == true -> {
                displayAuthorized()
            }
        }
        menuOptions.visibility = View.INVISIBLE
        urbanBtnTv.setOnClickListener{
            val intent = Intent(this, UrbanPlanificationsActivity::class.java)
            //intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
        nationalBtnTv.setOnClickListener{
            val intent = Intent(this, PlanificationsActivity::class.java)
            //intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            //finish()
        }
        messageTv2.setOnClickListener{
            displayAuthorized()
        }
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching data...")
        showLoader()
        mStateManager!!.current.performActionWithFreshTokens(mStateManager!!.mAuthService,
            callback)
    }

    private fun displayAuthorized() {
        val state = mStateManager!!.current
        Log.i(TAG, "token: ${state.idToken ?: "no id token returned"}")
        if (state.accessToken == null) {
            Log.e(TAG, "token: ${state.accessToken ?: "no access token returned"}")
            mStateManager?.refreshAccessToken()
            return
        } else {
            val expiresAt = state.accessTokenExpirationTime
            when {
                expiresAt == null -> {
                    Log.i(TAG, "no access token expiry")
                }
                expiresAt < System.currentTimeMillis() -> {
                    Log.i(TAG, "access token expired")
                    showSnackbar("Access token expired")
                    mStateManager?.refreshAccessToken()
                }
                else -> {
                    Log.i(TAG, "access token expires at: ${
                        DateTimeFormat
                            .forPattern("yyyy-MM-dd HH:mm:ss ZZ").print(expiresAt)
                    }")
                    when {
                        mStateManager?.keycloakUser == null && !isLoadingUserProfile -> {
                            Log.i(TAG,
                                "No hay datos de keycloak user guardados, solicitando keycloak user data...")
                            fetchData(this::fetchUserProfile)
                        }
                        mStateManager?.driverInfo == null && !isLoadingDriverInfo-> {
                            Log.i(TAG,
                                "No hay datos de driver guardados, solicitando datos de driver...")
                            fetchData(this::fetchDriverInfo)
                        }
                        mStateManager?.driverInfo != null && mStateManager?.keycloakUser != null -> {
                            runOnUiThread {
                                messageTv2.visibility = View.GONE
                                menuOptions.visibility = View.VISIBLE
                            }
                            hideLoader()
                        }
                    }
                }
            }
        }
    }

    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        showLoader()
        mStateManager?.performTokenRequest(
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(tokenResponse,
                authException)
        }
    }

    @MainThread
    private fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?,
    ) {
        mStateManager!!.updateAfterTokenResponse(tokenResponse, authException)
        if (!mStateManager!!.current.isAuthorized) {
            showAlert("Error", "Authorization Code exchange failed ", true)
        } else {
            runOnUiThread{
                displayAuthorized()
            }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        /*Log.i(TAG, "Pausando la actividad. Guardando datos...")
        // user info is retained to survive activity restarts, such as when rotating the
        // device or switching apps. This isn't essential, but it helps provide a less
        // jarring UX when these events occur - data does not just disappear from the view.
        if (mStateManager?.userInfo != null) {
            Log.i(TAG, "Guardando keycloak user ${mStateManager?.userInfo}")
            state.putSerializable(KEY_PROFILE_INFO, mStateManager?.keycloakUser)
        }
        if (mStateManager?.driverInfo != null) {
            Log.i(TAG, "Guardando driver info ${mStateManager?.driverInfo}")
            state.putSerializable(KEY_DRIVER_INFO, mStateManager?.driverInfo)
        }
        if (mStateManager?.userInfo != null) {
            Log.i(TAG, "Guardando user info ${mStateManager?.userInfo}")
            state.putString(KEY_USER_INFO, mStateManager?.userInfo.toString())
        }*/
    }

    private fun signOut() {
        if (mStateManager != null && mStateManager?.current != null && mStateManager?.mConfiguration?.redirectUri != null &&
            mStateManager?.current?.idToken != null && mStateManager?.current?.authorizationServiceConfiguration != null) {
            val endSessionRequest = EndSessionRequest.Builder(
                mStateManager?.current?.authorizationServiceConfiguration!!,
                mStateManager?.current?.idToken!!,
                mStateManager?.mConfiguration?.redirectUri!!
            ).build()
            if (endSessionRequest != null) {
                val authService = AuthorizationService(this)
                val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
                startActivityForResult(endSessionIntent, AuthStateManager.RC_END_SESSION)
            } else {
                showSnackbar("Error al intentar cerrar sesion")
            }
        } else {
            Log.i(TAG,
                "mStateManager?.mConfiguration?.redirectUri: ${mStateManager?.mConfiguration?.redirectUri}")
            Log.i(TAG, "mStateManager?.current?.idToken: ${mStateManager?.current?.idToken}")
            Log.i(TAG,
                "mStateManager?.current?.authorizationServiceConfiguration: ${mStateManager?.current?.authorizationServiceConfiguration}")
            showSnackbar("Error al intentar cerrar sesion")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AuthStateManager.RC_END_SESSION) {
            val resp: EndSessionResponse = EndSessionResponse.fromIntent(data!!)!!
            val ex = AuthorizationException.fromIntent(data)
            Log.i(TAG, "logout response: $resp")
            if (resp != null) {
                if (ex != null) {
                    Log.e(TAG, "Error al intentar finalizar sesion", ex)
                    showAlert("Error",
                        "No se pudo finalizar la sesion",
                        true)
                } else {
                    mStateManager?.signOut(this)
                    val mainIntent = Intent(this,
                        LoginActivity::class.java)
                    mainIntent.flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(mainIntent)
                    finish()
                }
            } else {
                Log.e(TAG, "Error al intentar finalizar sesion", ex)
                showAlert("Error",
                    "No se pudo finalizar la sesion remota",
                    true)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.default_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.default_action -> {
                Log.i(TAG, "solicitando logout...")
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchUserProfile(
        accessToken: String?, idToken: String?, ex: AuthorizationException?,
    ) {
        if (ex != null) {
            Log.e(TAG,
                "ocurrio una excepcion mientras se recuperaban detalles del perfil de usuario",
                ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", true)
            }
            return
        }
        val userProfileEndpoint = mStateManager?.mConfiguration?.userProfileEndpointUri.toString()
        Log.i(TAG, "constructed user profile endpoint: $userProfileEndpoint")
        val dataService: KeycloakDataService? = KeycloakClient.getInstance()?.create(
            KeycloakDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    isLoadingUserProfile = true
                    showLoader()
                    val call = dataService.getUserProfile(userProfileEndpoint,
                        "Bearer $accessToken")
                        .execute()
                    val response = call.body()
                    isLoadingUserProfile = false
                    hideLoader()
                    Log.i(TAG, "user profile response $response")
                    if (response != null) {
                        mStateManager?.keycloakUser = response
                        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                        val gson = Gson()
                        with(sharedPref.edit()) {
                            putString("keycloakUserJSON", gson.toJson(response))
                            commit()
                        }
                        Log.i(TAG, "user profile fetched ${mStateManager?.keycloakUser}")
                        if (mStateManager?.keycloakUser?.attributes?.userType?.get(0)
                                .equals("driver")
                        ) {
                            if (mStateManager?.driverInfo == null) {
                                fetchData(this@DashboardActivity::fetchDriverInfo)
                            }
                        } else {
                            showAlert("Error", "Tu usuario no es de tipo conductor")
                            return@doAsync
                        }
                    }
                } catch (toe: SocketTimeoutException) {
                        hideLoader()
                        showSnackbar("Network Error fetching user profile")
                    uiThread {
                        messageTv2.text = getText(R.string.fetching_profile_error)
                        messageTv2.visibility = View.VISIBLE
                        Log.e(TAG, "Network error when querying planification lines endpoint", toe)
                    }
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when querying user profile endpoint",
                        ioEx)
                    uiThread {
                        messageTv2.text = getText(R.string.fetching_profile_error)
                        messageTv2.visibility = View.VISIBLE
                    }
                } catch (jsonEx: JSONException) {
                    Log.e(TAG, "Failed to parse user profile response", jsonEx)
                    uiThread {
                        messageTv2.text = getText(R.string.fetching_profile_error)
                        messageTv2.visibility = View.VISIBLE
                    }
                } finally {
                    isLoadingUserProfile = true
                }
            }
        }
    }

    fun fetchDriverInfo(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion mientras se recuperaban detalles del conductor", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                showAlert("Sesion expirada", "Su sesion ha expirado", true)
            }
            return
        }
        val url = "drivers/${mStateManager?.keycloakUser?.attributes?.driverId?.get(0)}"
        Log.i(TAG, "driver info endpoint: ${url}")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    isLoadingDriverInfo = true
                    showLoader()
                    val call = dataService.getDriverInfo(url, "Bearer $accessToken").execute()
                    val response = call.body()
                    isLoadingDriverInfo = false
                    hideLoader()
                    if (response != null) {
                        mStateManager?.driverInfo = response
                        Log.i(TAG, "driver info response ${mStateManager?.driverInfo}")
                        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                        val gson = Gson()
                        with(sharedPref.edit()) {
                            putString("driverInfoJSON", gson.toJson(mStateManager?.driverInfo))
                            commit()
                        }
                        uiThread {
                            displayAuthorized()
                        }
                    } else {
                        Log.e(TAG, "la respuesta de driver info es null")
                        return@doAsync
                    }
                    Log.i(TAG, "driver info fetched: ${mStateManager?.driverInfo}")
                } catch (toe: SocketTimeoutException) {
                    hideLoader()
                    showSnackbar("Fetching driver failed")
                    uiThread {
                        messageTv2.text = getText(R.string.fetching_driver_error)
                        messageTv2.visibility = View.VISIBLE
                        Log.e(TAG, "Network error when querying planification lines endpoint", toe)
                    }
                } catch (ioEx: IOException) {
                    hideLoader()
                    showSnackbar("Fetching driver failed")
                    uiThread {
                        messageTv2.text = getText(R.string.fetching_driver_error)
                        messageTv2.visibility = View.VISIBLE
                        Log.e(TAG,
                            "Network error when querying driver info endpoint",
                            ioEx)
                    }
                } catch (jsonEx: JSONException) {
                    hideLoader()
                    showSnackbar("Fetching driver failed")
                    uiThread {
                        messageTv2.text = getText(R.string.fetching_driver_error)
                        messageTv2.visibility = View.VISIBLE
                        Log.e(TAG, "Failed to parse driver response")
                    }
                } catch (e: Exception) {
                    hideLoader()
                    showSnackbar("Fetching driver failed")
                    uiThread {
                        messageTv2.text = getText(R.string.fetching_driver_error)
                        messageTv2.visibility = View.VISIBLE
                        Log.e(TAG, "Unknown exception: ", e)
                    }
                } finally {
                    isLoadingDriverInfo = false
                }
            }
        }
    }

    fun hideLoader() {
        runOnUiThread {
            progressBar2.visibility = View.GONE
        }
    }

    fun showLoader() {
        runOnUiThread{
            messageTv2.visibility = View.GONE
            progressBar2.visibility = View.VISIBLE
        }
    }

    fun showAlert(title: String, message: String, exitToLogin: Boolean = false) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setPositiveButton("Aceptar", null)
            val dialog: AlertDialog = builder.create();
            if (!isFinishing && !isDestroyed) {
                dialog.show();
                dialog.setOnDismissListener {
                    if (exitToLogin) {
                        mStateManager?.signOut(this)
                    }
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Snackbar.make(container,
                    message,
                    Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mStateManager?.current?.accessToken != null) {
            displayAuthorized()
        }
    }
}