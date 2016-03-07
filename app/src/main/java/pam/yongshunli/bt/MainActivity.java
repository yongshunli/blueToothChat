package pam.yongshunli.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * une seule interface pour montrer la recherche,
 * l‘ecoute,la connexion ainsi que la communication entre les peripherique du client et serveur.
 */

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private ArrayList<BluetoothDevice> foundDevicesList = new ArrayList<BluetoothDevice>();
    private ArrayList<BluetoothDevice> pairedDevicesList = new ArrayList<BluetoothDevice>();
    private static int DISCOVERY_REQUEST = 1;
    private BluetoothAdapter bluetooth;
    private BluetoothSocket socket;
    private UUID uuid = UUID.fromString("a60f35f0-b93a-11de-8a39-08002009c666");
    private ArrayAdapter<BluetoothDevice> aaf, aap;
    private ListView foundDeviceListView, pairedDeviceListView;
    private Handler handler = new Handler();
    BroadcastReceiver discoveryResult = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice remoteDevice;
            remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (bluetooth.getBondedDevices().contains(remoteDevice)) {
                foundDevicesList.add(remoteDevice);
                aaf.notifyDataSetChanged();
            }
        }
    };
    //  private void configureBluetooth() {bluetooth = BluetoothAdapter.getDefaultAdapter();}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // obtenir le bt
        configureBluetooth();
        // activer le listview pour les nouveaus trouvee et les apparees
        setupListView();
        // activer les button pour chercher les peripherique du serveur
        setupSearchButton();
        // activer le cote serveur
        setupListenButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Make sure we're not doing discovery anymore
        if (bluetooth != null) {
            bluetooth.cancelDiscovery();
        }

        // desinscrire le recerveur pour les results de recherche.
        this.unregisterReceiver(discoveryResult);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in action bar clicked; goto parent activity.
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * assurer que le bt est pret.
     */

    private void configureBluetooth() {

        bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (null == bluetooth) {
            Toast.makeText(this, "bluetooth pas disponible", Toast.LENGTH_LONG).show();
        } else if (bluetooth.isEnabled()) {
            Toast.makeText(this, "votre bluetooth est deja pret", Toast.LENGTH_LONG).show();
        } else {
            bluetooth.enable();
            Toast.makeText(this, "your bluetooth device is now ready", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * activer le listview pour les nouveaus trouvee et les apparees,set listener pour les items de nouvelles trouvees.
     */
    private void setupListView() {
        aaf = new ArrayAdapter<BluetoothDevice>(this,
                android.R.layout.simple_list_item_1,
                foundDevicesList);
        Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            pairedDevicesList.add(device);
        }
        aap = new ArrayAdapter<BluetoothDevice>(this,
                android.R.layout.simple_list_item_1,
                pairedDevicesList);

        foundDeviceListView = (ListView) findViewById(R.id.list_discovered);
        pairedDeviceListView = (ListView) findViewById(R.id.list_paired);

        foundDeviceListView.setAdapter(aaf);
        pairedDeviceListView.setAdapter(aap);

        foundDeviceListView.setOnItemClickListener(this);


    }

    /**
     *  // activer les button pour chercher les peripherique du serveur
     */
    private void setupSearchButton() {
        Button searchButton = (Button) findViewById(R.id.button_search);
        searchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                registerReceiver(discoveryResult,
                        new IntentFilter(BluetoothDevice.ACTION_FOUND));
                if (!bluetooth.isDiscovering()) {
                    foundDevicesList.clear();
                    bluetooth.startDiscovery();
                }
            }
        });
    }

    /**
     * this function is to let the device(service side) itself to be discoverable
     */
    private void setupListenButton() {

        Button listenButton = (Button) findViewById(R.id.button_listen);
        listenButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent discIntent;
                discIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                startActivityForResult(discIntent, DISCOVERY_REQUEST);
            }
        });
    }

    /**
     * this function is to accept the collection request from eventual client device
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent
            data) {
        if (requestCode == DISCOVERY_REQUEST) {
            boolean isDiscoverable = resultCode > 0;
            if (isDiscoverable) {
                String name = "bluetoothserver";
                try {
                    final BluetoothServerSocket btserver =
                            bluetooth.listenUsingRfcommWithServiceRecord(name, uuid);
                    AsyncTask<Integer, Void, BluetoothSocket> acceptThread =
                            new AsyncTask<Integer, Void, BluetoothSocket>() {
                                @Override
                                protected BluetoothSocket doInBackground(Integer... params) {

                                    try {
                                        socket = btserver.accept(params[0] * 1000);
                                    } catch (IOException e) {
                                        Log.d("BLUETOOTH", e.getMessage());
                                    }

                                    return socket;
                                }

                                @Override
                                protected void onPostExecute(BluetoothSocket btSocket) {
                                    if (btSocket != null)
                                        updateUI();
                                }
                            };
                    acceptThread.execute(resultCode);
                } catch (IOException e) {
                    Log.d("BLUETOOTH", e.getMessage());
                }
            }
        }
    }

    @Override
    /**
     * this function is to collect to new found service side device
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        AsyncTask<Integer, Void, BluetoothSocket> connectTask =
                new AsyncTask<Integer, Void, BluetoothSocket >() {


                    @Override
                    protected BluetoothSocket  doInBackground(Integer... params) {
                        try {
                            BluetoothDevice device = foundDevicesList.get(params[0]);
                            socket = device.createRfcommSocketToServiceRecord(uuid);
                            socket.connect();
                        } catch (IOException e) {
                            Log.d("BLUETOOTH_CLIENT", e.getMessage());
                        }
                        return socket;
                    }

                    @Override
                    protected void onPostExecute(BluetoothSocket btSocket) {
                        bluetooth.cancelDiscovery();
                        updateUI();

                    }
                };
        connectTask.execute(position);


    }

    /**
     * once the connection between client and service etablished,user interface will change to exchange info state,
     * there to send outputstream by bluetoothsocket and by start a new thread to receieve incoming info.
     */
    private void updateUI() {
        final TextView messageText = (TextView) findViewById(R.id.text_messages);
        final EditText textEntry = (EditText) findViewById(R.id.text_message);
        messageText.setVisibility(View.VISIBLE);
        foundDeviceListView.setVisibility(View.GONE);
        textEntry.setEnabled(true);
      //write to outputstream can stay in main tread,because it is a non time consuming operation
        textEntry.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    sendMessage(socket, textEntry.getText().toString());
                    textEntry.setText("");
                    return true;
                }
                return false;
            }
        });
       //initialize a work tread to do read operation,because it is a time consuming operation
        BluetoothSocketListener bsl = new BluetoothSocketListener(socket,
                handler, messageText);
        Thread messageListener = new Thread(bsl);
        messageListener.start();
    }

    /**
     * this class enables a new thread to read the message from other side
     */
    private class BluetoothSocketListener implements Runnable {
        private BluetoothSocket socket;
        private TextView textView;
        private Handler handler;

        public BluetoothSocketListener(BluetoothSocket socket,
                                       Handler handler, TextView textView) {
            this.socket = socket;
            this.textView = textView;
            this.handler = handler;
        }

        public void run() {
            int bufferSize = 1024;

            byte[] buffer = new byte[bufferSize];
            try {
                InputStream instream = socket.getInputStream();
                int bytesRead = -1;
                String message = "";
                while (true) {
                    message = "";
                    bytesRead = instream.read(buffer);
                    if (bytesRead != -1) {
                        while ((bytesRead == bufferSize) && (buffer[bufferSize - 1] != 0)) {
                            message = message + new String(buffer, 0, bytesRead);
                            bytesRead = instream.read(buffer);
                        }
                        message = message + new String(buffer, 0, bytesRead - 1);
                        handler.post(new MessagePoster(textView, message));
                        //continue to get inputstream from remote bt device.
                        socket.getInputStream();
                    }
                }
            } catch (IOException e) {
                Log.d("BLUETOOTH_COMMS", e.getMessage());
            }
        }
    }

    /**
     * produce a outputstream from the message typed .
     * @param socket
     * @param msg
     */
    private void sendMessage(BluetoothSocket socket, String msg) {
        OutputStream outStream;
        try {
            outStream = socket.getOutputStream();
            byte[] byteString = (msg + " ").getBytes();
            // stringAsBytes[byteString.length-1] = 0;
            outStream.write(byteString);
        } catch (IOException e) {
            Log.d("BLUETOOTH_COMMS", e.getMessage());
        }
    }

    /**
     * this class enables to display the message in the IU
     */
    private class MessagePoster implements Runnable {
        private TextView textView;
        private String message;

        public MessagePoster(TextView textView, String message) {
            this.textView = textView;
            this.message = message;
        }

        public void run() {
            textView.setText(message);
        }
    }


}
