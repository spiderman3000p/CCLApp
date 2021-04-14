/*
 * Copyright 2017 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tautech.cclapp.classes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.tautech.cclapp.activities.LoginActivity;
import com.tautech.cclapp.models.Driver;
import com.tautech.cclapp.models.KeycloakUser;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.ClientAuthentication;
import net.openid.appauth.EndSessionRequest;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An example persistence mechanism for an {@link AuthState} instance.
 * This stores the instance in a shared preferences file, and provides thread-safe access and
 * mutation.
 */
public class AuthStateManager {
    public AuthorizationService mAuthService;
    public Configuration mConfiguration;
    private static final AtomicReference<WeakReference<AuthStateManager>> INSTANCE_REF =
            new AtomicReference<>(new WeakReference<>(null));
    public static KeycloakUser keycloakUser = null;
    //public static JSONObject userInfo = null;
    public static Driver driverInfo = null;
    private static final String TAG = "AuthStateManager";
    private static final String STORE_NAME = "AuthState";
    private static final String KEY_STATE = "state";
    public static final int RC_END_SESSION = 1000;

    private final SharedPreferences mPrefs;
    private final ReentrantLock mPrefsLock;
    private final AtomicReference<AuthState> mCurrentAuthState;

    @AnyThread
    public static com.tautech.cclapp.classes.AuthStateManager getInstance(@NonNull Context context) {
        com.tautech.cclapp.classes.AuthStateManager manager = INSTANCE_REF.get().get();
        if (manager == null) {
            manager = new com.tautech.cclapp.classes.AuthStateManager(context.getApplicationContext());
            INSTANCE_REF.set(new WeakReference<>(manager));
        }
        return manager;
    }

    private AuthStateManager(Context context) {
        mPrefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
        mPrefsLock = new ReentrantLock();
        mCurrentAuthState = new AtomicReference<>();
        mConfiguration = Configuration.getInstance(context);
        if (mConfiguration.hasConfigurationChanged()) {
            Log.e(TAG, "La configuracion de sesion ha cambiado, se cerrara su sesion");
        }
        mAuthService = new AuthorizationService(
                context,
                new AppAuthConfiguration.Builder()
                        .setConnectionBuilder(mConfiguration.getConnectionBuilder())
                        .build());
    }

    @AnyThread
    @NonNull
    public AuthState getCurrent() {
        if (mCurrentAuthState.get() != null) {
            return mCurrentAuthState.get();
        }

        AuthState state = readState();
        if (mCurrentAuthState.compareAndSet(null, state)) {
            return state;
        } else {
            return mCurrentAuthState.get();
        }
    }

    @AnyThread
    @NonNull
    public AuthState replace(@NonNull AuthState state) {
        writeState(state);
        mCurrentAuthState.set(state);
        return state;
    }

    @AnyThread
    @NonNull
    public AuthState updateAfterAuthorization(
            @Nullable AuthorizationResponse response,
            @Nullable AuthorizationException ex) {
        AuthState current = getCurrent();
        current.update(response, ex);
        return replace(current);
    }

    @AnyThread
    @NonNull
    public AuthState updateAfterTokenResponse(
            @Nullable TokenResponse response,
            @Nullable AuthorizationException ex) {
        AuthState current = getCurrent();
        current.update(response, ex);
        return replace(current);
    }

    @AnyThread
    @NonNull
    private AuthState readState() {
        mPrefsLock.lock();
        try {
            String currentState = mPrefs.getString(KEY_STATE, null);
            if (currentState == null) {
                return new AuthState();
            }

            try {
                return AuthState.jsonDeserialize(currentState);
            } catch (JSONException ex) {
                Log.w(TAG, "Failed to deserialize stored auth state - discarding");
                return new AuthState();
            }
        } finally {
            mPrefsLock.unlock();
        }
    }

    @AnyThread
    private void writeState(@Nullable AuthState state) {
        mPrefsLock.lock();
        try {
            SharedPreferences.Editor editor = mPrefs.edit();
            if (state == null) {
                editor.remove(KEY_STATE);
            } else {
                editor.putString(KEY_STATE, state.jsonSerializeString());
            }

            if (!editor.commit()) {
                throw new IllegalStateException("Failed to write state to shared prefs");
            }
        } finally {
            mPrefsLock.unlock();
        }
    }

    public KeycloakUser getKeycloakUser() {
        return keycloakUser;
    }

    /*public JSONObject getUserInfo() {
        return userInfo;
    }*/

    public Driver getDriverInfo() {
        return driverInfo;
    }

    public void setKeycloakUser(KeycloakUser _keycloakUser) {
        keycloakUser = _keycloakUser;
    }

    /*public void setUserInfo(JSONObject userInfo) {
        AuthStateManager.userInfo = userInfo;
    }*/

    public void setDriverInfo(Driver _driverInfo) {
        driverInfo = _driverInfo;
    }

    public void signOut(Context context) {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        AuthState currentState = this.getCurrent();
        if (currentState.getAuthorizationServiceConfiguration() != null) {
            AuthState clearedState = new AuthState(currentState.getAuthorizationServiceConfiguration());
            if (currentState.getLastRegistrationResponse() != null) {
                clearedState.update(currentState.getLastRegistrationResponse());
            }
            this.replace(clearedState);
        }
        SharedPreferences sharedPref = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("isLoggedIn", false);
        editor.apply();
        keycloakUser = null;
        driverInfo = null;
    }

    public void revalidateSessionData(Context context) {
        if (keycloakUser == null || driverInfo == null) {
            // buscamos los datos en shared prefs
            SharedPreferences sharedPref = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
            if (sharedPref.contains("driverInfoJSON") && sharedPref.contains("keycloakUserJSON")) {
                Gson gson = new Gson();
                String _driverInfo = sharedPref.getString("driverInfoJSON", "");
                String _keycloakUser = sharedPref.getString("keycloakUserJSON", "");
                if (!_driverInfo.isEmpty()) {
                    driverInfo = gson.fromJson(_driverInfo, Driver.class);
                }
                if (!_keycloakUser.isEmpty()) {
                    keycloakUser = gson.fromJson(_keycloakUser, KeycloakUser.class);
                }
            } else {
                Toast.makeText(context, "Some user data are wrong or empty.", Toast.LENGTH_LONG).show();
                signOut(context);
            }
        }
    }

    public void performTokenRequest(TokenRequest request, AuthorizationService.TokenResponseCallback callback) {
        ClientAuthentication clientAuthentication;
        try {
        clientAuthentication = this.getCurrent().getClientAuthentication();
            mAuthService.performTokenRequest(
                    request,
                    clientAuthentication,
                    callback);
        } catch (ClientAuthentication.UnsupportedAuthenticationMethod ex) {
            Log.d(TAG,
                    "Token request cannot be made, client authentication for the token "
                            + "endpoint could not be constructed (%s)",
                    ex);
            Log.e(TAG, "Client authentication method is unsupported");
        }
    }

    public void refreshAccessToken() {
        this.performTokenRequest(
                getCurrent().createTokenRefreshRequest(), this::handleAccessTokenResponse
        );
    }

    private void handleAccessTokenResponse(TokenResponse tokenResponse,
            AuthorizationException authException) {
        if (authException != null) {
            Log.e(TAG, "Exception trying to fetch token", authException);
        } else {
            this.updateAfterTokenResponse(tokenResponse, authException);
        }
    }
}
