package com.tautech.cclapp.activities

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
/*import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase*/
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.Configuration
import kotlinx.android.synthetic.main.activity_login.*
import net.openid.appauth.*
import net.openid.appauth.browser.VersionedBrowserMatcher
import java.util.concurrent.CountDownLatch

class LoginActivity : AppCompatActivity() {
    val TAG = "LOGIN_ACTIVITY"
    private val mUsePendingIntents: Boolean = false
    private val EXTRA_FAILED = "failed"
    private val RC_AUTH = 100
    private var mAuthService: AuthorizationService? = null
    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration
    private var mClientId: String? = null
    private var mAuthRequest: AuthorizationRequest? = null
    private var mAuthIntent: CustomTabsIntent? = null
    private var mAuthIntentLatch = CountDownLatch(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        //Splash
        /*Thread.sleep(2000)
        setTheme(R.style.AppTheme)*/

        super.onCreate(savedInstanceState)
        //supportActionBar?.hide()
        setContentView(R.layout.activity_login)
        //analytics event
        /*val analytics: FirebaseAnalytics = Firebase.analytics
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_ITEM) {
            param(FirebaseAnalytics.Param.ITEM_ID, "InitScreen")
        }*/
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
        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        if (!sharedPref.contains("firstRun")) {
            val intent = Intent(this, IntroActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        mAuthStateManager = AuthStateManager.getInstance(this)
        mConfiguration = Configuration.getInstance(this)
        if (!mConfiguration.isValid) {
            //displayError(mConfiguration.getConfigurationError(), false)
            Log.e(TAG, "Error en configuracion de auth manager")
            //return
        }
        if (mConfiguration.hasConfigurationChanged()) {
            // discard any existing authorization state due to the change of configuration
            Log.i(TAG, "Configuration change detected, discarding old state")
            mAuthStateManager.replace(AuthState())
            mConfiguration.acceptConfiguration()
        }

        loginBtn.setOnClickListener {
           // doLogin()
            //getAccessToken()
            doAuth()
        }
        initializeAppAuth()
        if (mAuthStateManager.getCurrent().isAuthorized && !mConfiguration.hasConfigurationChanged()) {
            Log.i(TAG, "User is already authenticated, proceeding to token activity")
            doAuth()
            return
        }
    }

    private fun createAuthRequest() {
        Log.i(TAG, "Creating auth request for login")
        val authRequestBuilder = AuthorizationRequest.Builder(
            mAuthStateManager.current.authorizationServiceConfiguration!!,
            mConfiguration.clientId!!,
            ResponseTypeValues.CODE,
            mConfiguration.redirectUri)
            .setScope(mConfiguration.scope)
        val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        if (sharedPref.contains("isLoggedIn") && !sharedPref.getBoolean("isLoggedIn", false)) {
            authRequestBuilder.setPromptValues("login")
        }
        mAuthRequest = authRequestBuilder.build()
    }

    private fun recreateAuthorizationService() {
        if (mAuthService != null) {
            Log.i(TAG, "Discarding existing AuthService instance")
            mAuthService?.dispose()
        }
        mAuthService = createAuthorizationService()
    }

    private fun createAuthorizationService(): AuthorizationService {
        Log.i(TAG, "Creating authorization service")
        val appAuthConfig = AppAuthConfiguration.Builder()
            .setBrowserMatcher(net.openid.appauth.browser.BrowserWhitelist(
                VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
                VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB))
            .build()
        return AuthorizationService(this, appAuthConfig)
    }

    private fun initializeAppAuth() {
        Log.i(TAG, "Initializing AppAuth")
        recreateAuthorizationService()
        if (mAuthStateManager.current.authorizationServiceConfiguration != null) {
            // configuration is already created, skip to client initialization
            Log.i(TAG, "auth config already established")
            initializeClient()
            return
        }

        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.

        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.
        if (mConfiguration.discoveryUri == null) {
            Log.i(TAG, "Creating auth config from res/raw/auth_config.json")
            val config = AuthorizationServiceConfiguration(
                mConfiguration.authEndpointUri!!,
                mConfiguration.tokenEndpointUri!!,
                mConfiguration.registrationEndpointUri)
            mAuthStateManager.replace(AuthState(config))
            initializeClient()
            return
        }
    }

    private fun initializeClient() {
        if (mConfiguration.clientId != null) {
            Log.i(TAG, "Using static client ID: " + mConfiguration.clientId)
            // use a statically configured client ID
            mClientId = mConfiguration.clientId
            initializeAuthRequest()
            return
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "on activity result")
        if (resultCode == RESULT_CANCELED) {
            Log.e(TAG, "Auth canceled")
        } else {
            val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("isLoggedIn", true)
                commit()
            }
            loginBtn.visibility = View.GONE
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.putExtras(data!!.extras!!)
            startActivity(intent)
            finish()
        }
    }


    private fun initializeAuthRequest() {
        createAuthRequest()
        warmUpBrowser()
    }

    private fun warmUpBrowser() {
        mAuthIntentLatch = CountDownLatch(1)
        Log.i(TAG, "Warming up browser instance for auth request")
        val intentBuilder =
            mAuthService?.createCustomTabsIntentBuilder(mAuthRequest?.toUri())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            intentBuilder?.setToolbarColor(getColor(R.color.material_on_primary_emphasis_medium))
        }
        mAuthIntent = intentBuilder?.build()
        mAuthIntentLatch.countDown()
    }

    private fun doAuth() {
        try {
            mAuthIntentLatch.await()
        } catch (ex: InterruptedException) {
            Log.w(TAG, "Interrupted while waiting for auth intent")
        }
        if (mUsePendingIntents) {
            val completionIntent = Intent(this, PlanificationsActivity::class.java)
            val cancelIntent = Intent(this, LoginActivity::class.java)
            cancelIntent.putExtra(EXTRA_FAILED, true)
            cancelIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            mAuthService?.performAuthorizationRequest(
                mAuthRequest!!,
                PendingIntent.getActivity(this, 0, completionIntent, 0),
                PendingIntent.getActivity(this, 0, cancelIntent, 0),
                mAuthIntent!!)
        } else {
            val intent = mAuthService?.getAuthorizationRequestIntent(
                mAuthRequest!!,
                mAuthIntent!!)
            startActivityForResult(intent, RC_AUTH)
        }
    }
}