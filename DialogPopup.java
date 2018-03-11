package com.itmg_consulting.photobyebye;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

public class DialogPopup extends DialogFragment {

    private static LocationManagerPhotoByeBye mLocationManager;

    public static DialogPopup newInstance(LocationManagerPhotoByeBye locationManager, CharSequence[] response) {
        DialogPopup f = new DialogPopup();

        mLocationManager = locationManager;

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putCharSequenceArray("response", response);

        f.setArguments(args);

        return f;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), AlertDialog.THEME_HOLO_DARK);

        final CharSequence[] response = getArguments().getCharSequenceArray("response");

        if(response.length > 1)
        {
            builder.setTitle("Choisi une location");
            builder.setItems(response, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    mLocationManager.popupCallBack(response[which]);
                }
            });
        }
        else{
            builder.setTitle("Désolé, pas de location trouvé")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        CharSequence response = "";
                        mLocationManager.popupCallBack(response);
                    }
                });
        }

        return builder.create();
    }

    public void onResume() {
        // Store access variables for window and blank point
        Window window = getDialog().getWindow();
        Point size = new Point();
        // Store dimensions of the screen in `size`
        Display display = window.getWindowManager().getDefaultDisplay();
        display.getSize(size);
        // Set the width of the dialog proportional to 75% of the screen width
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, (int) (size.y * 0.75));
        window.setGravity(Gravity.CENTER);
        // Call super onResume after sizing
        super.onResume();
    }
}