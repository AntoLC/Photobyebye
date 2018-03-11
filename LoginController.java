package com.itmg_consulting.photobyebye;


import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

class LoginController {
    private LocationManagerPhotoByeBye mLocationManager;
    private boolean isLocationRequest = false;

    private MainActivity parent;

    private boolean logged = false;
    private boolean loginStep = false;

    private EditText mEmailView;
    private EditText mUsernameView;
    private EditText mPasswordView;

    private String token;
    private String refresh_token;

    private static final String WS_TOKEN_REFRESH = "http://preprod.photobyebye.com/api/token/refresh";
    private static final String WS_REGISTER = "http://preprod.photobyebye.com/register";
    private static final String WS_LOGIN = "http://preprod.photobyebye.com/api/login_check";

    LoginController(MainActivity mainActivity) {
        init(mainActivity);
    }

    LoginController(MainActivity mainActivity, LocationManagerPhotoByeBye locationManager) {
        isLocationRequest = true;
        mLocationManager = locationManager;
        init(mainActivity);
    }

    private void init(MainActivity mainActivity){
        parent = mainActivity;

        // Get Token
        SharedPreferences sharedPref = parent.getPreferences(Context.MODE_PRIVATE);
        token = sharedPref.getString("token", "null");
        refresh_token = sharedPref.getString("refresh_token", "null");

        if(MainActivity.DEBUG == 1)
        {
            Log.d("Token_", token);
            Log.d("Token_refresh_token", refresh_token);
        }
    }

    boolean checkToken(){
        if(!token.equals("null"))
        {
            if(isTokenExpired(token))
            {
                Map<String, String> params = new HashMap<>();
                params.put("refresh_token", refresh_token);
                requestAPI(params, WS_TOKEN_REFRESH);

                if(isLocationRequest)
                    return false;
            }
            else if(! isLocationRequest)
                logged(true);

            return true;
        }
        else
            return false;
    }

    /**
     * Getter if Logged
     * @return boolean
     */
    boolean getLogged(){
        return logged;
    }

    /**
     * Process Login
     * <li>Success: Return to main @see {@link MainActivity#activeCamera()}</li>
     * <li>Failed: Display popup then display form login</li>
     * @see MainActivity#popupSnackBar(CharSequence)
     * @see #loginDisplay()
     */
    private void logged(Boolean success){
        if (success) {
            logged = true;
            parent.activeCamera();
        } else {
            if(loginStep)
                parent.popupSnackBar("Login ou mot de passe incorrect");
            else
                loginDisplay();
        }
    }

