package com.abraham.android.bounce;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;


public class ConnectionSettings extends ActionBarActivity {
    private boolean isClient = false;
    private boolean isHost = false;
    /*Not any specific number*/
    private int PORT_NUMBER = 1337;
    private String HOST_NAME = "AbiePC";
    private Switch hostSwitch;
    private Switch clientSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_settings);

        hostSwitch = (Switch) findViewById(R.id.host_start_switch);
        hostSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                BounceServer myServer;
                if (isChecked) {
                    /*The switch is pressed*/
                    //if (!isClient){
                /*The current phone is not a client. Can only either be a client or host.*/
                    try {
                    /*Code taken from http://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html*/
                        myServer = new BounceServer();
                        myServer.serverConnect();
                        isHost = true;
                    } catch (Exception myException) {
                    /*Error occurred during server init*/
                    }
                    //}
                } else {
                    /*The switch is not pressed. Therefore stop the server*/
                    if (isHost) {
                        //myServer.endSession();
                        isHost = false;
                    }
                }
            }
        });

        clientSwitch = (Switch) findViewById(R.id.client_switch);
        clientSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                BounceClient myClient;

                if (isChecked) {
                    //if (!isHost){
                    try {
                        myClient = new BounceClient();
                        myClient.clientConnect();
                        isClient = true;
                    } catch (Exception myException) {
                    /*Error occurred during server init*/
                    }
                    // }
                } else {
            /*Disconnect the client*/
                    if (isClient) {
                        //myClient.endSession();
                        isClient = false;
                    }
                }
            }
        });
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

    public class BounceServer {

        ServerSocket serverSocket;
        boolean isServerOn = false;

        public void serverConnect() throws IOException {

            int portNumber = PORT_NUMBER;/*Port Number*/

            try {
                serverSocket = new ServerSocket(portNumber);
                Socket clientSocket = serverSocket.accept();
                PrintWriter out =
                        new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                String inputLine, outputLine;

                isServerOn = true;
                // Initiate conversation with client
                BounceProtocol kkp = new BounceProtocol();
                outputLine = kkp.processInput(null);
                out.println(outputLine);

                while ((inputLine = in.readLine()) != null) {
                    outputLine = kkp.processInput(inputLine);
                    out.println(outputLine);
                    if (outputLine.equals("Bye."))
                        break;
                }
            } catch (IOException e) {
                System.out.println("Exception caught when trying to listen on port "
                        + portNumber + " or listening for a connection");
                System.out.println(e.getMessage());
            }
        }

        public void endSession(){
            if (isServerOn){
                try {
                    serverSocket.close();
                    isServerOn = false;
                }
                catch (Exception myException){
                    /*An error occurred during losing the socket*/
                }
            }
        }
    }

    public class BounceProtocol {
        private static final int WAITING = 0;
        private static final int SENTKNOCKKNOCK = 1;
        private static final int SENTCLUE = 2;
        private static final int ANOTHER = 3;

        private static final int NUMJOKES = 5;

        private int state = WAITING;
        private int currentJoke = 0;

        private String[] clues = { "Turnip", "Little Old Lady", "Atch", "Who", "Who" };
        private String[] answers = { "Turnip the heat, it's cold in here!",
                "I didn't know you could yodel!",
                "Bless you!",
                "Is there an owl in here?",
                "Is there an echo in here?" };

        public String processInput(String theInput) {
            String theOutput = null;

            if (state == WAITING) {
                theOutput = "Knock! Knock!";
                state = SENTKNOCKKNOCK;
            } else if (state == SENTKNOCKKNOCK) {
                if (theInput.equalsIgnoreCase("Who's there?")) {
                    theOutput = clues[currentJoke];
                    state = SENTCLUE;
                } else {
                    theOutput = "You're supposed to say \"Who's there?\"! " +
                            "Try again. Knock! Knock!";
                }
            } else if (state == SENTCLUE) {
                if (theInput.equalsIgnoreCase(clues[currentJoke] + " who?")) {
                    theOutput = answers[currentJoke] + " Want another? (y/n)";
                    state = ANOTHER;
                } else {
                    theOutput = "You're supposed to say \"" +
                            clues[currentJoke] +
                            " who?\"" +
                            "! Try again. Knock! Knock!";
                    state = SENTKNOCKKNOCK;
                }
            } else if (state == ANOTHER) {
                if (theInput.equalsIgnoreCase("y")) {
                    theOutput = "Knock! Knock!";
                    if (currentJoke == (NUMJOKES - 1))
                        currentJoke = 0;
                    else
                        currentJoke++;
                    state = SENTKNOCKKNOCK;
                } else {
                    theOutput = "Bye.";
                    state = WAITING;
                }
            }
            return theOutput;
        }
    }

    public class BounceClient {

        Socket kkSocket;
        boolean isClientOn = false;

        public void clientConnect() throws IOException {

            String hostName = HOST_NAME;
            int portNumber = PORT_NUMBER;

            try {
                kkSocket = new Socket(hostName, portNumber);
                PrintWriter out = new PrintWriter(kkSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(kkSocket.getInputStream()));

                BufferedReader stdIn =
                        new BufferedReader(new InputStreamReader(System.in));
                String fromServer;
                String fromUser;
                isClientOn = true;

                while ((fromServer = in.readLine()) != null) {
                    System.out.println("Server: " + fromServer);
                    if (fromServer.equals("Bye."))
                        break;

                    fromUser = stdIn.readLine();
                    if (fromUser != null) {
                        System.out.println("Client: " + fromUser);
                        out.println(fromUser);
                    }
                }
            } catch (UnknownHostException e) {
                System.err.println("Don't know about host " + hostName);
                System.exit(1);
            } catch (IOException e) {
                System.err.println("Couldn't get I/O for the connection to " +
                        hostName);
                System.exit(1);
            }
        }

        public void endSession(){
            if (isClientOn){
                try {
                    kkSocket.close();
                    isClientOn = false;
                }
                catch (Exception myException){
                    /*An error occured with closing the client socket*/
                }
            }
        }

    }



}
