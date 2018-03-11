package com.itmg_consulting.photobyebye;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;

class PermissionRequestDialog{

    private static String mManifestPermission;
    private static int mMessagePermission;
    private static int mRequestPermission = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    PermissionRequestDialog(String manifestPermission, int requestPermission, int messagePermission){
        mManifestPermission = manifestPermission;
        mRequestPermission = requestPermission;
        mMessagePermission = messagePermission;
    }

    PermissionRequestDialog(){

    }

    void showConfirmationDialog(FragmentManager fragmentManager){
        new ConfirmationDialog().show(fragmentManager, FRAGMENT_DIALOG);
    }

    void showErrorDialog(FragmentManager fragmentManager, String requestPermission){
        ErrorDialog.newInstance(requestPermission).show(fragmentManager, FRAGMENT_DIALOG);
    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final MainActivity activity = (MainActivity) getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if(activity.getmAlertDialog() != null)
                                activity.getmAlertDialog().dismiss();

                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    /**
     * Shows OK/Cancel confirmation dialog about permission.
     */
    public static class ConfirmationDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(mMessagePermission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{mManifestPermission},
                                    mRequestPermission);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    MainActivity activity = (MainActivity) parent.getActivity();
                                    if (activity != null) {
                                        if(activity.getmAlertDialog() != null)
                                            activity.getmAlertDialog().dismiss();

                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }
}