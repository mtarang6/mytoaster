package com.bae.dialogflowbot;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bae.dialogflowbot.adapters.ChatAdapter;
import com.bae.dialogflowbot.helpers.SendMessageInBg;
import com.bae.dialogflowbot.interfaces.BotReply;
import com.bae.dialogflowbot.models.Message;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BotReply {

  RecyclerView chatView;
  ChatAdapter chatAdapter;
  List<Message> messageList = new ArrayList<>();
  EditText editMessage;
  ImageButton btnSend;

  //dialogFlow
  private SessionsClient sessionsClient;
  private SessionName sessionName;
  private String uuid = UUID.randomUUID().toString();
  private String TAG = "mainactivity";

  private TextToSpeech textToSpeech;
  private String languageCode;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    chatView = findViewById(R.id.chatView);
    editMessage = findViewById(R.id.editMessage);
    btnSend = findViewById(R.id.btnSend);
    languageCode  = getString(R.string.language_code);
    initTTS();
    if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
              new String[]{Manifest.permission.RECORD_AUDIO}, 1);
    }
    chatAdapter = new ChatAdapter(messageList, this);
    chatView.setAdapter(chatAdapter);

    btnSend.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        Intent speechInteny = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechInteny.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechInteny.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak To Text");
        startActivityForResult(speechInteny,1);
        ///
      /*  String message = editMessage.getText().toString();
        if (!message.isEmpty()) {
          messageList.add(new Message(message, false));
          editMessage.setText("");
          sendMessageToBot(message);
          Objects.requireNonNull(chatView.getAdapter()).notifyDataSetChanged();
          Objects.requireNonNull(chatView.getLayoutManager())
              .scrollToPosition(messageList.size() - 1);
        } else {
          Toast.makeText(MainActivity.this, "Please enter text!", Toast.LENGTH_SHORT).show();
        }*/
      }
    });

    setUpBot();
  }

  private void setUpBot() {
    try {
      InputStream stream = this.getResources().openRawResource(R.raw.cred);
      GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
          .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
      Log.e(TAG, "setUpBot: "+credentials);
      String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
      SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
      SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
          FixedCredentialsProvider.create(credentials)).build();
      sessionsClient = SessionsClient.create(sessionsSettings);
      sessionName = SessionName.of(projectId, uuid);
      Log.e(TAG, "sessionsClient: "+sessionsClient);
      Log.e(TAG, "sessionName: "+sessionName);

      Log.d(TAG, "projectId : " + projectId);
    } catch (Exception e) {
      Log.d(TAG, "setUpBot: " + e.getMessage());
    }
  }

  private void sendMessageToBot(String message) {
    QueryInput input = QueryInput.newBuilder()
        .setText(TextInput.newBuilder().setText(message).setLanguageCode("en-US")).build();
    new SendMessageInBg(this, sessionName, sessionsClient, input).execute();
  }

  @Override
  public void callback(DetectIntentResponse returnResponse) {
    Log.e(TAG, "callback: "+returnResponse + " responses know " +returnResponse );
     if(returnResponse!=null) {
       String botReply = returnResponse.getQueryResult().getFulfillmentText();
       Log.d(TAG, "botReply: "+botReply);
       if(!botReply.isEmpty()){
         messageList.add(new Message(botReply, true));
         chatAdapter.notifyDataSetChanged();
         Objects.requireNonNull(chatView.getLayoutManager()).scrollToPosition(messageList.size() - 1);
         if (botReply.length() > 1) {
           muteAudio(false);
           HashMap<String, String> speechParams = new HashMap<>();
           speechParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uuid);
           textToSpeech.speak(botReply, TextToSpeech.QUEUE_ADD, speechParams);
         }
       }else {
         Toast.makeText(this, "something went wrong", Toast.LENGTH_SHORT).show();
       }
     } else {
       Toast.makeText(this, "failed to connect!", Toast.LENGTH_SHORT).show();
     }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if(requestCode == 1 && resultCode == RESULT_OK){
      ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      String message = matches.get(0).toString();
   //   editMessage.setText(message);
            //  editMessage.setText(matches.get(0).toString());
     // String message = editMessage.getText().toString();
      if (!message.isEmpty()) {
        messageList.add(new Message(message, false));
        editMessage.setText("");
        sendMessageToBot(message);
        Objects.requireNonNull(chatView.getAdapter()).notifyDataSetChanged();
        Objects.requireNonNull(chatView.getLayoutManager())
                .scrollToPosition(messageList.size() - 1);
      } else {
        Toast.makeText(MainActivity.this, "Please enter text!", Toast.LENGTH_SHORT).show();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
  @SuppressWarnings("ConstantConditions")
  private void muteAudio(boolean state) {
    try {
      AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
      audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, state);
      audioManager.setStreamMute(AudioManager.STREAM_ALARM, state);
      audioManager.setStreamMute(AudioManager.STREAM_MUSIC, state);
      audioManager.setStreamMute(AudioManager.STREAM_RING, state);
      audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, state);
    }catch (NullPointerException e){
      e.printStackTrace();
    }
  }
  public void initTTS() {
      textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
          if (status != TextToSpeech.ERROR) {
            int setLanguage = textToSpeech.setLanguage(Locale.ENGLISH);
            // TODO: Update voice parameters.
            if (setLanguage == TextToSpeech.LANG_MISSING_DATA ||
                    setLanguage == TextToSpeech.LANG_NOT_SUPPORTED) {
              Toast.makeText(getApplicationContext(),
                      getString(R.string.texttospeech_init_error),
                      Toast.LENGTH_SHORT).show();
              finish();
            }
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
              @Override
              public void onStart(String s) {
              }

              @Override
              public void onDone(String s) {
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    new Handler().postDelayed(new Runnable() {
                      @Override
                      public void run() {
                        muteAudio(true);

                      }
                    }, 500);
                  }
                });
              }

              @Override
              public void onError(final String s) {
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {

                    muteAudio(true);

                  }
                });
              }
            });
          }
        }
      });

  }
}
