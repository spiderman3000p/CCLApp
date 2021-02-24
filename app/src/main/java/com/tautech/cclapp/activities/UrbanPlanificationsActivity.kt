package com.tautech.cclapp.activities

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.tautech.cclapp.R
import com.tautech.cclapp.adapters.PlanificationAdapter
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.database.AppDatabase
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.Driver
import com.tautech.cclapp.models.KeycloakUser
import com.tautech.cclapp.models.Planification
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.activity_planifications.*
import kotlinx.android.synthetic.main.content_scrolling.*
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
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
            showAlert("Error", "La configuracion de sesion ha cambiado. Se cerrara su sesion", true)
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
                    Log.i(TAG, "planification response with retrofit: $planifications")
                    hideLoader()
                    if (planifications.isEmpty()) {
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
                            uiThread {
                                showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                            }
                        } catch (ex: SQLiteConstraintException) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            uiThread {
                                showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG,
                                "Error saving planifications to local dabase",
                                ex)
                            uiThread {
                                showAlert(getString(R.string.database_error), getString(R.string.database_error_saving_planifications))
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
        mStateManager?.signOut(this)
        //finish()
    }

    fun initData() {
        planifications = arrayListOf()
        initAdapter()
    }

    fun initAdapter() {
        runOnUiThread{
            swiperefresh.setOnRefreshListener {
                fetchData(this@UrbanPlanificationsActivity::fetchUserPlanifications)
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
                }
            }
        } else {
            showAlert("User Data Error", "Some user data are wrong or empty.", true)
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
            if(!isFinishing && !isDestroyed) {
                dialog.show();
                dialog.setOnDismissListener {
                    if (exitToLogin) {
                        signOut()
                    }
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
            query == null || it.address?.toLowerCase()?.contains(query) == true || it.dispatchDate?.toLowerCase()?.contains(query) == true ||
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