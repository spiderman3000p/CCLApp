package com.tautech.cclapp.activities

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.interfaces.KeycloakDataService
import com.tautech.cclapp.services.CclClient
import com.tautech.cclapp.services.KeycloakClient
import org.json.JSONException
import retrofit2.Retrofit
import kotlinx.android.synthetic.main.activity_dashboard.*
import net.openid.appauth.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.joda.time.format.DateTimeFormat
import java.io.IOException
import java.net.SocketTimeoutException

class DashboardActivity: AppCompatActivity() {
    /*val KEY_USER_INFO = "userInfo"
    val KEY_PROFILE_INFO = "profileInfo"
    val KEY_DRIVER_INFO = "driverInfo"*/
    val TAG = "DASHBOARD_ACTIVITY"
    private var retrofitClient: Retrofit? = null
    var db: AppDatabase? = null
    /*private var mUserInfoJson: JSONObject? = null
    private var userInfo: KeycloakUser? = null
    private var driverInfo: Driver? = null*/
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this)
        mConfiguration = Configuration.getInstance(this)
        val config = Configuration.getInstance(this)
        try {
            db = AppDatabase.getDatabase(this)
        } catch(ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        if (config.hasConfigurationChanged()) {
            Toast.makeText(
                this,
                "Configuration change detected",
                Toast.LENGTH_SHORT)
                .show()
            signOut()
            return
        }
        mAuthService = AuthorizationService(
            this,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build())
        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        /*if (response != null || ex != null) {
            if (ex != null) {
                Log.e(TAG, "Authorization exception. token exchange is imposible", ex)
            }
            if (response != null) {
                Log.i(TAG, "Authorization response is not null.")
            }
            mStateManager!!.updateAfterAuthorization(response, ex)
        }*/
        try {
            db = AppDatabase.getDatabase(this)
        } catch(ex: SQLiteDatabaseLockedException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteAccessPermException) {
            Log.e(TAG, "Database error found", ex)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            Log.e(TAG, "Database error found", ex)
        }
        if (response != null && response.authorizationCode != null) {
            // authorization code exchange is required
            Log.i(TAG, "exchanging token...")
            mStateManager!!.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            Log.e(TAG, "Authorization flow failed: " + ex.message, ex)
            refreshAccessToken()
        } else if (mStateManager?.current?.isAuthorized == true) {
            displayAuthorized()
        }
        menuOptions.visibility = View.INVISIBLE
        urbanBtnTv.setOnClickListener{
            val intent = Intent(this, UrbanPlanificationsActivity::class.java)
            startActivity(intent)
        }
        nationalBtnTv.setOnClickListener{
            val intent = Intent(this, PlanificationsActivity::class.java)
            startActivity(intent)
        }
        messageTv2.setOnClickListener{
            displayAuthorized()
        }
        /*
        if (savedInstanceState != null && mStateManager?.current == null) {
            try {
                val jsonString: String? = savedInstanceState.getString(KEY_USER_INFO)
                if (jsonString != null && mStateManager?.userInfo == null) {
                    mStateManager?.userInfo = JSONObject(jsonString)
                    Log.i(TAG, "se recupero user info de savedInstanceState: ${mStateManager?.userInfo}")
                }
                if (savedInstanceState.containsKey(KEY_PROFILE_INFO) && mStateManager?.keycloakUser == null) {
                    mStateManager?.keycloakUser = savedInstanceState.getSerializable(KEY_PROFILE_INFO) as KeycloakUser
                    Log.i(TAG, "se recupero usuario keycloak de savedInstanceState: ${mStateManager?.keycloakUser}")
                }
                if (savedInstanceState.containsKey(KEY_DRIVER_INFO) && mStateManager?.driverInfo == null) {
                    mStateManager?.driverInfo = savedInstanceState.getSerializable(KEY_DRIVER_INFO) as Driver
                    Log.i(TAG, "se recupero driver de savedInstanceState: ${mStateManager?.driverInfo}")
                }
            } catch (ex: JSONException) {
                Log.e(TAG, "Failed to parse saved user info JSON, discarding", ex)
            }
        } */
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching data...")
        showLoader()
        try {
            mStateManager?.current?.performActionWithFreshTokens(mAuthService!!,
                callback)
        }catch (ex: AuthorizationException) {
            hideLoader()
            runOnUiThread {
                messageTv2.text = getText(R.string.parse_planifications_error)
                messageTv2.visibility = View.VISIBLE
            }
            showSnackbar("Error fetching data")
            Log.e(TAG, "error fetching data", ex)
        }
    }

    private fun displayAuthorized() {
        val state = mStateManager!!.current
        Log.i(TAG, "token: ${state.idToken ?: "no id token returned"}")
        if (state.accessToken == null) {
            Log.e(TAG, "token: ${state.accessToken ?: "no access token returned"}")
            refreshAccessToken()
            return
        } else {
            val expiresAt = state.accessTokenExpirationTime
            if (expiresAt == null) {
                Log.i(TAG, "no access token expiry")
            } else if (expiresAt < System.currentTimeMillis()) {
                Log.i(TAG, "access token expired")
                showSnackbar("Access token expired")
                refreshAccessToken()
            } else {
                Log.i(TAG, "access token expires at: ${
                    DateTimeFormat
                        .forPattern("yyyy-MM-dd HH:mm:ss ZZ").print(expiresAt)
                }")
                /*if (AuthStateManager.userInfo == null) {
                    Log.i(TAG, "No hay datos de user info guardados, solicitando user info...")
                    fetchData(this::fetchUserInfo)
                } else*/ if (AuthStateManager.keycloakUser == null) {
                    Log.i(TAG, "No hay datos de keycloak user guardados, solicitando keycloak user data...")
                    fetchData(this::fetchUserProfile)
                }  else if (AuthStateManager.driverInfo == null) {
                    Log.i(TAG, "No hay datos de driver guardados, solicitando datos de driver...")
                    fetchData(this::fetchDriverInfo)
                } else {
                    runOnUiThread {
                        messageTv2.visibility = View.GONE
                        menuOptions.visibility = View.VISIBLE
                    }
                    hideLoader()
                }
            }
        }
    }

    @MainThread
    private fun refreshAccessToken() {
        showLoader()
        performTokenRequest(
            mStateManager!!.current.createTokenRefreshRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleAccessTokenResponse(tokenResponse,
                authException)
        }
    }

    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        showLoader()
        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(tokenResponse,
                authException)
        }
    }

    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback,
    ) {
        val clientAuthentication: ClientAuthentication
        clientAuthentication = try {
            mStateManager!!.current.clientAuthentication
        } catch (ex: ClientAuthentication.UnsupportedAuthenticationMethod) {
            Log.d(TAG,
                "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)",
                ex)
            Log.e(TAG, "Client authentication method is unsupported")
            return
        }
        mAuthService!!.performTokenRequest(
            request,
            clientAuthentication,
            callback)
    }

    @MainThread
    private fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?,
    ) {
        if (authException != null) {
            hideLoader()
            messageTv2.text = "Error loading user data. Touch here to try again"
            messageTv2.visibility = View.VISIBLE
            Log.e(TAG, "Exception trying to fetch token", authException)
        } else {
            mStateManager!!.updateAfterTokenResponse(tokenResponse, authException)
            runOnUiThread{
                displayAuthorized()
            }
        }
    }

    @MainThread
    private fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?,
    ) {
        mStateManager!!.updateAfterTokenResponse(tokenResponse, authException)
        if (!mStateManager!!.current.isAuthorized) {
            val message = ("Authorization Code exchange failed "
                    + if (authException != null) authException.error else "")
            signOut()
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
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = mStateManager!!.current
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        mStateManager!!.replace(clearedState)
        val mainIntent = Intent(this, LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
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

    private fun fetchUserProfile(accessToken: String?, idToken: String?, ex: AuthorizationException?
    ) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching user profile", ex)
            AuthStateManager.keycloakUser = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {

            }
            showAlert("Error", "Sesion Expirada", true)
            return
        }
        val userProfileEndpoint = mConfiguration!!.userProfileEndpointUri.toString()
        Log.i(TAG, "constructed user profile endpoint: $userProfileEndpoint")
        val dataService: KeycloakDataService? = KeycloakClient.getInstance()?.create(
            KeycloakDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    showLoader()
                    val call = dataService.getUserProfile(userProfileEndpoint,
                        "Bearer $accessToken")
                        .execute()
                    val response = call.body()
                    hideLoader()
                    Log.i(TAG, "user profile response $response")
                    if (response != null) {
                        AuthStateManager.keycloakUser = response
                        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                        val gson = Gson()
                        with(sharedPref.edit()) {
                            putString("keycloakUserJSON", gson.toJson(response))
                            commit()
                        }
                        Log.i(TAG, "user profile fetched ${AuthStateManager.keycloakUser}")
                        if (AuthStateManager.keycloakUser.attributes?.userType?.get(0)
                                .equals("driver")
                        ) {
                            if (AuthStateManager.driverInfo == null) {
                                fetchData(this@DashboardActivity::fetchDriverInfo)
                            }
                        } else {
                            showAlert("Error", "Tu usuario no es de tipo conductor")
                            return@doAsync
                        }
                    }
                } catch(toe: SocketTimeoutException) {

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
                    messageTv2.text = getText(R.string.fetching_profile_error)
                    messageTv2.visibility = View.VISIBLE
                } catch (jsonEx: JSONException) {
                    Log.e(TAG, "Failed to parse user profile response", jsonEx)
                    messageTv2.text = getText(R.string.fetching_profile_error)
                    messageTv2.visibility = View.VISIBLE
                }
            }
        }
    }

    fun fetchDriverInfo(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching driver info", ex)
            AuthStateManager.driverInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
            }
            showAlert("Error", "Sesion Expirada", true)
            return
        }
        if (AuthStateManager.keycloakUser == null) {
            Log.e(TAG, "El usuario keycloak es null. No se puede obtener datos del conductor")
            return
        }
        val url = "drivers/${AuthStateManager.keycloakUser.attributes?.driverId?.get(0)}"
        Log.i(TAG, "driver info endpoint: ${url}")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)

        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    showLoader()
                    val call = dataService.getDriverInfo(url, "Bearer $accessToken").execute()
                    val response = call.body()
                    hideLoader()
                    if (response != null) {
                        AuthStateManager.driverInfo = response
                        Log.i(TAG, "driver info response ${AuthStateManager.driverInfo}")
                        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                        val gson = Gson()
                        with(sharedPref.edit()) {
                            putString("driverInfoJSON", gson.toJson(AuthStateManager.driverInfo))
                            commit()
                        }
                        uiThread {
                            displayAuthorized()
                        }
                    } else {
                        Log.e(TAG, "la respuesta de driver info es null")
                        return@doAsync
                    }
                    Log.i(TAG, "driver info fetched: ${AuthStateManager.driverInfo}")
                } catch(toe: SocketTimeoutException) {
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
                }
            }
        }
    }

    /*private fun fetchUserInfo(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching user info", ex)
            AuthStateManager.userInfo = null
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
            }
            runOnUiThread {
                showAlert("Error", "Sesion Expirada", true)
            }
            return
        }
        val userInfoEndpoint = mConfiguration!!.userInfoEndpointUri.toString()
        //Log.i(TAG, "constructed user endpoint: $userInfoEndpoint")
        val dataService: KeycloakDataService? = KeycloakClient().getInstance()?.create(
            KeycloakDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    showLoader()
                    val call = dataService.getUserInfo(userInfoEndpoint,
                        "Bearer $accessToken")
                        .execute()
                    val response = call.body()
                    hideLoader()
                    Log.i(TAG, "user info response $response")
                    if (response != null && response.has("id")) {
                        //AuthStateManager.userInfo = response
                        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                        val gson = Gson()
                        with(sharedPref.edit()) {
                            putString("userInfo", gson.toJson(response))
                            commit()
                        }
                        //Log.i(TAG, "user info fetched ${AuthStateManager.userInfo}")
                        if (AuthStateManager.keycloakUser == null) {
                            fetchData(this@DashboardActivity::fetchUserProfile)
                        }
                    }
                } catch(toe: SocketTimeoutException) {
                    uiThread {
                        hideLoader()
                        showSnackbar("Fetching user profile failed")
                        messageTv2.text = getText(R.string.fetching_userinfo_error)
                        messageTv2.visibility = View.VISIBLE
                        Log.e(TAG, "Network error when querying planification lines endpoint", toe)
                    }
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when querying userinfo endpoint",
                        ioEx)
                    messageTv2.text = getText(R.string.fetching_userinfo_error)
                    messageTv2.visibility = View.VISIBLE
                } catch (jsonEx: JSONException) {
                    Log.e(TAG, "Failed to parse userinfo response", jsonEx)
                    messageTv2.text = getText(R.string.fetching_userinfo_error)
                    messageTv2.visibility = View.VISIBLE
                }
            }
        }
    }*/

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
                        signOut()
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
}