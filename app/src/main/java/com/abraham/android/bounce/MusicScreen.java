package com.abraham.android.bounce;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.PlayConfig;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.PlayerStateCallback;
import com.spotify.sdk.android.player.Spotify;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;



public class MusicScreen extends Activity implements PlayerNotificationCallback, ConnectionStateCallback, AdapterView.OnItemSelectedListener, AdapterView.OnItemClickListener {

    private static final String CLIENT_ID = "ccf6c320eb754ab0bd13358005a32e4f";
    private static final String REDIRECT_URI = "bounceredirect://callback";
    private static final int REQUEST_CODE = 1337;
    private static final int NAME = 0;
    private static final int ARTIST = 1;
    private static final int ID = 2;
    private Player mPlayer;

    public String[] menuItems = {"About", "Settings"};
    public String accessToken;

    /*Holds the Name, Artist and Id of the current song*/
    public String[] currentTrackData;
    public String currentUserId;
    public String currentPlaylistId;

    public ArrayList<String> itemsInSpinner;

    /*Holds the Playlist names and Ids*/
    public ArrayList<ArrayList<String>> playlistDataArray;
    /*Holds the TrackNames, Artists and the Ids*/
    public ArrayList<ArrayList<String>> trackDataArray;

    public ArrayAdapter playlistListViewAdapter;
    public ArrayAdapter playlistSpinnerAdapter;

    public STATE state = STATE.IDLE;

