package com.tautech.cclapp.activities

import android.content.Context
import android.content.Intent
import android.database.sqlite.*
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.snackbar.Snackbar
import com.tautech.cclapp.R
import com.tautech.cclapp.adapters.PlanificationAdapter
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.*
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.activity_dashboard.*
import kotlinx.android.synthetic.main.activity_planifications.*
import kotlinx.android.synthetic.main.content_scrolling.*
import net.openid.appauth.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
const val KEY_USER_INFO = "userInfo"
const val KEY_PROFILE_INFO = "profileInfo"
const val KEY_DRIVER_INFO = "driverInfo"
class PlanificationsActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {
    val TAG = "PLANIFICATIONS_ACTIVITY"
    var searchStr: String = ""
    private var retrofitClient: Retrofit? = null
    private var planifications: MutableList<Planification> = mutableListOf()
    private var mAdapter: PlanificationAdapter? = null
    var db: AppDatabase? = null
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null
    private val viewModel: PlanificationsActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planifications)
        setSupportActionBar(findViewById(R.id.toolbar))

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout).title = title
        swiperefresh.setOnRefreshListener {
            fetchData(this@PlanificationsActivity::fetchUserPlanifications)
        }
        /*FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            // Get new FCM registration token
            val token = task.result
            // Log and toast
            Log.d(TAG, token)
            Toast.makeText(baseContext, token, Toast.LENGTH_SHORT).show()
        })*/
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this)
        mConfiguration = Configuration.getInstance(this)
        db = AppDatabase.getDatabase(this)
        val config = Configuration.getInstance(this)
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
        initData()
        Log.i(TAG, "Restoring state...")
        /*if (savedInstanceState != null) {
            try {
                var jsonString: String? = savedInstanceState.getString(KEY_USER_INFO)
                Log.i(TAG, "recovered user info: $jsonString")
                if (jsonString != null) {
                    AuthStateManager.userInfo = JSONObject(jsonString)
                }
                if (savedInstanceState.containsKey(KEY_PROFILE_INFO)) {
                    AuthStateManager.keycloakUser = savedInstanceState.getSerializable(
                        KEY_PROFILE_INFO) as KeycloakUser
                }
                if (savedInstanceState.containsKey(KEY_DRIVER_INFO)) {
                    AuthStateManager.driverInfo = savedInstanceState.getSerializable(
                        KEY_DRIVER_INFO) as Driver
                }
            } catch (ex: JSONException) {
                Log.e(TAG, "Failed to parse saved user info JSON, discarding", ex)
            } catch (ex: JsonSyntaxException) {
                Log.e(TAG, "Failed to parse saved user info JSON, discarding", ex)
            }
        }
        else*/ if (/*AuthStateManager.userInfo != null && */AuthStateManager.keycloakUser != null && AuthStateManager.driverInfo != null) {
            fetchData(this::fetchUserPlanifications)
        } else {
            runOnUiThread {
                //Log.i(TAG, "userInfo ${AuthStateManager.userInfo}")
                Log.i(TAG, "keycloakUser ${AuthStateManager.keycloakUser}")
                Log.i(TAG, "driverInfo ${AuthStateManager.driverInfo}")
            }
            showAlert("User Data Error", "Some user data are wrong or empty.", false)
            startActivity(Intent(this, DashboardActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        initAdapter()
        viewModel.planifications.observe(this, Observer{_planifications ->
            planifications.clear()
            planifications.addAll(_planifications)
            mAdapter?.notifyDataSetChanged()
        })
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        // user info is retained to survive activity restarts, such as when rotating the
        // device or switching apps. This isn't essential, but it helps provide a less
        // jarring UX when these events occur - data does not just disappear from the view.
        if (AuthStateManager.keycloakUser != null) {
            state.putSerializable(KEY_PROFILE_INFO, AuthStateManager.keycloakUser)
        }
        if (AuthStateManager.driverInfo != null) {
            state.putSerializable(KEY_DRIVER_INFO, AuthStateManager.driverInfo)
        }
        /*if (AuthStateManager.userInfo != null) {
            state.putString(KEY_USER_INFO, AuthStateManager.userInfo.toString())
        }*/
        // TODO: hacer posible guardar las planificaciones
        /*if (planifications.size > 0) {
            state.putParcelableArray("planifications", planifications)
        }*/
    }

    override fun onDestroy() {
        super.onDestroy()
        mAuthService!!.dispose()
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching data...")
        showLoader()
        try {
            mStateManager?.current?.performActionWithFreshTokens(mAuthService!!,
                callback)
        }catch (ex: AuthorizationException) {
            hideLoader()
            messageTv.text = getText(R.string.parse_planifications_error)
            messageTv.visibility = View.VISIBLE
            showSnackbar("Error fetching data")
            Log.e(TAG, "error fetching data", ex)
        }
    }

    private fun fetchUserPlanifications(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        /*if (AuthStateManager.userInfo == null) {
            Log.e(TAG, "El usuario es nulo")
            //fetchData(this::fetchUserInfo)
            return
        }*/
        if (AuthStateManager.driverInfo == null) {
            Log.e(TAG, "El driver es nulo")
            //fetchData(this@PlanificationsActivity::fetchDriverInfo)
            return
        }
        //val today = DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime())
        //val url = "planification/byDriver/2;planificationType-filterType=text;planificationType-type=equals;planificationType-filter=national;planificationDate-filterType=date;planificationDate-type=equals;planificationDate-dateFrom=$today;startRow=0;endRow=1000;sort-planificationDate=desc"
        val url = "planification/byDriver/2;planificationType-filterType=text;planificationType-type=equals;planificationType-filter=national;startRow=0;endRow=1000;sort-planificationDate=desc"
        Log.i(TAG, "planifications endpoint: $url")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        showLoader()
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getPlanifications(url, "Bearer $accessToken", AuthStateManager.driverInfo.id).execute()
                    val response = call.body()
                    val allPlanifications = response?.content ?: listOf()
                    val planifications = allPlanifications.filter{
                        it.state == "Dispatched" || it.state == "OnGoing"
                    }
                    viewModel.planifications.postValue(planifications.toMutableList())
                    Log.i(TAG, "planification response with retrofit: $planifications")
                    uiThread {
                        mAdapter?.notifyDataSetChanged()
                    }
                    hideLoader()
                    if (planifications.size == 0) {
                        uiThread {
                            messageTv.text = getText(R.string.no_planifications)
                            messageTv.visibility = View.VISIBLE
                        }
                    } else {
                        try {
                            db?.planificationDao()?.insertAll(planifications)
                        } catch (ex: SQLiteException) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                        } catch (ex: Exception) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                        }
                        uiThread {
                            messageTv.visibility = View.GONE
                        }
                    }
                } catch (ioEx: IOException) {
                    uiThread {
                        hideLoader()
                        showSnackbar(getString(R.string.error_fetching_planifications))
                        messageTv.text = getText(R.string.network_error)
                        messageTv.visibility = View.VISIBLE
                        Log.e(TAG, "Network error when querying planifications endpoint", ioEx)
                    }

                } catch (jsonEx: JSONException) {
                    hideLoader()
                    showSnackbar(getString(R.string.error_fetching_planifications))
                    uiThread {
                        messageTv.text = getText(R.string.parse_planifications_error)
                        messageTv.visibility = View.VISIBLE
                        Log.e(TAG, "Failed to parse planifications response", jsonEx)
                    }
                } catch (e: Exception) {
                    hideLoader()
                    showSnackbar(getString(R.string.error_fetching_planifications))
                    uiThread {
                        messageTv.text = getText(R.string.unknown_error)
                        messageTv.visibility = View.VISIBLE
                        Log.e(TAG, "Unknown exception: ", e)
                    }
                } catch (e: SocketTimeoutException) {
                        hideLoader()
                        showSnackbar(getString(R.string.error_fetching_planifications))
                    uiThread {
                        messageTv.text = getText(R.string.unknown_error)
                        messageTv.visibility = View.VISIBLE
                    }
                    Log.e(TAG, "Socket timeout exception: ", e)
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        runOnUiThread {
        Snackbar.make(cordinator,
            message,
            Snackbar.LENGTH_SHORT)
            .show()
        }
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
        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", false)
            commit()
        }
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }

    fun initData() {
        planifications = arrayListOf()
        initAdapter()
    }

    fun initAdapter() {
        runOnUiThread{
            mAdapter = PlanificationAdapter(planifications, this)
            planificationsRv.layoutManager = LinearLayoutManager(this)
            planificationsRv.adapter = mAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "on resume...")
        initData()
        if (AuthStateManager.keycloakUser != null && AuthStateManager.driverInfo != null) {
            if (planifications.isNullOrEmpty()) {
                fetchData(this::fetchUserPlanifications)
            } else {
                showLoader()
                doAsync {
                    val allPlanifications = db?.planificationDao()?.getAllByTypeAndDriver("Urban", mStateManager?.driverInfo?.id)
                    val planifications = allPlanifications?.filter{
                        it.state == "Dispatched" || it.state == "OnGoing"
                    }
                    viewModel.planifications.value?.clear()
                    viewModel.planifications.postValue(planifications?.toMutableList())
                    Log.i(TAG, "planifications loaded from local DB: $planifications")
                    hideLoader()
                    uiThread {
                        mAdapter?.notifyDataSetChanged()
                    }
                }
            }
        } else {
            showAlert("User Data Error", "Some user data are wrong or empty.", true)
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
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun hideLoader() {
        runOnUiThread {
            swiperefresh.isRefreshing = false
            planificationsRv.visibility = View.VISIBLE
        }
    }

    fun showLoader() {
        runOnUiThread{
            swiperefresh.isRefreshing = true
            messageTv.visibility = View.GONE
            planificationsRv.visibility = View.GONE
        }
    }

    fun showAlert(title: String, message: String, exitToLogin: Boolean = false) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setPositiveButton("Aceptar", null)
            val dialog: AlertDialog = builder.create();
            dialog.show();
            dialog.setOnDismissListener {
                if (exitToLogin) {
                    signOut()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.default_menu, menu)
        val searchMenuItem = menu.findItem(R.id.search)
        searchMenuItem.isVisible = true
        val searchView = searchMenuItem.actionView as androidx.appcompat.widget.SearchView
        searchView.setQuery(searchStr, false)
        searchView.setOnQueryTextListener( object: SearchView.OnQueryTextListener, OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Toast like print
                Log.i(TAG, "query submit $query")
                if( ! searchView.isIconified()) {
                    searchView.setIconified(true)
                }
                filterPlanifications(query?.toLowerCase())
                searchMenuItem.collapseActionView()
                return true
            }
            override fun onQueryTextChange(s: String): Boolean {
                Log.i(TAG, "text change query $s")
                return true
            }
        })
        return true
    }

    private fun filterPlanifications(query: String?) {
        Log.i(TAG, "filtering planifications")
        planifications.clear()
        val filtered = viewModel.planifications.value?.filter{
            query == null || it.address?.toLowerCase()?.contains(query) == true || it.customerName?.toLowerCase()?.contains(query) == true ||
                    it.date?.toLowerCase()?.contains(query) == true || it.driverName?.toLowerCase()?.contains(query) == true ||
                    it.label?.toLowerCase()?.contains(query) == true || it.planificationType?.toLowerCase()?.contains(query) == true ||
                    it.state?.toLowerCase()?.contains(query) == true || it.vehicleLicensePlate?.toLowerCase()?.contains(query) == true ||
                    it.vehicleType?.toLowerCase()?.contains(query) == true
        }!!
        Log.i(TAG, "filtered results ${filtered.size}")
        planifications.addAll(filtered)
        searchStr = query ?: ""
        mAdapter?.notifyDataSetChanged()
        invalidateOptionsMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.default_action -> {
                Log.i(TAG, "solicitando logout...")
                signOut()
                true
            }
            R.id.search -> {

                true
            }
            R.id.clear_filter -> {
                filterPlanifications(null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        searchStr.let {
            val clearFilterMenuItem = menu?.findItem(R.id.clear_filter)
            clearFilterMenuItem?.isVisible = it.isNotEmpty()
        }
        return true
    }

    override fun onRefresh() {
        searchStr = ""
        fetchData(this@PlanificationsActivity::fetchUserPlanifications)
    }
}