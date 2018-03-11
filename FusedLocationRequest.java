package com.itmg_consulting.photobyebye;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

class FusedLocationRequest{
    private MainActivity mMainActivity;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationManagerPhotoByeBye mLocationManager;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private boolean attemptChangeSetting = false; // ALLOW ONE TIME TO CHANGE THE SETTING

    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    FusedLocationRequest(MainActivity mainActivity, LocationManagerPhotoByeBye locationManager) {
        Log.d("LocOnLocationRequest", "FusedLocationRequest");

        mMainActivity = mainActivity;
        mLocationManager = locationManager;
    }

    void init(){
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mMainActivity);

        if (ActivityCompat.checkSelfPermission(mMainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mMainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(mMainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                mLocationManager.requestLocationPermission();
            }
            else
            {
                createLocationRequest();

                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                        .addLocationRequest(mLocationRequest);

                SettingsClient client = LocationServices.getSettingsClient(mMainActivity);
                Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

                task.addOnSuccessListener(mMainActivity, new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.d("LocFuse","onSuccess");

                        mLocationCallback = new LocationCallback() {
                            @Override
                            public void onLocationResult(LocationResult locationResult) {
                                for (Location location : locationResult.getLocations()) {
                                    mLocationManager.setCurrentLocation(location);
                                }
                            }
                        };

                        mFusedLocationClient.getLastLocation()
                                .addOnSuccessListener(mMainActivity, new OnSuccessListener<Location>() {
                                    @Override
                                    public void onSuccess(Location location) {
                                        // Got last known location. In some rare situations this can be null.
                                        if (location != null) {
                                            mLocationManager.setCurrentLocation(location);
                                        }
                                    }
                                });

                        startLocationUpdates();
                    }
                });

                task.addOnFailureListener(mMainActivity, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("LocFuse","onFailure");

                        if (e instanceof ResolvableApiException) {
                            // Location settings are not satisfied, but this can be fixed
                            // by showing the user a dialog.
                            try {
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                if(! attemptChangeSetting){
                                    attemptChangeSetting = true;
                                    ResolvableApiException resolvable = (ResolvableApiException) e;
                                    resolvable.startResolutionForResult(mMainActivity, REQUEST_CHECK_SETTINGS);
                                }
                            } catch (IntentSender.SendIntentException sendEx) {
                                // Ignore the error.
                            }
                        }
                    }
                });
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,null);
    }

    /**
     * Stop the listener, so stop the GPS to free the memory and the battery
     *
     * @param from To know what call this function
     */
    void stopLocationUpdates(String from){
        if(MainActivity.DEBUG == 1)
            Log.d("LocOnStopListener",from);

        if (mLocationCallback != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }
}
