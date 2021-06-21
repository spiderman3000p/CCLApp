package com.tautech.cclapp.activities

import android.app.PendingIntent
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tautech.cclapp.R
import com.tautech.cclapp.adapters.PlanificationAdapter
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.activity_planifications.*
import kotlinx.android.synthetic.main.content_scrolling.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException

class UrbanPlanificationsActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {
    private var searchStr: String = ""
    val TAG = "URBAN_PLANIFICATIONS_ACTIVITY"
    private var retrofitClient: Retrofit? = null
    private var planifications: MutableList<Planification> = mutableListOf()
    private var mAdapter: PlanificationAdapter? = null
    var db: AppDatabase? = null
    private var mStateManager: AuthStateManager? = null
    private val viewModel: UrbanPlanificationsActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planifications)
        Log.i(TAG, "onCreate()...")
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout).title = title
        retrofitClient = CclClient.getInstance()
        mStateManager = AuthStateManager.getInstance(this)
        db = AppDatabase.getDatabase(this)
        val config = Configuration.getInstance(this)
        if (config.hasConfigurationChanged()) {
            CclUtilities.getInstance().showAlert(this,"Error", "La configuracion de sesion ha cambiado. Se cerrara su sesion", this::signOut)
            return
        }
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
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "Fetching data...")
        mStateManager?.current?.performActionWithFreshTokens(mStateManager?.mAuthService!!,
            callback)
    }

    private fun fetchUserPlanifications(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        // val url = "planification/byDriver/2;planificationType-filterType=text;planificationType-type=equals;planificationType-filter=urban;startRow=0;endRow=1000;sort-planificationDate=desc"
        val url = "planificationVO2s/search/findByDriverIdAndPlanificationType?driverId=${mStateManager?.driverInfo?.id}&planificationType=Urban"
        Log.i(TAG, "planifications endpoint: $url")
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        showLoader()
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getPlanifications(url, "Bearer $accessToken").execute()
                    val response = call.body()
                    val planifications = response?._embedded?.planificationVO2s?.filter{
                        it.state == "Dispatched" || it.state == "OnGoing"
                    } ?: listOf()
                    viewModel.planifications.postValue(planifications.toMutableList())
                    Log.i(TAG, "planifications response with retrofit: $planifications")
                    hideLoader()
                    if (planifications.isEmpty()) {
                        uiThread {
                            messageTv.text = getText(R.string.no_planifications)
                            messageTv.visibility = View.VISIBLE
                        }
                    } else {
                        try {
                            db?.planificationDao()?.deleteAllByType("Urban")
                            db?.planificationDao()?.insertAll(planifications)
                        } catch (ex: SQLiteException) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            uiThread {
                                CclUtilities.getInstance().showAlert(this@UrbanPlanificationsActivity,getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                            }
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            uiThread {
                                CclUtilities.getInstance().showAlert(this@UrbanPlanificationsActivity,getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            uiThread {
                                CclUtilities.getInstance().showAlert(this@UrbanPlanificationsActivity,getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                            }
                        }
                        uiThread {
                            messageTv.visibility = View.GONE
                        }
                    }
                } catch (ioEx: IOException) {
                    hideLoader()
                    showSnackbar(getString(R.string.error_fetching_planifications))
                    uiThread {
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
                        Log.e(TAG, "Socket timeout exception: ", e)
                    }
                }
            }
        }
    }

    private fun fetchBanks(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        val url = "banks"
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getBanks(url, "Bearer $accessToken").execute()
                    val response = call.body()
                    response?._embedded?.banks?.let{ banks ->
                        db?.bankDao()?.insertAll(banks)
                    }
                    Log.i(TAG, "banks response: $response")
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    e.printStackTrace()
                    showSnackbar(getString(R.string.error_fetching_banks))
                    Log.e(TAG, "exception: ", e)
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        runOnUiThread {
            if(!isFinishing && !isDestroyed) {
                Snackbar.make(cordinator,
                    message,
                    Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
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
                    CclUtilities.getInstance().showAlert(this,"Error",
                        "No se pudo finalizar la sesion",
                        this::signOut)
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
                CclUtilities.getInstance().showAlert(this,"Error",
                    "No se pudo finalizar la sesion remota",
                    this::signOut)
            }
        }
    }

    fun initData() {
        planifications = arrayListOf()
        initAdapter()
    }

    fun initAdapter() {
        runOnUiThread{
            swiperefresh.setOnRefreshListener {
                fetchData(this::fetchUserPlanifications)
            }
            mAdapter = PlanificationAdapter(planifications, this)
            planificationsRv.layoutManager = LinearLayoutManager(this)
            planificationsRv.adapter = mAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "on resume...")
        initData()
        mStateManager?.revalidateSessionData(this)
        if (mStateManager?.keycloakUser != null && mStateManager?.driverInfo != null) {
            if (viewModel.planifications.value.isNullOrEmpty()) {
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
                }
            }
            fetchData(this::fetchBanks)
        } else {
            CclUtilities.getInstance().showAlert(this,"User Data Error", "Some user data are wrong or empty.", this::signOut)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        /*startActivity(Intent(this, DashboardActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()*/
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.default_menu, menu)
        val searchMenuItem = menu.findItem(R.id.search)
        searchMenuItem.isVisible = true
        val searchView = searchMenuItem.actionView as androidx.appcompat.widget.SearchView
        searchView.setQuery(searchStr, false)
        searchView.setOnQueryTextListener( object: SearchView.OnQueryTextListener,
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
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
        Log.i(TAG, "filtering planifications by $query...")
        planifications.clear()
        val filtered = viewModel.planifications.value?.filter{
            Log.i(TAG, "examining planification ${it.id}: $it")
            query == null || it.dispatchDate?.toLowerCase()?.contains(query) == true ||
                    it.label?.toLowerCase()?.contains(query) == true || it.planificationType?.toLowerCase()?.contains(query) == true ||
                    it.state?.toLowerCase()?.contains(query) == true || it.licensePlate?.toLowerCase()?.contains(query) == true
        } ?: arrayListOf()
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
    }
}