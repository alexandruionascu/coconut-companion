package hubclub.coconutcompanion;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;
import java.util.UUID;


public class MainActivity extends Activity {

    MediaPlayer player;
    MediaRecorder recorder;
    String outputFile;
    boolean recording = false;
    boolean playing = false;
    boolean uploading = false;
    //true if a specific recording has been uploaded to Azure
    boolean uploaded = false;

    //Azure connection string
    public static final String storageConnectionString =
            "DefaultEndpointsProtocol=http;" +
                    "AccountName=coconutaudio;" +
                    "AccountKey=yvYXXgpref+0Sybs860DevelZUs+kvjDxlywU09DXffl78hEFLX/iWF2qFv4sMubfG42c6Z628vG6c0hbfkysw==";

    public static String pairKey = null;

    //keep a backup for url, because of the click event
    public String urlBackup = "";



    private Socket mSocket;
    {
        try {
            mSocket = IO.socket("http://192.168.1.105:3000");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //button click events
        final Button recordButton = (Button) findViewById(R.id.record);
        recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
                //set that the recording hasn't been uploaded
                uploaded = false;
                if(recording) {

                    try {
                        System.out.println("stopped recording");
                        stopRecording();
                        recordButton.setText("RECORD");
                    } catch(Exception e) {
                        e.printStackTrace();
                    }

                } else {

                    try {
                        System.out.println("started recording");
                        beginRecording();
                        recordButton.setText("STOP");
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        final Button playButton = (Button) findViewById(R.id.play);
        playButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
               if(recording) {


                   try {
                       stopPlayback();
                   } catch(Exception e) {
                       e.printStackTrace();
                   }


               }  else {

                   try {
                       playRecording();
                   } catch(Exception e) {
                       e.printStackTrace();
                   }
               }
            }
        });

        final Button postButton = (Button) findViewById(R.id.post);
        postButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click

                if(MainActivity.pairKey != null) {

                    if (uploading) {
                        Toast toast = Toast.makeText(getApplicationContext(), "File is already uploading", Toast.LENGTH_SHORT);
                        toast.show();
                    } else {
                        try {
                            postAudioNote();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    Toast toast = Toast.makeText(getApplicationContext(), "You haven't synced yet", Toast.LENGTH_SHORT);
                    toast.show();
                    System.out.println(MainActivity.pairKey);
                }

            }
        });

        final Button syncButton = (Button) findViewById(R.id.sync);
        syncButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
                Sync();
            }
        });

        //set output file
        outputFile = Environment.getExternalStorageDirectory() + "/audiorec.mp4";
        //socket connect
        mSocket.connect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    //Auxiliary methods

    //------------------------------------AUDIO RECORD----------------------

    private void checkMediaRecorder() {
        if(recorder != null)
            recorder.release();
    }

    private void beginRecording() throws Exception{

        recording = true;
        checkMediaRecorder();
        File output = new File(outputFile);

        if(output.exists())
            output.delete();

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(outputFile);
        recorder.prepare();
        recorder.start();
    }

    private void stopRecording() {
        if(recorder != null)
            recorder.stop();

        recording = false;
    }
    //---------------------------------------------------------------------------


    //----------------------------------------AUDIO PLAYBACK---------------------
    private void checkMediaPlayer() {
        if(player != null) {
            try {
                player.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void playRecording() throws Exception{
        checkMediaPlayer();
        player = new MediaPlayer();
        player.setDataSource(outputFile);
        player.prepare();
        player.start();


    }


    private void stopPlayback() {
        if(player != null)
            recorder.stop();
    }

    //-------------------------------------------------------------------------------

    //---------------------------------POST AUDIO NOTES------------------------------
    public void postAudioNote() {

        if(recording == true) {

            Toast toast = Toast.makeText(getApplicationContext(), "You are still recording", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        if(recorder != null) {

            if(uploaded) {
                Toast toast = Toast.makeText(getApplicationContext(), "Succesfully posted", Toast.LENGTH_SHORT);
                toast.show();

                mSocket.emit("send audio note", urlBackup);

            } else {

                uploading = true;
                //upload audio to azure in a new thread
                new Thread(new Runnable() {
                    public void run() {

                        try {
                            // Retrieve storage account from connection-string.
                            CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);

                            // Create the blob client.
                            CloudBlobClient blobClient = storageAccount.createCloudBlobClient();

                            // Retrieve reference to a previously created container.
                            CloudBlobContainer container = blobClient.getContainerReference("recordings");


                            // Create a new blob with random UUID Name of 8 characters
                            final String fileName = UUID.randomUUID().toString().substring(0,7);
                            CloudBlockBlob blob = container.getBlockBlobReference(fileName + ".mp4");
                            File source = new File(outputFile);
                            blob.upload(new FileInputStream(source), source.length());
                            System.out.println("Upload complete!");
                            final String url = "https://coconutaudio.blob.core.windows.net/recordings/" + fileName + ".mp4";
                            //keep a backup in case the user posts the note multiple times
                            urlBackup = fileName;

                            uploaded = true;

                            //show toast on the UI thread
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast toast = Toast.makeText(getApplicationContext(), "Succesfully posted", Toast.LENGTH_SHORT);
                                    toast.show();

                                    mSocket.emit("send audio note", fileName);
                                }
                            });

                            uploading = false;


                        } catch (Exception e) {
                            // Output the stack trace.
                            e.printStackTrace();
                        }

                    }
                }).start();

            }




        }

    }

    private void Sync() {

        IntentIntegrator scanIntegrator = new IntentIntegrator(this);
        scanIntegrator.initiateScan();
    }
    //this method calls when the intent finishes
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanningResult != null) {
            //we have a result
            String scanContent = scanningResult.getContents();
            Toast toast = Toast.makeText(getApplicationContext(), "Synced", Toast.LENGTH_SHORT);
            toast.show();

            MainActivity.pairKey = scanContent;
            //send pair key to the server
            mSocket.emit("pair audio", MainActivity.pairKey);

            Toast letoast = Toast.makeText(getApplicationContext(), scanContent, Toast.LENGTH_SHORT);
            letoast.show();



        }

    }


}
