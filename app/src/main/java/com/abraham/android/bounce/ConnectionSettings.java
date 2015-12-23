package com.abraham.android.bounce;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;


public class ConnectionSettings extends ActionBarActivity {

    /*Client*/
    TextView inputTextAddress, inputTextPort, responseTextView;
    EditText editTextAddress, editTextPort;
    Button buttonConnect;

    Switch hostSwitch, clientSwitch;

    /*Server*/
    TextView portTextView, ipSpecifierTextView, serverMessage;
    String message = "";
    ServerSocket serverSocket;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_settings);

        if (savedInstanceState == null) {

            /*For the client*/
            editTextAddress = (EditText) findViewById(R.id.input_ip_edittext);
            editTextPort = (EditText) findViewById(R.id.input_port_edittext);
            buttonConnect = (Button) findViewById(R.id.connect_button);
            inputTextAddress = (TextView) findViewById(R.id.input_ip_textview);
            inputTextPort = (TextView) findViewById(R.id.input_port_textview);
            responseTextView = (TextView) findViewById(R.id.response_textview);
            serverMessage = (TextView) findViewById(R.id.server_message_textview);

            /*For the host*/
            portTextView = (TextView) findViewById(R.id.port_specifier_textview);
            ipSpecifierTextView = (TextView) findViewById(R.id.ip_specifier_textview);

            /*To choose*/
            hostSwitch = (Switch) findViewById(R.id.host_switch);
            clientSwitch = (Switch) findViewById(R.id.connect_to_host_switch);

            hostSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        ipSpecifierTextView.setVisibility(View.VISIBLE);
                        portTextView.setVisibility(View.VISIBLE);
                        serverMessage.setVisibility(View.VISIBLE);
                        clientSwitch.setEnabled(false);
                        ipSpecifierTextView.setText(getIpAddress());
                        Thread socketServerThread = new Thread(new SocketServerThread());
                        socketServerThread.start();
                    } else {
                        ipSpecifierTextView.setVisibility(View.INVISIBLE);
                        portTextView.setVisibility(View.INVISIBLE);
                        serverMessage.setVisibility(View.INVISIBLE);
                        clientSwitch.setEnabled(true);
                    }
                }
            });

            clientSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        hostSwitch.setEnabled(false);
                        buttonConnect.setVisibility(View.VISIBLE);
                        editTextAddress.setVisibility(View.VISIBLE);
                        editTextPort.setVisibility(View.VISIBLE);
                        inputTextAddress.setVisibility(View.VISIBLE);
                        inputTextPort.setVisibility(View.VISIBLE);
                        responseTextView.setVisibility(View.VISIBLE);

                        buttonConnect.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MyClientTask myClientTask = new MyClientTask(editTextAddress.getText().toString(), Integer.parseInt(editTextPort.getText().toString()));
                                myClientTask.execute();
                            }
                        });
                    } else {
                        hostSwitch.setEnabled(true);
                        buttonConnect.setVisibility(View.INVISIBLE);
                        editTextAddress.setVisibility(View.INVISIBLE);
                        editTextPort.setVisibility(View.INVISIBLE);
                        inputTextAddress.setVisibility(View.INVISIBLE);
                        inputTextPort.setVisibility(View.INVISIBLE);
                        responseTextView.setVisibility(View.INVISIBLE);
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_connection_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        ip = "Local IP Address: " + inetAddress.getHostAddress();
                    }
                }
            }

        } catch (SocketException e) {
            e.printStackTrace();
            ip = "Something Wrong! " + e.toString();
        }

        return ip;
    }

    public class MyClientTask extends AsyncTask<Void, Void, Void> {

        String dstAddress;
        int dstPort;
        String response = "";

        MyClientTask(String addr, int port) {
            dstAddress = addr;
            dstPort = port;
        }

        @Override
        protected Void doInBackground(Void... arg0) {

            Socket socket = null;

            try {
                socket = new Socket(dstAddress, dstPort);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
                byte[] buffer = new byte[1024];

                int bytesRead;
                InputStream inputStream = socket.getInputStream();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    response += byteArrayOutputStream.toString("UTF-8");
                }

            } catch (UnknownHostException e) {
                e.printStackTrace();
                response = "UnknownHostException: " + e.toString();
            } catch (IOException e) {
                e.printStackTrace();
                response = "IOException: " + e.toString();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            /*Recieved from the server*/
            responseTextView.setText(response);
            super.onPostExecute(result);
        }

    }

    private class SocketServerThread extends Thread {

        static final int SocketServerPORT = 8080;
        int count = 0;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(SocketServerPORT);
                ConnectionSettings.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        portTextView.setText("I'm waiting here: " + serverSocket.getLocalPort());
                    }
                });

                while (true) {
                    Socket socket = serverSocket.accept();
                    count++;
                    message = "#" + count + " from " + socket.getInetAddress() + ":" + socket.getPort();

                    ConnectionSettings.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            /*Message recieved from the client*/
                            serverMessage.setText(message);
                        }
                    });

                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(socket, count);
                    socketServerReplyThread.run();

                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    private class SocketServerReplyThread extends Thread {

        int cnt;
        private Socket hostThreadSocket;

        SocketServerReplyThread(Socket socket, int c) {
            hostThreadSocket = socket;
            cnt = c;
        }

        @Override
        public void run() {
            OutputStream outputStream;
            String msgReply = "Hello from Android, you are #" + cnt;

            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(msgReply);
                printStream.close();

                message = "replayed: " + msgReply;

                ConnectionSettings.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        serverMessage.setText(message);
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
                message = "Something wrong! " + e.toString();
            }

            ConnectionSettings.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    serverMessage.setText(message);
                }
            });
        }

    }
}