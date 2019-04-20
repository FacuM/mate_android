package me.facuarmo.mate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getSimpleName();
    private int IP_RANGE_MIN;
    private int IP_RANGE_MAX;

    private Button mNotifyButton;
    private ProgressBar mProgressBar;
    private TextView mServerStatus;
    private String[] mIpAsArray = new String[4];
    private String serverIp;

    private boolean testIp(String ip)
    {
        try {
            Socket socket = new Socket(ip, getResources().getInteger(R.integer.server_port));
            socket.setSoTimeout(getResources().getInteger(R.integer.server_timeout_ms));
        }
        catch (IOException e)
        {
            Log.d(TAG, "testIp: " + e.getMessage());
            return false;
        }
        return true;
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncServerDiscovery extends AsyncTask<Void, Void, Boolean>
    {
        @SuppressLint("WrongThread")
        @Override
        protected Boolean doInBackground(Void... voids) {
            if (serverIp != null && testIp(serverIp)) {
                mProgressBar.setVisibility(View.INVISIBLE);
                mNotifyButton.setVisibility(View.VISIBLE);
                Log.d(TAG, "doInBackground: the server at " + serverIp + " is working.");
            } else {
                mServerStatus.setText(getString(R.string.server_status_discovery_loading));

                Log.d(TAG, "doInBackground: miIpAsArray: size: " + mIpAsArray.length + ".");

                String concatIp = mIpAsArray[0] + "." + mIpAsArray [1] + "." + mIpAsArray[2] + ".";

                for (int i = IP_RANGE_MIN; i <= IP_RANGE_MAX; i++)
                {
                    String currentIp = concatIp + i;
                    Log.d(TAG, "doInBackground: testing... " + currentIp);
                    mServerStatus.setText(getString(R.string.server_status_discovery_loading_ip, currentIp));

                    if (testIp(currentIp))
                    {
                        serverIp = currentIp;
                        Log.d(TAG, "doInBackground: server found.");
                        mServerStatus.setVisibility(View.INVISIBLE);
                        break;
                    }
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            if (serverIp == null) {
                mProgressBar.setVisibility(View.INVISIBLE);
                mServerStatus.setText(getString(R.string.server_status_discovery_none));
                Log.e(TAG, "onPostExecute: failed to find a server.");

                mNotifyButton.setVisibility(View.VISIBLE);
                mNotifyButton.setText(getString(R.string.button_notify_as_retry));

                mNotifyButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mNotifyButton.setVisibility(View.INVISIBLE);
                        mProgressBar.setVisibility(View.VISIBLE);

                        new AsyncServerDiscovery().execute();
                    }
                });
            } else {
                SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString(getString(R.string.server_ip_key), serverIp).apply();

                mProgressBar.setVisibility(View.INVISIBLE);
                mNotifyButton.setVisibility(View.VISIBLE);


                mNotifyButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "onClick: firing up new notification...");
                        mNotifyButton.setVisibility(View.INVISIBLE);
                        mProgressBar.setVisibility(View.VISIBLE);

                        new AsyncSocketConnectionHandler().execute();
                    }
                });
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncSocketConnectionHandler extends AsyncTask<Void, Void, Boolean>
    {
        private Socket socket;

        @SuppressLint("WrongThread")
        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.d(TAG, "doInBackground: asynchronous notification task started.");
            try {
                socket = new Socket(serverIp, getResources().getInteger(R.integer.server_port));
            }
            catch (UnknownHostException e)
            {
                Log.e(TAG, "doInBackground: " + e.getMessage());
                mServerStatus.setText(R.string.server_status_unknown_host);
            }
            catch (IOException e)
            {
                Log.e(TAG, "doInBackground: " + e.getMessage());
                mServerStatus.setText(R.string.server_status_io_exception);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            Log.d(TAG, "onPostExecute: executed.");
            mProgressBar.setVisibility(View.INVISIBLE);
            mNotifyButton.setVisibility(View.VISIBLE);

            Toast.makeText(MainActivity.this, R.string.server_status_connect_pass, Toast.LENGTH_SHORT).show();

        }

        @Override
        protected void onCancelled() {
            super.onCancelled();

            try {
                socket.close();
            } catch (IOException e)
            {
                Log.e(TAG, "onCancelled: failed to close socket connection, this problem might cause server-side issues in the future.");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        serverIp = sharedPreferences.getString(getString(R.string.server_ip_key), null);

        // Prepare global objects.
        mNotifyButton = findViewById(R.id.mate_button_notify);
        mProgressBar = findViewById(R.id.mate_progressbar_main);
        mServerStatus = findViewById(R.id.mate_tv_status);

        // Prepare local objects.
        NetworkInfo NetworkInfo;
        ConnectivityManager ConnectivityManager;
        WifiManager WifiManager;

        // Set up fake constants from integers in resources.
        IP_RANGE_MIN = getResources().getInteger(R.integer.server_ip_range_min);
        IP_RANGE_MAX = getResources().getInteger(R.integer.server_ip_range_max);

        boolean permErr = false;
        ConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (ConnectivityManager == null) {
            Log.e(TAG, "onCreate: failed to set up the connectivity manager.");

            permErr = true;
        }
        else {
            Log.d(TAG, "onCreate: success obtaining the connectivity manager.");
            NetworkInfo = ConnectivityManager.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI);

            if (NetworkInfo.isConnected()) {
                Log.d(TAG, "onCreate: the device is connected to a WiFi network.");
                WifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

                if (WifiManager == null) {
                    Log.e(TAG, "onCreate: failed to obtain the WiFi manager.");

                    permErr = true;
                }
                else {
                    Log.d(TAG, "onCreate: success obtaining the WiFi manager.");
                    String ip = Formatter.formatIpAddress(WifiManager.getConnectionInfo().getIpAddress());
                    Log.d(TAG, "onCreate: detected IP address: " + ip + ".");
                    mIpAsArray = ip.split("\\.");

                    // If no IP is defined or the previously defined one is unavailable, fire up the discovery.
                    new AsyncServerDiscovery().execute();
                }
            }
            else {
                Log.w(TAG, "onCreate: no WiFi network connected.");
            }
        }

        // If a permission error occurred, tell the user so.
        if (permErr) {
            mServerStatus.setText(getString(R.string.server_status_network_info_exception));
        }
    }
}
