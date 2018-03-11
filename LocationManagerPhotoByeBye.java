package com.itmg_consulting.photobyebye;


import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationManagerPhotoByeBye implements FragmentCompat.OnRequestPermissionsResultCallback  {

    private Camera2BasicFragment mCamera;
    private MainActivity mMainActivity;
    private Location mCurrentLocation;

    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final String PAS_DE_CONNEXION = "Pas de connexion internet.";
    private static final String PROBLEME_DE_CONNEXION = "Problème de connexion.";
    private static final String PROBLEME_GPS = "Le gps n'a pas pu déterminer votre position.";
    private static final String WS_GETPOI = "http://preprod.photobyebye.com/api/getPoi";

    LocationManagerPhotoByeBye(MainActivity mainActivity, Camera2BasicFragment camera){
        mCamera = camera;
        mMainActivity = mainActivity;
    }

    void setCurrentLocation(Location currentLocation){
        Log.d("LocOnCurrentLocation",
                "Lat:"+String.valueOf(currentLocation.getLatitude())+
                        " Long:"+String.valueOf(currentLocation.getLongitude()));

        mCurrentLocation = currentLocation;
    }

    /**
     * Check if the token is expired
     * If Not Request the API geoloc #requestAPIGeoloc()
     * If Yes Request a new token, when the new token is collected call automatically #requestAPIGeoloc()
     * @see       #requestAPIGeoloc()
     * @see       LoginController#checkToken()
     */
    void checkToken(){
        if(!mMainActivity.checkNetwork()){
            mMainActivity.popupSnackBar(PAS_DE_CONNEXION);
        }
        else{
            LoginController loginController = new LoginController(mMainActivity, this);
            if(loginController.checkToken())
                requestAPIGeoloc();
        }
    }

    /**
     * Attempts to connect to the API to get the different places from the location.
     * If no connection form a token request, don't show a popup but redirect to login
     */
    void requestAPIGeoloc() {
        if(mMainActivity.checkGpsNetwork())
        {
            if(mCurrentLocation != null){
                mMainActivity.showProgress(true);
                String latitude = String.valueOf(mCurrentLocation.getLatitude());
                String longitude = String.valueOf(mCurrentLocation.getLongitude());

                Map<String, String> params = new HashMap<>();
                if(MainActivity.DEBUG == 1)
                {
                    params.put("latitude", "48.856203");
                    params.put("longitude", "2.297678");
                    //params.put("latitude", "48.9197286");
                    //params.put("longitude", "2.3154484");
                    Log.d("LocOnrequestAPIGPS", WS_GETPOI);
                    Log.d("LocOnLatitudeCurrent", latitude);
                    Log.d("LocOnLongitudeCurrent", longitude);
                }
                else{
                    params.put("latitude", latitude);
                    params.put("longitude", longitude);
                }

                SharedPreferences sharedPref = mMainActivity.getPreferences(Context.MODE_PRIVATE);
                final String token = sharedPref.getString("token", "null");

                RequestQueue queue = Volley.newRequestQueue(mMainActivity.getApplicationContext());

                // Request a JsonObject response from the provided URL.
                com.itmg_consulting.photobyebye.LocationManagerPhotoByeBye.CustomJsonArrayRequest stringRequest = new com.itmg_consulting.photobyebye.LocationManagerPhotoByeBye.CustomJsonArrayRequest(Request.Method.POST, WS_GETPOI, new JSONObject(params),
                        new Response.Listener<JSONArray>() {
                            @Override
                            public void onResponse(JSONArray response) {
                                List<String> listItems = new ArrayList<>();

                                for (int i = 0; i < response.length(); i++) {
                                    JSONObject jsonobject;

                                    try {
                                        jsonobject = response.getJSONObject(i);
                                        String name = jsonobject.getString("name");
                                        listItems.add(name);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }

                                popupListLocation(listItems.toArray(new CharSequence[listItems.size()]));
                                mMainActivity.showProgress(false);
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                mMainActivity.showProgress(false);
                                mMainActivity.popupSnackBar(PROBLEME_DE_CONNEXION);
                                Log.d("LocOnRegister_WSError", error.toString());
                            }
                        }
                ){
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        String bearer = "Bearer ".concat(token);
                        Map<String, String> headersSys = super.getHeaders();
                        Map<String, String> headers = new HashMap<>();
                        headersSys.remove("Authorization");
                        headers.put("Authorization", bearer);
                        headers.putAll(headersSys);
                        return headers;
                    }

                };
                queue.add(stringRequest);
            }
            else {
                mMainActivity.popupSnackBar(PROBLEME_GPS);
            }
        }
    }

    /**
     * Show the location found in a popup
     *
     * @param response Tableau response gave by the API
     * @see #requestAPIGeoloc()
     */
    private void popupListLocation(CharSequence[] response){
        if(response.length == 0 || response.length > 1)
        {
            DialogPopup dialogPopup = DialogPopup.newInstance(this, response);
            dialogPopup.show(mMainActivity.getFragmentManager(), "PopUp");
        }
        else
            popupCallBack(response[0]);
    }

    /**
     * Callback from the popup of the place chosen by th user
     *
     * @param response choose by the user
     */
    void popupCallBack(CharSequence response){
        mCamera.setTextPreview(response);
    }

    void requestLocationPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(mCamera, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionRequestDialog permissionRequestDialog = new PermissionRequestDialog(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_LOCATION_PERMISSION, R.string.request_permission_location);
            permissionRequestDialog.showConfirmationDialog(mCamera.getChildFragmentManager());
        } else {
            FragmentCompat.requestPermissions(mCamera, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                if (mCamera.isResumed() && !mCamera.isRemoving()) {
                    PermissionRequestDialog permissionRequestDialog = new PermissionRequestDialog();
                    permissionRequestDialog.showErrorDialog(mCamera.getChildFragmentManager(), mCamera.getString(R.string.request_permission_location));
                }
            }
        }
    }

    class CustomJsonArrayRequest extends JsonRequest<JSONArray> {
        /**
         * Creates a new request.
         * @param method the HTTP method to use
         * @param url URL to fetch the JSON from
         * @param jsonRequest A {@link JSONObject} to post with the request. Null is allowed and
         *   indicates no parameters will be posted along with request.
         * @param listener Listener to receive the JSON response
         * @param errorListener Error listener, or null to ignore errors.
         */
        CustomJsonArrayRequest(int method, String url, JSONObject jsonRequest,
                               Response.Listener<JSONArray> listener, Response.ErrorListener errorListener) {
            super(method, url, (jsonRequest == null) ? null : jsonRequest.toString(), listener,
                    errorListener);
        }

        @Override
        protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
            try {
                String jsonString = new String(response.data,
                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                return Response.success(new JSONArray(jsonString),
                        HttpHeaderParser.parseCacheHeaders(response));
            } catch (UnsupportedEncodingException | JSONException e) {
                return Response.error(new ParseError(e));
            }
        }
    }
}