    /**
     * Display the screen Login:
     * <li>Control Field @see {@link #controlFields(String, String, String)}</li>
     * <li>requestAPI: @see {@link #requestAPI(Map, String)}</li>
     * <li>Access Register Screen : @see {@link #registerDisplay()}</li>
     */
    void loginDisplay(){
        loginStep = true;

        // Set up the login form.
        mUsernameView = (AutoCompleteTextView) parent.findViewById(R.id.username);
        mPasswordView =  parent.findViewById(R.id.password);
        mUsernameView.requestFocus();
        //openKeyboard();

        Button mEmailSignInButton = parent.findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = mUsernameView.getText().toString();
                String password = mPasswordView.getText().toString();
                if(!controlFields(username, password, null))
                {
                    Map<String, String> params = new HashMap<>();
                    params.put("_username", username);
                    params.put("_password", password);
                    requestAPI(params, WS_LOGIN);
                }

            }
        });

        Button mEmailRegister = parent.findViewById(R.id.email_register);
        mEmailRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerDisplay();
            }
        });
    }

    /**
     * Display the screen Login:
     * <li>Control Field @see {@link #controlFields(String, String, String)}</li>
     * <li>requestAPI: @see {@link #requestAPI(Map, String)}</li>
     * <li>Access Login Screen : @see {@link #loginDisplay()}</li>
     */
    private void registerDisplay(){
        parent.setContentView(R.layout.activity_register);

        mEmailView =  (AutoCompleteTextView) parent.findViewById(R.id.email);
        mUsernameView =  parent.findViewById(R.id.username);
        mPasswordView =  parent.findViewById(R.id.password);
        mEmailView.requestFocus();
        openKeyboard();

        Button mEmailRegister = parent.findViewById(R.id.email_register);
        mEmailRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Store values at the time of the login attempt.
                String email = mEmailView.getText().toString();
                String username = mUsernameView.getText().toString();
                String password = mPasswordView.getText().toString();
                if(!controlFields(username, password, email))
                {
                    Map<String, String> params = new HashMap<>();
                    params.put("username", username);
                    params.put("email", email);
                    params.put("password", password);
                    requestAPI(params, WS_REGISTER);
                }
            }
        });

        Button backSignIn = parent.findViewById(R.id.back_login);
        backSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parent.setContentView(R.layout.activity_login);
                loginDisplay();
            }
        });
    }

    /**
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private boolean controlFields(String username, String password, String email) {
        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(parent.getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        } else if (!isPasswordValid(password)) {
            mPasswordView.setError(parent.getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            mUsernameView.setError(parent.getString(R.string.error_field_required));
            focusView = mUsernameView;
            cancel = true;
        } else if (!isUsernameValid(username)) {
            mUsernameView.setError(parent.getString(R.string.error_invalid_username));
            focusView = mUsernameView;
            cancel = true;
        }

        // Check for a valid email.
        if(email != null)
        {
            mEmailView.setError(null);

            if (TextUtils.isEmpty(email)) {
                mEmailView.setError(parent.getString(R.string.error_field_required));
                focusView = mEmailView;
                cancel = true;
            } else if (!isEmailValid( email )) {
                mEmailView.setError(parent.getString(R.string.error_invalid_email));
                focusView = mEmailView;
                cancel = true;
            }
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        }

        return cancel;
    }

    /**
     * Attempts to sign in or register the account specified by the login form or from the tokens.
     * If no connection from a token request, don't show a popup but redirect to login.
     */
    private void requestAPI(final Map<String, String> params, String url) {
        Log.d("requestAPI", url);

        closeKeyboard();

        if(!parent.checkNetwork()){
            if(loginStep){
                parent.popupSnackBar("Pas de connexion internet");
                parent.showProgress(false);
            }
            else if(!isLocationRequest)
                loginDisplay();
        }
        else {
            // Show a progress spinner, and kick off a background task to
            if(loginStep || isLocationRequest)
                parent.showProgress(true);

            RequestQueue queue = Volley.newRequestQueue(parent.getApplicationContext());

            // Request a JsonObject response from the provided URL.
            JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, url, new JSONObject(params),
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                if(response.has("token"))
                                {
                                    if(MainActivity.DEBUG == 1)
                                    {
                                        Log.d("Token_WSSucces_token", response.getString("token")  );
                                        Log.d("Token_WSSucces_refresh", response.getString("refresh_token")  );
                                    }

                                    // Save token
                                    SharedPreferences sharedPref = parent.getPreferences(Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sharedPref.edit();
                                    editor.putString("token", response.getString("token"));
                                    editor.putString("refresh_token", response.getString("refresh_token"));
                                    editor.apply();
                                }
                                else if(response.has("message") && response.getString("message").equals("success")){
                                    Log.d("Pass", params.get("username")+" ::"+params.get("password"));
                                    Map<String, String> paramsLogin = new HashMap<>();
                                    paramsLogin.put("_username", params.get("username"));
                                    paramsLogin.put("_password", params.get("password"));
                                    requestAPI(paramsLogin, WS_LOGIN);
                                    return;
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            if(loginStep || isLocationRequest)
                                parent.showProgress(false);

                            if(!isLocationRequest)
                                logged(true);
                            else
                                mLocationManager.requestAPIGeoloc();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            if(loginStep || isLocationRequest)
                                parent.showProgress(false);

                            Log.d("Register_WSError", error.toString());

                            if(!isLocationRequest)
                                logged(false);
                        }
                    }
            );
            queue.add(stringRequest);
        }
    }

    /**
     * Close the keyboard when Call to the WebService
     * @see #requestAPI(Map, String)
     */
    private void closeKeyboard(){
        if (parent.getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) parent.getSystemService(MainActivity.INPUT_METHOD_SERVICE);
            if(imm != null )
                imm.hideSoftInputFromWindow(parent.getCurrentFocus().getWindowToken(), 0);
        }
    }

    /** Open the keyboard */
    private void openKeyboard(){
        InputMethodManager imm = (InputMethodManager) parent.getSystemService(MainActivity.INPUT_METHOD_SERVICE);
        if(imm != null )
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
    }

    /**
     * Check if the token is expired
     * @return boolean
     */
    private boolean isTokenExpired(String token) {
        long timestamp_expired = 0;
        String[] splitToken = token.split("\\.");
        byte[] decodedBytes = Base64.decode(splitToken[1], Base64.URL_SAFE);

        try {
            String payload = new String(decodedBytes, "UTF-8");
            try {
                JSONObject jobj = new JSONObject(payload);
                timestamp_expired = Long.parseLong(jobj.getString( "exp" ));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        long unixTimeStamp = System.currentTimeMillis() / 1000L;

        return timestamp_expired < unixTimeStamp;
    }

    private boolean isUsernameValid(String username) {
        return username.length() > 4;
    }

    private boolean isEmailValid(String email) {
        return email.contains("@");
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
    }
}
