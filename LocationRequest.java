package com.itmg_consulting.photobyebye;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

public class LocationRequest implements LocationListener{

    private MainActivity mMainActivity = null;
    private Location currentLocation = null;
    private LocationManager mLocationManager;
    private LocationManagerPhotoByeBye mLocationManagerPhotoByeBye;

    private static final int MINUTES = 1000 * 60;
    private static final int MINUTES_VALID = 2;
    private static final int MIN_DISTANCE = 2; // Meter
    private static final long SEC_UPDATE_GPS = 3000; // Milliseconde

    LocationRequest(MainActivity mainActivity, LocationManagerPhotoByeBye locationManagerPhotoByeBye) {
        Log.d("LocOnLocationRequest", "LocationRequest");

        mLocationManagerPhotoByeBye = locationManagerPhotoByeBye;
        mMainActivity = mainActivity;
    }

    void init(){
        if (ActivityCompat.checkSelfPermission(mMainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mMainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(mMainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                mLocationManagerPhotoByeBye.requestLocationPermission();
            }
            else
            {
                mLocationManager = (LocationManager) mMainActivity.getSystemService(MainActivity.LOCATION_SERVICE);

                attemptCatchLastKnownLocation();

                String provider = (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                        ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;

                mLocationManager.requestLocationUpdates(provider, SEC_UPDATE_GPS, MIN_DISTANCE, this);
            }
        }
    }

    /**
     * Overload the last location by a new one if it is better
     *
     * @param location The new location receipt
     */
    @Override
    public void onLocationChanged(Location location) {
        Log.d("LocOnLocationChanged", location.getProvider());
        if (location != null) {
            if(currentLocation != null){
                if(isBetterLocation(currentLocation, location))
                    setCurrentLocation(location);
            }
            else
                setCurrentLocation(location);
        }
    }

    private void setCurrentLocation(Location location){
        mLocationManagerPhotoByeBye.setCurrentLocation(location);
        currentLocation = location;
    }

    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    private boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > (MINUTES * MINUTES_VALID);
        boolean isSignificantlyOlder = timeDelta < -(MINUTES * MINUTES_VALID);
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    /** Try to give a location from the most accuracy to the less */
    @SuppressLint("MissingPermission")
    private void attemptCatchLastKnownLocation(){
        if(currentLocation == null){
            setCurrentLocation(mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
            if(currentLocation == null){
                setCurrentLocation(mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
                if(currentLocation == null){
                    Log.d("LocOnLastKnownLocation", "Not Found");
                }
            }
        }
    }

    /**
     * Check the update of status to request the best provider
     *
     * @param provider GPS Or Network
     * @param status of the provider
     * @param extras To be able the transfert data
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        String statusMessage = "";
        if(status == 0)
            statusMessage = "OUT_OF_SERVICE";
        else if(status == 1)
            statusMessage = "TEMPORARILY_UNAVAILABLE";
        else if(status == 2)
            statusMessage = "AVAILABLE";

        if(MainActivity.DEBUG == 1){
            Log.d("LocOnStatusChanged",provider);
            Log.d("LocOnStatusChanged",statusMessage);
        }

        if(provider.equalsIgnoreCase("gps") && status != 2){
            if(MainActivity.DEBUG == 1)
                Log.d("LocOnStatusChanged","START_NETWORK_PROVIDER");

            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SEC_UPDATE_GPS, MIN_DISTANCE, this);
        }
        else if(provider.equalsIgnoreCase("gps") && status == 2){
            if(MainActivity.DEBUG == 1)
                Log.d("LocOnStatusChanged","RESTART_GPS_PROVIDER");

            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, SEC_UPDATE_GPS, MIN_DISTANCE, this);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("LocOnProviderEnabled",provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d("LocOnProviderDisabled",provider);
    }

    /**
     * Stop the listener, so stop the GPS to free the memory and the battery
     *
     * @param from To know what call this function
     */
    void stopLocationUpdates(String from){
        if(MainActivity.DEBUG == 1)
            Log.d("LocOnStopListener",from);

        if(mLocationManager != null)
            mLocationManager.removeUpdates(this);
    }
}