    public ListView playlistListView;
    public boolean isPlayingMusic = false;
    public boolean isSongStarted = false;
    public int currentLengthOfSong;
    public int iPointBarValue = 0;
    public boolean isSliderMoving = false;
    public int currentIndexInList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_screen);
        /*The initialization for the lists and the different elements of the layout*/
        Init();
        /*Sets up the Spotify player (Authenticates and Sets up a login)(Makes sure that this portion is not done twice)*/
        if (savedInstanceState == null) {
            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URI);
            builder.setScopes(new String[]{"user-read-private", "streaming"});
            AuthenticationRequest request = builder.build();
            AuthenticationClient.openLoginActivity(MusicScreen.this, REQUEST_CODE, request);
        }
    }

    private void Init() {
        /*Setups up the slide-in menu*/
        ListView drawerListView = (ListView) findViewById(R.id.left_drawer);
        drawerListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, menuItems));
        drawerListView.setVisibility(View.VISIBLE);
        drawerListView.setOnItemClickListener(new DrawerItemClickListener());

        /*Setup the playlist dropdown menu*/
        itemsInSpinner = new ArrayList<>();
        playlistSpinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, itemsInSpinner);
        Spinner playlistSpinner = (Spinner) findViewById(R.id.playlist_spinner);
        playlistSpinner.setOnItemSelectedListener(this);
        playlistSpinner.setAdapter(playlistSpinnerAdapter);

        /*Sets up the global names in the playlists that are passed back and forth between threads*/
        playlistDataArray = new ArrayList<>();
        playlistDataArray.add(new ArrayList<String>()); //For Playlist Names
        playlistDataArray.add(new ArrayList<String>()); //For Playlist IDs
        
        trackDataArray = new ArrayList<>();
        trackDataArray.add(new ArrayList<String>()); //For Track Names
        trackDataArray.add(new ArrayList<String>()); //For Track Artists
        trackDataArray.add(new ArrayList<String>()); //For Track Ids

        /*Init the currentTrackData array*/
        currentTrackData = new String[3];
        currentTrackData[ID] = "spotify:track:2TpxZ7JUBn3uw46aR7qd6V";
        
        playlistListViewAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, trackDataArray.get(NAME));
        playlistListView = (ListView) findViewById(R.id.playlist_listview);
        playlistListView.setAdapter(playlistListViewAdapter);
        playlistListView.setOnItemClickListener(this);

        /*Setup the seekbar for music time control*/
        SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);
        songSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                TextView pointInSong = (TextView) findViewById(R.id.song_point);

                if(isSliderMoving){
                    int point = (progress * currentLengthOfSong)/100;
                    pointInSong.setText(String.format("%02d:%02d", (point / 60000), ((point % 60000) / 1000)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSliderMoving = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                TextView pointInSong = (TextView) findViewById(R.id.song_point);

                isSliderMoving = false;
                iPointBarValue = (seekBar.getProgress() * currentLengthOfSong) / 100;
                pointInSong.setText(String.format("%02d:%02d",(iPointBarValue/60000),((iPointBarValue%60000)/1000)));
                if(isPlayingMusic) {
                    PlayConfig myConfig =  PlayConfig.createFor("spotify:track:" + currentTrackData[ID]);
                    myConfig.withInitialPosition(iPointBarValue);
                    mPlayer.play(myConfig);
                }
            }
        });

        /*Setup the statemachine running in another thread that polls the webendpoint*/
        NetworkingThread myNetworkThread = new NetworkingThread();
        myNetworkThread.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_music_screen, menu);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        /*Check to make sure that the activity that finished was the Spotify activity*/
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                /*Setup a new configuration for Spotify*/
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                accessToken = response.getAccessToken();

                /*Get the UserId using the statemachine in the other thread*/
                state = STATE.GET_USER_ID;
                while (state != STATE.IDLE) {}

                /*Fill the list with an initial playlist*/
                playlistButtonClicked();

                mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        mPlayer.addConnectionStateCallback(MusicScreen.this);
                        mPlayer.addPlayerNotificationCallback(MusicScreen.this);
                        /*Have another thread make periodic callbacks inorder to refresh seek bar*/
                        SongThread mySongThread = new SongThread();
                        mySongThread.Init();
                        mySongThread.start();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("MainActivity", "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

    public String httpGet(String urlStr, String accessToken) throws IOException {
        try {
            /*Send a Get response to the specific endpoint*/
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if (accessToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            if (conn.getResponseCode() != 200) {
                throw new IOException(conn.getResponseMessage());
            }

            /* Buffer the result into a string */
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }

            /*Close the connection and the reader*/
            rd.close();
            conn.disconnect();

            /*Put the string into the global variable*/
            return sb.toString();

        }
        catch (Exception myException) {
            /*Error occurred when sending the Get request*/
            return null;
        }
    }

    public void onPlayStopButton(View view) {
        /*Specifies the button for the rest of the program*/
        Button playStopButton = (Button) findViewById(R.id.play_button);

        if (!isPlayingMusic && isSongStarted) {
            /*Play song from a certain position*/
            PlayConfig myConfig =  PlayConfig.createFor("spotify:track:" + currentTrackData[ID]);
            myConfig.withInitialPosition(iPointBarValue);
            mPlayer.play(myConfig);
            playStopButton.setText("Stop");
            isPlayingMusic = true;
        }
        else if (isPlayingMusic){
            /*Pause song at the certain point*/
            mPlayer.pause();
            playStopButton.setText("Play");
            isPlayingMusic = false;
        }
    }

    public void playlistButtonClicked() {
        /*Use the statemachine in the NetworkThread to retrieve the playlists*/
        state = STATE.GET_PLAYLISTS;
        while (state != STATE.IDLE){}

        /*Add the pertinient data to the ListView*/
        for (int i = 0; i < playlistDataArray.get(NAME).size(); i++) {
            itemsInSpinner.add(playlistDataArray.get(NAME).get(i));
        }
        playlistSpinnerAdapter.notifyDataSetChanged();
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
        TextView playlistName = (TextView) findViewById(R.id.playlist_name);

        /*Get the playlist id and load in the songs pertinient to it*/
        playlistName.setText(parent.getItemAtPosition(pos).toString());
        currentPlaylistId = playlistDataArray.get(ARTIST).get(pos);
        loadInPlaylistSongs();
    }

    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);
        TextView songName = (TextView) findViewById(R.id.song_name);
        TextView artistName = (TextView) findViewById(R.id.artist_name);
        TextView songLengthTotal = (TextView) findViewById(R.id.song_length);

        /*Get the Id of the song in the list as well as the index*/
        currentTrackData[ID] = trackDataArray.get(ID).get(arg2);
        currentIndexInList = arg2;

        /*Play song and fill out the song data*/
        mPlayer.play("spotify:track:"+currentTrackData[ID]);
        state = STATE.GET_SONG_DATA;
        while (state != STATE.IDLE){}
        songName.setText(currentTrackData[NAME]);
        artistName.setText(currentTrackData[ARTIST]);
        songLengthTotal.setText(String.format("%02d:%02d", (currentLengthOfSong / 60000), ((currentLengthOfSong % 60000) / 1000)));

        /*Reset the seekbar to 0*/
        iPointBarValue = 0;
        songSeekBar.setProgress(0);

        isPlayingMusic = true;
        isSongStarted = true;
    }

    public void loadInPlaylistSongs(){
        /*Use the statemaching in the NetworkThread to get a list of playlist songs and ids*/
        state = STATE.GET_PLAYLIST_SONGS;
        while (state != STATE.IDLE){}
        playlistListViewAdapter.notifyDataSetChanged();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    public void onBackButtonClick(View view){
        SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);
        TextView songName = (TextView) findViewById(R.id.song_name);
        TextView artistName = (TextView) findViewById(R.id.artist_name);
        TextView songLengthTotal = (TextView) findViewById(R.id.song_length);

        /*Enable click once to go to the beginning and a double-click to go back a song*/
        if (iPointBarValue >= 20){
            iPointBarValue = 0;
            mPlayer.play("spotify:track:" + currentTrackData[ID]);
            songSeekBar.setProgress(0);
            isPlayingMusic = true;
            isSongStarted = true;
        }
        else if( currentIndexInList > 0) {
            /*Moves the index back and goes to the previous song in the list*/
            currentIndexInList = --currentIndexInList;
            currentTrackData[ID] = trackDataArray.get(ID).get(currentIndexInList);
            currentTrackData[NAME] = trackDataArray.get(NAME).get(currentIndexInList);
            iPointBarValue = 0;
            mPlayer.play("spotify:track:" + currentTrackData[ID]);
            state = STATE.GET_SONG_DATA;
            while (state != STATE.IDLE) {}
            songName.setText(currentTrackData[NAME]);
            artistName.setText(currentTrackData[ARTIST]);
            songLengthTotal.setText(String.format("%02d:%02d", (currentLengthOfSong / 60000), ((currentLengthOfSong % 60000) / 1000)));
            songSeekBar.setProgress(0);
            isPlayingMusic = true;
            isSongStarted = true;
        }
    }

    public void onForwardButtonClick (View view){
        SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);
        TextView songName = (TextView) findViewById(R.id.song_name);
        TextView artistName = (TextView) findViewById(R.id.artist_name);
        TextView songLengthTotal = (TextView) findViewById(R.id.song_length);

        /*Moves to the next song in the list and plays it*/
        if(currentIndexInList < trackDataArray.get(NAME).size() - 1){
            currentIndexInList = ++currentIndexInList;
            currentTrackData[ID] = trackDataArray.get(ID).get(currentIndexInList);
            currentTrackData[NAME] = trackDataArray.get(NAME).get(currentIndexInList);
            iPointBarValue = 0;
            mPlayer.play("spotify:track:" + currentTrackData[ID]);
            state = STATE.GET_SONG_DATA;
            while (state != STATE.IDLE) {}
            songName.setText(currentTrackData[NAME]);
            artistName.setText(currentTrackData[ARTIST]);
            songLengthTotal.setText(String.format("%02d:%02d", (currentLengthOfSong / 60000), ((currentLengthOfSong % 60000) / 1000)));
            songSeekBar.setProgress(0);
            isPlayingMusic = true;
            isSongStarted = true;
        }
    }

    /*Enum for statemachine in NetworkingThread*/
    public enum STATE {
        IDLE,
        GET_SONG_DATA,
        GET_PLAYLISTS,
        GET_USER_ID,
        GET_PLAYLIST_SONGS
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            ListView drawerListView = (ListView) findViewById(R.id.left_drawer);
            String recievedString = (String) drawerListView.getItemAtPosition(position);
            Intent sendIntent;

            /*Determine where to go when an item in the nav drawer is clicked*/
            if (recievedString == "About") {
                sendIntent = new Intent(getApplicationContext(), About.class);
                startActivity(sendIntent);
            }
            else if (recievedString == "Settings"){
                sendIntent = new Intent(getApplicationContext(), Settings.class);
                startActivity(sendIntent);
            }


        }
    }

    public class NetworkingThread extends Thread {

        public class User{
            private String birthdate;
            private String country;
            private String display_name;
            private String email;
            //private ExternalURL eternal_urls;
            private Followers followers;
            private String href;
            private String id;
            private ImageType[] images;
            private String product;
            private String type;
            private String uri;
        }
        public class Followers{
            private String href;
            private int total;
        }
        public class ImageType{
            private int height;
            private String url;
            private int width;
        }
        public class Track {
            private SimplifiedAlbum album;
            private SimplifiedArtist[] artists;
            private String[] available_markets;
            private int disc_number;
            private int duration_ms;
            private boolean explicit;
            //private ExternalID external_ids;
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private boolean is_playable;
            //private LinkedTrack linked_from;
            private String name;
            private int popularity;
            private String preview_url;
            private int track_number;
            private String type;
            private String uri;
        }
        public class SimplifiedAlbum{
            private String album_type;
            private String[] available_markets;
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private ImageType[] images;
            private String name;
            private String type;
            private String uri;
        }
        public class SimplifiedArtist{
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private String name;
            private String type;
            private String uri;
        }
        public class PlaylistTrack{
            //private TimeStamp added_at;
            private User added_by;
            private boolean is_local;
            private Track track;
        }
        public class PlaylistTracksPaging {
            private String href;
            private PlaylistTrack[] items;
            private int limit;
            private String next;
            private int offset;
            private String previous;
            private int total;
        }
        public class SimplifiedPlaylist{
            private boolean collaborative;
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private ImageType[] images;
            private String name;
            private User owner;
            //private bool/null public;
            private String snapshot_id;
            //private Track tracks; -> has string href; and string total;
            private String type;
            private String uri;
        }
        public class UsersPlaylistsPaging{
            private String href;
            private SimplifiedPlaylist[] items;
            private int limit;
            private String next;
            private int offset;
            private String previous;
            private int total;
        }

        public void run() {
            /*Statemachine that sends out the Get requests and proceses the incoming data*/
            while (true) {
                String jsonString;

                try {
                    switch (state) {
                        case IDLE:
                            break;
                        case GET_SONG_DATA:
                            jsonString = httpGet("https://api.spotify.com/v1/tracks/" + currentTrackData[ID], null);
                            try {
                                Gson gObj = new Gson();
                                Track track =  gObj.fromJson(jsonString, Track.class);
                                currentTrackData[NAME] = track.name;
                                currentTrackData[ARTIST] = track.artists[0].name;
                                currentLengthOfSong = track.duration_ms;
                            } catch (Exception myException) {
                                /*An error occured when running the statemachine*/
                            }
                            state = STATE.IDLE;
                            break;
                        case GET_PLAYLISTS:
                            jsonString = httpGet("https://api.spotify.com/v1/users/" + currentUserId + "/playlists", accessToken);
                            try {
                                Gson gObj = new Gson();
                                UsersPlaylistsPaging myPlaylists = gObj.fromJson(jsonString, UsersPlaylistsPaging.class);
                                playlistDataArray.get(NAME).clear();
                                playlistDataArray.get(ARTIST).clear();

                                for (int i = 0; i < myPlaylists.items.length; i++){
                                    playlistDataArray.get(NAME).add(myPlaylists.items[i].name);
                                    playlistDataArray.get(ARTIST).add(myPlaylists.items[i].id);
                                }
                            } catch (Exception myException) {
                                /*An error occured when running the statemachine*/
                            }
                            state = STATE.IDLE;
                            break;
                        case GET_USER_ID:
                            jsonString = httpGet("https://api.spotify.com/v1/me", accessToken);
                            try {
                                Gson gObj = new Gson();
                                User user =  gObj.fromJson(jsonString, User.class);
                                currentUserId = user.id;

                            } catch (Exception myException) {
                               /*An error occured when running the statemachine*/
                            }
                            state = STATE.IDLE;
                            break;
                        case GET_PLAYLIST_SONGS:
                            jsonString = httpGet("https://api.spotify.com/v1/users/" + currentUserId + "/playlists/" + currentPlaylistId + "/tracks", accessToken);
                            try {
                                Gson gObj = new Gson();
                                PlaylistTracksPaging myPlaylistTracks = gObj.fromJson(jsonString, PlaylistTracksPaging.class);
                                trackDataArray.get(NAME).clear();
                                trackDataArray.get(ARTIST).clear();
                                trackDataArray.get(ID).clear();

                                for (int i = 0; i < myPlaylistTracks.items.length; i++){
                                    trackDataArray.get(NAME).add(myPlaylistTracks.items[i].track.name);
                                    trackDataArray.get(ARTIST).add(myPlaylistTracks.items[i].track.artists[0].name);
                                    trackDataArray.get(ID).add(myPlaylistTracks.items[i].track.id);
                                }
                            }
                            catch(Exception myException){
                                /*An error occured when running the statemachine*/
                            }
                            state = STATE.IDLE;
                            break;
                    }
                } catch (Exception myException) {
                    myException.getMessage();
                }
            }
        }
    }

    public class SongThread extends Thread{

        PlayerStateCallback myCallback;

        public void Init(){
            myCallback = new PlayerStateCallback() {
                @Override
                public void onPlayerState(PlayerState playerState) {
                    TextView pointInSong = (TextView) findViewById(R.id.song_point);
                    SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);

                    if (playerState.playing) {
                        if (isPlayingMusic) {
                            if (!isSliderMoving) {
                                /*Move the seekbar to the position that the song is at*/
                                pointInSong.setText(String.format("%02d:%02d", (playerState.positionInMs / 60000), ((playerState.positionInMs % 60000) / 1000)));
                                songSeekBar.setProgress(((playerState.positionInMs * 100) / currentLengthOfSong));
                            }
                            iPointBarValue = playerState.positionInMs;
                        }
                    }
                    else {
                       // onForwardButtonClick(null);
                    }
                }
            };
        }

        public void run(){
            while(true){
                try {
                    sleep(500);
                }
                catch(Exception myException){
                    /*An error occured when trying to sleep this thread*/
                }
                if(isPlayingMusic){
                    mPlayer.getPlayerState(myCallback);
                }
                else{
                    /*Continue on and do not callback*/
                }
            }
        }
    }


}