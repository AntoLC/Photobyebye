package com.itmg_consulting.photobyebye;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.crashlytics.android.Crashlytics;
import java.util.Timer;
import java.util.TimerTask;

import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private Camera2BasicFragment mCameraFragment;
    private AlertDialog mAlertDialog;

    private static int DURATION_SPLASH_SCREEN = 3000; // Millisecondes
    public static int DEBUG = 1;
    public static boolean USE_FUSED_LOCATION = true;
    public static boolean ASK_PERMISSION_OVERLAY = false;

    public static int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE= 5469;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());

        setContentView( R.layout.activity_login);
        splashScreen();

        if(ASK_PERMISSION_OVERLAY)
            checkPermission();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mAlertDialog != null)
            mAlertDialog.dismiss();
    }

    public AlertDialog getmAlertDialog(){
        return mAlertDialog;
    }

    public void checkPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                mAlertDialog = new AlertDialog.Builder(this)
                    .setTitle("Superposition")
                    .setMessage(R.string.permission_overscreen)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @TargetApi(Build.VERSION_CODES.M)
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
                        }})
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                checkPermission();
            }
        }
    }

    /**
     *  Display the splashScreen during an interval
     * @see       #DURATION_SPLASH_SCREEN
     */
    private void splashScreen(){
        final ImageView splash = findViewById(R.id.splash);
        final LinearLayout mainLinear = findViewById(R.id.mainLogin);

        splash.setVisibility(View.VISIBLE);
        mainLinear.setBackgroundColor(getResources().getColor(R.color.control_background_splash));
        final LoginController loginController = new LoginController(this);
        final boolean checkToken = loginController.checkToken();
        splash.postDelayed(new Runnable() {
            @Override
            public void run() {
                mainLinear.setBackgroundColor(getResources().getColor(R.color.white));
                splash.setVisibility(View.GONE);

                if(!checkToken)
                    loginController.loginDisplay();
            }
        }, DURATION_SPLASH_SCREEN);
    }

    /**
     * Activity Lifecycle:
     * Active the camera if already logged
     * @see       #activeCamera()
     * @see       LoginController#getLogged()
     */
    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * Active the screen camera
     * <li>Set the menu</li>
     * <li>Inform if the gps or network are activated</li>
     * @see       #setMenuOnCamera()
     * @see       #checkGpsNetwork()
     * @see       Camera2BasicFragment#newInstance()
     */
    public void activeCamera(){
        setContentView(R.layout.activity_camera);
        if(!isFinishing()) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    mCameraFragment = Camera2BasicFragment.newInstance();
                    getFragmentManager().beginTransaction()
                            .replace(R.id.container, mCameraFragment)
                            .commitAllowingStateLoss();

                    runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                findViewById(R.id.splash).setVisibility(View.GONE);
                                findViewById(R.id.drawer_layout).setBackgroundColor(getResources().getColor(R.color.black));

                                setMenuOnCamera();
                                checkGpsNetwork();
                            }
                        }
                    );
                }
            }, 1500);
        }
    }

    /** Set the menu on the camera screen */
    private void setMenuOnCamera(){
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setVisibility(View.VISIBLE);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    /**
     * Check GPS and Network, inform by a snackbar the status
     * @see       #checkGps()
     * @see       #checkNetwork()
     * @see       #popupSnackBar(CharSequence)
     */
    public boolean checkGpsNetwork(){
        String MESSAGE_ERROR_GPS = "GPS non activé";
        String MESSAGE_ERROR_RESEAU = "Réseau internet non détecté";

        String messageError = "";
        Boolean checkGps = checkGps();
        Boolean checkNetwork = checkNetwork();
        if(!checkGps)
            messageError = MESSAGE_ERROR_GPS;

        if(!checkNetwork)
        {
            if(!checkGps)
                messageError += " / ";

            messageError += MESSAGE_ERROR_RESEAU;
        }

        if(!checkGps || !checkNetwork){
            Log.d("GPS || NetWork", "Prob");
            popupSnackBar(messageError);

            return false;
        }

        return true;
    }

    /**
     * SnackBar On the current View
     */
    public void popupSnackBar(CharSequence message){
        Snackbar snackbar = Snackbar.make(findViewById(R.id.snackbar_inform), message, Snackbar.LENGTH_LONG);
        View snackBarView = snackbar.getView();

        TextView snackBarText = snackBarView.findViewById(android.support.design.R.id.snackbar_text);
        snackBarText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.ic_dialog_alert, 0);
        snackBarText.setGravity(Gravity.CENTER);
        snackbar.show();
    }

    /**
     * Check GPS Active
     * @return     boolean
     */
    private boolean checkGps()
    {
        boolean gps_enabled = false;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            if (lm != null) {
                gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            }
        } catch(Exception ex) {
            gps_enabled = false;
        }

        return gps_enabled;
    }

    /**
     * Check Network Active: First check Wifi if not active then check mobile network
     * @return     boolean
     */
    public boolean checkNetwork()
    {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        boolean networkEnabled = false;

        if(wifiManager != null)
            if(wifiManager.isWifiEnabled())
                networkEnabled = true;

        try {
            if(!networkEnabled){
                ConnectivityManager connectivityManager =
                        (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                if(connectivityManager != null)
                {
                    NetworkInfo mobileInfo =
                            connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

                    networkEnabled = mobileInfo.getState() == NetworkInfo.State.CONNECTED;

                    Log.d("Wifi", mobileInfo.getState().toString());
                }
            }

        } catch(Exception ex) {
            networkEnabled = false;
        }

        return networkEnabled;
    }

    /**
     * Click return on the phone: If menu open, it close it
     */
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if(mCameraFragment != null && Camera2BasicFragment.STATE_PICTURE_TAKEN == mCameraFragment.getState())
                mCameraFragment.unlockFocus();
            else
                super.onBackPressed();
        }
    }

    /**
     * Event on items menu:
     *  <li>Gallery Image</li>
     *  <li>Disconnect (Empty the token, restart App)</li>
     *  <li>A propos</li>
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_gallery) {
            //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("content://media/internal/images/media/PhotoByeBye"));
            Intent intent = new Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivity(intent);
        } else if (id == R.id.nav_deconnexion) {
            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("token", "null");
            editor.putString("refresh_token", "null");
            editor.apply();

            killCameraRestart();
        } else if (id == R.id.nav_apropos) {
            final Dialog dialog = new Dialog(this, R.style.AppTheme);
            dialog.setContentView(R.layout.activity_apropos);

            ImageView dialogButton = dialog.findViewById(R.id.dialogButtonOK);
            dialogButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            dialog.show();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void killCameraRestart(){
        getFragmentManager().beginTransaction()
                .remove(mCameraFragment)
                .commitAllowingStateLoss();

        recreate();
    }

    /** Shows the progress UI */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public void showProgress(final boolean show) {
        final View mProgressView = this.findViewById(R.id.login_progress);

        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }
}