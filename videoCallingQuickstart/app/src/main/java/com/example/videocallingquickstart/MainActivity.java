package com.example.videocallingquickstart;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.content.Context;
import com.azure.android.communication.calling.CallState;
import com.azure.android.communication.calling.CallingCommunicationException;
import com.azure.android.communication.calling.GroupCallLocator;
import com.azure.android.communication.calling.IncomingAudioOptions;
import com.azure.android.communication.calling.IncomingVideoOptions;
import com.azure.android.communication.calling.JoinCallOptions;
import com.azure.android.communication.calling.OutgoingAudioOptions;
import com.azure.android.communication.calling.OutgoingVideoOptions;
import com.azure.android.communication.calling.ParticipantsUpdatedListener;
import com.azure.android.communication.calling.PropertyChangedEvent;
import com.azure.android.communication.calling.PropertyChangedListener;
import com.azure.android.communication.calling.StartCallOptions;
import com.azure.android.communication.calling.StartTeamsCallOptions;
import com.azure.android.communication.calling.TeamsCallAgentOptions;
import com.azure.android.communication.calling.VideoDeviceInfo;
import com.azure.android.communication.calling.VideoStreamType;
import com.azure.android.communication.common.CommunicationCloudEnvironment;
import com.azure.android.communication.common.CommunicationIdentifier;
import com.azure.android.communication.common.CommunicationTokenCredential;
import com.azure.android.communication.calling.CallAgent;
import com.azure.android.communication.calling.TeamsCallAgent;
import com.azure.android.communication.calling.CallClient;
import com.azure.android.communication.calling.DeviceManager;
import com.azure.android.communication.calling.VideoOptions;
import com.azure.android.communication.calling.LocalVideoStream;
import com.azure.android.communication.calling.VideoStreamRenderer;
import com.azure.android.communication.calling.VideoStreamRendererView;
import com.azure.android.communication.calling.CreateViewOptions;
import com.azure.android.communication.calling.ScalingMode;
import com.azure.android.communication.calling.IncomingCall;
import com.azure.android.communication.calling.TeamsIncomingCall;
import com.azure.android.communication.calling.Call;
import com.azure.android.communication.calling.TeamsCall;
import com.azure.android.communication.calling.AcceptCallOptions;
import com.azure.android.communication.calling.ParticipantsUpdatedEvent;
import com.azure.android.communication.calling.RemoteParticipant;
import com.azure.android.communication.calling.RemoteVideoStream;
import com.azure.android.communication.calling.RemoteVideoStreamsEvent;
import com.azure.android.communication.calling.RendererListener;
import com.azure.android.communication.common.CommunicationUserIdentifier;
import com.azure.android.communication.common.MicrosoftTeamsUserIdentifier;
import com.azure.android.communication.common.PhoneNumberIdentifier;
import com.azure.android.communication.common.UnknownIdentifier;
import okhttp3.*;
import java.net.URL;
import java.io.*;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private CallAgent callAgent;
    private TeamsCallAgent teamsCallAgent;
    private VideoDeviceInfo currentCamera;
    private LocalVideoStream currentVideoStream;
    private DeviceManager deviceManager;
    private IncomingCall incomingCall;
    private TeamsIncomingCall teamsIncomingCall;
    private Call call;
    private TeamsCall teamsCall;
    VideoStreamRenderer previewRenderer;
    VideoStreamRendererView preview;
    final Map<Integer, StreamData> streamData = new HashMap<>();
    private boolean renderRemoteVideo = true;
    private ParticipantsUpdatedListener remoteParticipantUpdatedListener;
    private PropertyChangedListener onStateChangedListener;

    final HashSet<String> joinedParticipants = new HashSet<>();

    Button switchSourceButton;
    RadioButton acsCall, cteCall, oneToOneCall, groupCall;
    CallClient callClient;
    private boolean isCte = false;
    private String endpointURL = "<YOUR_DEVTUNNEL_ENDPOINT>";
    private String call_Id = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getAllPermissions();
        setupAgent();
        setDeviceManager();

        switchSourceButton = findViewById(R.id.switch_source);
        switchSourceButton.setOnClickListener(l -> switchSource());

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        acsCall = findViewById(R.id.acs_call);
        acsCall.setOnClickListener(this::onCallTypeSelected);
        acsCall.setChecked(true);
        cteCall = findViewById(R.id.cte_call);
        cteCall.setOnClickListener(this::onCallTypeSelected);

        Button hangupButton = findViewById(R.id.hang_up);
        hangupButton.setOnClickListener(l -> hangUp());
        Button startVideo = findViewById(R.id.show_preview);
        startVideo.setOnClickListener(l -> turnOnLocalVideo());
        Button stopVideo = findViewById(R.id.hide_preview);
        stopVideo.setOnClickListener(l -> turnOffLocalVideo());
        Button reportIssue = findViewById(R.id.reportIssue_button);
        reportIssue.setOnClickListener(l -> reportIssue());
        Button submitIssue = findViewById(R.id.submitIssue_button);
        submitIssue.setOnClickListener(l -> submitIssue());



        oneToOneCall = findViewById(R.id.one_to_one_call);
        oneToOneCall.setOnClickListener(this::onCallTypeSelected);
        oneToOneCall.setChecked(true);
        groupCall = findViewById(R.id.group_call);
        groupCall.setOnClickListener(this::onCallTypeSelected);

    }

    private void getAllPermissions() {
        String[] requiredPermissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE};
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToAskFor.add(permission);
            }
        }
        if (!permissionsToAskFor.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToAskFor.toArray(new String[0]), 1);
        }
    }

    private void setDeviceManager(){
        Context context = this.getApplicationContext();
        try {
             callClient = new CallClient();
            deviceManager = callClient.getDeviceManager(context).get();
        }catch (Exception ex){
            Toast.makeText(context, "Failed to set device manager.", Toast.LENGTH_SHORT).show();
        }
    }

    private void createAgent() {
        Context context = this.getApplicationContext();
        String userToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjYwNUVCMzFEMzBBMjBEQkRBNTMxODU2MkM4QTM2RDFCMzIyMkE2MTkiLCJ4NXQiOiJZRjZ6SFRDaURiMmxNWVZpeUtOdEd6SWlwaGsiLCJ0eXAiOiJKV1QifQ.eyJza3lwZWlkIjoiYWNzOmNmMGVhZGRlLTNiMWUtNDJiNi1hZjUyLTQ1NmE4NmJhZjhmM18wMDAwMDAxZS05ZjQzLTYxZmEtZWVmMC04YjNhMGQwMDdiZjQiLCJzY3AiOjE3OTIsImNzaSI6IjE3MDkzODY2MTIiLCJleHAiOjE3MDk0NzMwMTIsInJnbiI6ImFtZXIiLCJhY3NTY29wZSI6ImNoYXQsdm9pcCIsInJlc291cmNlSWQiOiJjZjBlYWRkZS0zYjFlLTQyYjYtYWY1Mi00NTZhODZiYWY4ZjMiLCJyZXNvdXJjZUxvY2F0aW9uIjoidW5pdGVkc3RhdGVzIiwiaWF0IjoxNzA5Mzg2NjEyfQ.bDR75IQtmtS69Dci6sWIBWNytsrCm4FTZXXNsugwN3qFcJZKgO-YEh7SQmAWKXyGf2ng24sIs1cvD928Fz2XxdtQ3LxYm7U6_kpqs1CEnO4uoPBiRUXGmdTO_epCTSuKty4FCCHqG4OGhpE8oOyvI2T-UaAvYGGs-Vl44tmScEkh-D--d871V_N8r1RyjqcLBuFVH3pxCAOWfmSFXr11iT_BVIJOl_MsJ-kfiylaezE63H8LcrcvV2PHAydrd6xu76pzd44Qncr6oQ1GoC9Scca2ZMnjFZC3YMMmXCk5vOn69AJle_C0cNbXJQnLq0eu67ols0MFgzaOz509WWxSaQ";
        try {
            CommunicationTokenCredential credential = new CommunicationTokenCredential(userToken);
             callClient = new CallClient();
            callAgent = callClient.createCallAgent(getApplicationContext(), credential).get();
        } catch (Exception ex) {
            Toast.makeText(context, "Failed to create call agent.", Toast.LENGTH_SHORT).show();
        }
    }

    private void createTeamsAgent() {
        Context context = this.getApplicationContext();
        String userToken = "<USER_ACCESS_TOKEN>";
        try {
            CommunicationTokenCredential credential = new CommunicationTokenCredential(userToken);
             CallClient callClient = new CallClient();
            TeamsCallAgentOptions teamsCallAgentOptions = new TeamsCallAgentOptions();
            teamsCallAgent = callClient.createTeamsCallAgent(getApplicationContext(), credential, teamsCallAgentOptions).get();
        } catch (Exception ex) {
            Toast.makeText(context, "Failed to create teams call agent.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupAgent(){
        if(isCte){
            if (callAgent != null) {
                callAgent.dispose();
                callAgent = null;
            }
            createTeamsAgent();
            handleTeamsIncomingCall();
        }else{
            if (teamsCallAgent != null) {
                teamsCallAgent.dispose();
                teamsCallAgent = null;
            }
            createAgent();
            handleIncomingCall();
        }
        setupButtonListener();
    }

    private void handleIncomingCall() {
        callAgent.addOnIncomingCallListener((incomingCall) -> {
            this.incomingCall = incomingCall;
            Executors.newCachedThreadPool().submit(this::answerIncomingCall);
        });
    }

    private void handleTeamsIncomingCall() {
        teamsCallAgent.addOnIncomingCallListener((incomingCall) -> {
            this.teamsIncomingCall = incomingCall;
            Executors.newCachedThreadPool().submit(this::answerTeamsIncomingCall);
        });
    }

    private void startCall() {
        Context context = this.getApplicationContext();
        EditText callIdView = findViewById(R.id.call_id);
        String callId = callIdView.getText().toString();
        ArrayList<CommunicationIdentifier> participants = new ArrayList<CommunicationIdentifier>();
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();


        if(oneToOneCall.isChecked()){
            StartCallOptions options = new StartCallOptions();
            IncomingVideoOptions incomingVideoOptions = new IncomingVideoOptions();
            OutgoingVideoOptions outgoingVideoOptions = new OutgoingVideoOptions();
            OutgoingAudioOptions outgoingAudioOptions = new OutgoingAudioOptions();
            if(!cameras.isEmpty()) {
                currentCamera = getNextAvailableCamera(null);
                currentVideoStream = new LocalVideoStream(currentCamera, context);
                LocalVideoStream[] videoStreams = new LocalVideoStream[1];
                videoStreams[0] = currentVideoStream;
                incomingVideoOptions.setStreamType(VideoStreamType.REMOTE_INCOMING);
                outgoingVideoOptions.setOutgoingVideoStreams(Arrays.asList(videoStreams[0]));
                outgoingAudioOptions.setMuted(false);
                showPreview(currentVideoStream);
            }
            participants.add(new CommunicationUserIdentifier(callId));

            options.setIncomingVideoOptions(incomingVideoOptions);
            options.setOutgoingVideoOptions(outgoingVideoOptions);
            options.setOutgoingAudioOptions(outgoingAudioOptions);

            call = callAgent.startCall(
                    context,
                    participants,
                    options);
        }
        else{

            JoinCallOptions options = new JoinCallOptions();
            if(!cameras.isEmpty()) {
                currentCamera = getNextAvailableCamera(null);
                currentVideoStream = new LocalVideoStream(currentCamera, context);
                LocalVideoStream[] videoStreams = new LocalVideoStream[1];
                videoStreams[0] = currentVideoStream;
                VideoOptions videoOptions = new VideoOptions(videoStreams);
                options.setVideoOptions(videoOptions);
                showPreview(currentVideoStream);
            }
            GroupCallLocator groupCallLocator = new GroupCallLocator(UUID.fromString(callId));

            call = callAgent.join(
                    context,
                    groupCallLocator,
                    options);
        }
        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleCallOnStateChanged;
        call.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        call.addOnStateChangedListener(onStateChangedListener);
        call_Id = call.getId().toString();
    }

    private void startTeamsCall() {
        Context context = this.getApplicationContext();
        EditText callIdView = findViewById(R.id.call_id);
        String callId = callIdView.getText().toString();

        MicrosoftTeamsUserIdentifier participant;
        if (callId.startsWith("8:orgid:")){
            participant = new MicrosoftTeamsUserIdentifier(callId.substring("8:orgid:".length())).setCloudEnvironment(CommunicationCloudEnvironment.PUBLIC);
        } else if (callId.startsWith("8:dod:")) {
            participant = new MicrosoftTeamsUserIdentifier(callId.substring("8:dod:".length())).setCloudEnvironment(CommunicationCloudEnvironment.DOD);
        } else if (callId.startsWith("8:gcch:")) {
            participant = new MicrosoftTeamsUserIdentifier(callId.substring("8:gcch:".length())).setCloudEnvironment(CommunicationCloudEnvironment.GCCH);
        } else {
            participant = new MicrosoftTeamsUserIdentifier(callId).setCloudEnvironment(CommunicationCloudEnvironment.PUBLIC);
        }

        List<VideoDeviceInfo> cameras = deviceManager.getCameras();

        if(oneToOneCall.isChecked()){
            StartTeamsCallOptions options = new StartTeamsCallOptions();
            IncomingVideoOptions incomingVideoOptions = new IncomingVideoOptions();
            OutgoingVideoOptions outgoingVideoOptions = new OutgoingVideoOptions();
            OutgoingAudioOptions outgoingAudioOptions = new OutgoingAudioOptions();
            if(!cameras.isEmpty()) {
                currentCamera = getNextAvailableCamera(null);
                currentVideoStream = new LocalVideoStream(currentCamera, this);
                LocalVideoStream[] videoStreams = new LocalVideoStream[1];
                videoStreams[0] = currentVideoStream;
                incomingVideoOptions.setStreamType(VideoStreamType.REMOTE_INCOMING);
                outgoingVideoOptions.setOutgoingVideoStreams(Arrays.asList(videoStreams[0]));
                outgoingAudioOptions.setMuted(false);
                showPreview(currentVideoStream);
            }

            options.setIncomingVideoOptions(incomingVideoOptions);
            options.setOutgoingVideoOptions(outgoingVideoOptions);
            options.setOutgoingAudioOptions(outgoingAudioOptions);

            teamsCall = teamsCallAgent.startCall(
                    context,
                    participant,
                    options);
        }
        else{
            Toast.makeText(context, "Teams user cannot join a group call", Toast.LENGTH_SHORT).show();
        }

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleTeamsCallOnStateChanged;
        teamsCall.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        teamsCall.addOnStateChangedListener(onStateChangedListener);
        call_Id = call.getId().toString();
    }

    private void hangUp() {
        renderRemoteVideo = false;
        try {
            if (isCte){
                for(RemoteParticipant participant : teamsCall.getRemoteParticipants()){
                    for (RemoteVideoStream stream : participant.getVideoStreams()){
                        stopRenderingVideo(stream);
                    }
                }
                teamsCall.hangUp().get();
            }else {
                for(RemoteParticipant participant : call.getRemoteParticipants()){
                    for (RemoteVideoStream stream : participant.getVideoStreams()){
                        stopRenderingVideo(stream);
                    }
                }
                call.hangUp().get();
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        if (previewRenderer != null) {
            previewRenderer.dispose();
        }
    }

    public void turnOnLocalVideo() {
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        if(!cameras.isEmpty()) {
            try {
                currentVideoStream = new LocalVideoStream(currentCamera, this);
                showPreview(currentVideoStream);
                if (isCte){
                    teamsCall.startVideo(this, currentVideoStream).get();
                }else {
                    call.startVideo(this, currentVideoStream).get();
                }
                switchSourceButton.setVisibility(View.VISIBLE);
            } catch (CallingCommunicationException acsException) {
                acsException.printStackTrace();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void turnOffLocalVideo() {
        try {
            LinearLayout container = findViewById(R.id.localvideocontainer);
            for (int i = 0; i < container.getChildCount(); ++i) {
                Object tag = container.getChildAt(i).getTag();
                if (tag != null && (int)tag == 0) {
                    container.removeViewAt(i);
                }
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
            previewRenderer.dispose();
            previewRenderer = null;
            if(isCte){
                teamsCall.stopVideo(this, currentVideoStream).get();
            }else {
                call.stopVideo(this, currentVideoStream).get();
            }
        } catch (CallingCommunicationException acsException) {
            acsException.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void reportIssue()
    {
        View issueDescView = findViewById(R.id.issueDescription_text);
        View SubmitIssueView = findViewById(R.id.submitIssue_button);

        if (issueDescView.getVisibility() == View.INVISIBLE)
        {
            issueDescView.setVisibility(View.VISIBLE);
            SubmitIssueView.setVisibility(View.VISIBLE);
        }
        else
        {
            issueDescView.setVisibility(View.INVISIBLE);
            SubmitIssueView.setVisibility(View.INVISIBLE);
        }
    }
    public void submitIssue()
    {
        List<File> supportFiles = callClient.getDebugInfo().getSupportFiles();



        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)// connect timeout
                .readTimeout(60, TimeUnit.SECONDS) // Set connect timeout
                .writeTimeout(60, TimeUnit.SECONDS) // Set write timeout
                .build();


        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);


        // RequestBody requestBody = null;


        for (File file : supportFiles) {
            //  builder.addFormDataPart("files", file.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), file));
            RequestBody requestBody = RequestBody.create(null, file);
            System.out.println(file);

            // Create a POST request for blogfiles
            Request request = new Request.Builder()
                    .url(endpointURL+"/api/File/uploadblog")
                    .post(requestBody)
                    .build();

            // Execute the request asynchronously
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    // Handle failure
                    e.printStackTrace();
                }

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        // Handle success
                        String responseBody = response.body().string();
                        System.out.println(responseBody);
                    } else {
                        // Handle failure
                        throw new IOException("Unexpected code " + response);
                    }
                    // Close the response body to prevent resource leak
                    response.close();
                }
            });
        }
        EditText issueDesc = findViewById(R.id.issueDescription_text);
        RequestBody requestBody = RequestBody.create(null, "Last CallId: " + call_Id+ "\n"+ issueDesc.getText().toString());

        // Create a POST request for issueDesc
        Request request = new Request.Builder()
                .url(endpointURL+"/api/File/uploaddesc")
                .post(requestBody)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                // Handle failure
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Handle success
                    String responseBody = response.body().string();
                    System.out.println(responseBody);
                } else {
                    // Handle failure
                    throw new IOException("Unexpected code " + response);
                }
                // Close the response body to prevent resource leak
                response.close();
            }
        });

}


    private VideoDeviceInfo getNextAvailableCamera(VideoDeviceInfo camera) {
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        int currentIndex = 0;
        if (camera == null) {
            return cameras.isEmpty() ? null : cameras.get(0);
        }

        for (int i = 0; i < cameras.size(); i++) {
            if (camera.getId().equals(cameras.get(i).getId())) {
                currentIndex = i;
                break;
            }
        }
        int newIndex = (currentIndex + 1) % cameras.size();
        return cameras.get(newIndex);
    }

    private void showPreview(LocalVideoStream stream) {
        // Create renderer
        previewRenderer = new VideoStreamRenderer(stream, this);
        LinearLayout layout = findViewById(R.id.localvideocontainer);
        preview = previewRenderer.createView(new CreateViewOptions(ScalingMode.FIT));
        preview.setTag(0);
        runOnUiThread(() -> {
            layout.addView(preview);
            switchSourceButton.setVisibility(View.VISIBLE);
        });
    }

    public void switchSource() {
        if (currentVideoStream != null) {
            try {
                currentCamera = getNextAvailableCamera(currentCamera);
                currentVideoStream.switchSource(currentCamera).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleCallOnStateChanged(PropertyChangedEvent args) {
        if (call.getState() == CallState.CONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is CONNECTED", Toast.LENGTH_SHORT).show());
            handleCallState();
        }
        if (call.getState() == CallState.DISCONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is DISCONNECTED", Toast.LENGTH_SHORT).show());
            if (previewRenderer != null) {
                previewRenderer.dispose();
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
        }
    }

    private void handleTeamsCallOnStateChanged(PropertyChangedEvent args) {
        if (teamsCall.getState() == CallState.CONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is CONNECTED", Toast.LENGTH_SHORT).show());
            handleTeamsCallState();
        }
        if (teamsCall.getState() == CallState.DISCONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is DISCONNECTED", Toast.LENGTH_SHORT).show());
            if (previewRenderer != null) {
                previewRenderer.dispose();
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
        }
    }

    private void handleCallState() {
        handleAddedParticipants(call.getRemoteParticipants());
    }

    private void handleTeamsCallState() {
        handleAddedParticipants(teamsCall.getRemoteParticipants());
    }

    private void answerIncomingCall() {
        Context context = this.getApplicationContext();
        if (incomingCall == null) {
            return;
        }
        AcceptCallOptions acceptCallOptions = new AcceptCallOptions();
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        if(!cameras.isEmpty()) {
            currentCamera = getNextAvailableCamera(null);
            currentVideoStream = new LocalVideoStream(currentCamera, context);
            LocalVideoStream[] videoStreams = new LocalVideoStream[1];
            videoStreams[0] = currentVideoStream;
            VideoOptions videoOptions = new VideoOptions(videoStreams);
            acceptCallOptions.setVideoOptions(videoOptions);
            showPreview(currentVideoStream);
        }
        try {
            call = incomingCall.accept(context, acceptCallOptions).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleCallOnStateChanged;
        call.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        call.addOnStateChangedListener(onStateChangedListener);
        call_Id = call.getId().toString();
    }

    private void answerTeamsIncomingCall() {
        Context context = this.getApplicationContext();
        if (teamsIncomingCall == null) {
            return;
        }
        AcceptCallOptions acceptCallOptions = new AcceptCallOptions();
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        if(!cameras.isEmpty()) {
            currentCamera = getNextAvailableCamera(null);
            currentVideoStream = new LocalVideoStream(currentCamera, context);
            LocalVideoStream[] videoStreams = new LocalVideoStream[1];
            videoStreams[0] = currentVideoStream;
            VideoOptions videoOptions = new VideoOptions(videoStreams);
            acceptCallOptions.setVideoOptions(videoOptions);
            showPreview(currentVideoStream);
        }
        try {
            teamsCall = teamsIncomingCall.accept(context, acceptCallOptions).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleTeamsCallOnStateChanged;
        teamsCall.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        teamsCall.addOnStateChangedListener(onStateChangedListener);
        call_Id = call.getId().toString();
    }

    public void handleRemoteParticipantsUpdate(ParticipantsUpdatedEvent args) {
        handleAddedParticipants(args.getAddedParticipants());
        handleRemovedParticipants(args.getRemovedParticipants());
    }

    private void handleAddedParticipants(List<RemoteParticipant> participants) {
        for (RemoteParticipant remoteParticipant : participants) {
            if(!joinedParticipants.contains(getId(remoteParticipant))) {
                joinedParticipants.add(getId(remoteParticipant));

                if (renderRemoteVideo) {
                    for (RemoteVideoStream stream : remoteParticipant.getVideoStreams()) {
                        if (!streamData.containsKey(stream.getId())) {
                            Log.i("MainActivity", "HandleAddedParticipants => Started Rendering of Remote Video for video Id: " + stream.getId());
                            StreamData data = new StreamData(stream, null, null);
                            streamData.put(stream.getId(), data);
                            startRenderingVideo(data);
                        } else {
                            Log.w("MainActivity", "HandleAddedParticipants => Rendering of Remote Video already started for video Id: " + stream.getId());
                        }
                    }
                }
                remoteParticipant.addOnVideoStreamsUpdatedListener(videoStreamsEventArgs -> videoStreamsUpdated(videoStreamsEventArgs));
            }
        }
    }

    public String getId(final RemoteParticipant remoteParticipant) {
        final CommunicationIdentifier identifier = remoteParticipant.getIdentifier();
        if (identifier instanceof PhoneNumberIdentifier) {
            return ((PhoneNumberIdentifier) identifier).getPhoneNumber();
        } else if (identifier instanceof MicrosoftTeamsUserIdentifier) {
            return ((MicrosoftTeamsUserIdentifier) identifier).getUserId();
        } else if (identifier instanceof CommunicationUserIdentifier) {
            return ((CommunicationUserIdentifier) identifier).getId();
        } else {
            return ((UnknownIdentifier) identifier).getId();
        }
    }

    private void handleRemovedParticipants(List<RemoteParticipant> removedParticipants) {
        for (RemoteParticipant remoteParticipant : removedParticipants) {
            if(joinedParticipants.contains(getId(remoteParticipant))) {
                joinedParticipants.remove(getId(remoteParticipant));
            }
        }
    }

    private void videoStreamsUpdated(RemoteVideoStreamsEvent videoStreamsEventArgs) {
        for(RemoteVideoStream stream : videoStreamsEventArgs.getAddedRemoteVideoStreams()) {
            if (!streamData.containsKey(stream.getId())) {
                Log.i("MainActivity", "VideoStreamsUpdated => Started Rendering of Remote Video for video Id: " + stream.getId());
                StreamData data = new StreamData(stream, null, null);
                streamData.put(stream.getId(), data);
                if (renderRemoteVideo) {
                    startRenderingVideo(data);
                }
            } else {
                Log.w("MainActivity", "VideoStreamsUpdated => Rendering of Remote Video already started for video Id: " + stream.getId());
            }
        }

        for(RemoteVideoStream stream : videoStreamsEventArgs.getRemovedRemoteVideoStreams()) {
            stopRenderingVideo(stream);
        }
    }

    void startRenderingVideo(StreamData data){
        if (data.renderer != null) {
            return;
        }
        GridLayout layout = ((GridLayout)findViewById(R.id.remotevideocontainer));
        data.renderer = new VideoStreamRenderer(data.stream, this);
        data.renderer.addRendererListener(new RendererListener() {
            @Override
            public void onFirstFrameRendered() {
                String text = data.renderer.getSize().toString();
                Log.i("MainActivity", "Video rendering at: " + text);
            }

            @Override
            public void onRendererFailedToStart() {
                String text = "Video failed to render";
                Log.i("MainActivity", text);
            }
        });
        data.rendererView = data.renderer.createView(new CreateViewOptions(ScalingMode.FIT));
        data.rendererView.setTag(data.stream.getId());
        runOnUiThread(() -> {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(layout.getLayoutParams());
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            params.height = (int)(displayMetrics.heightPixels / 2.5);
            params.width = displayMetrics.widthPixels / 2;
            layout.addView(data.rendererView, params);
        });
    }

    void stopRenderingVideo(RemoteVideoStream stream) {
        StreamData data = streamData.get(stream.getId());
        if (data == null || data.renderer == null) {
            return;
        }
        runOnUiThread(() -> {
            GridLayout layout = findViewById(R.id.remotevideocontainer);
            for(int i = 0; i < layout.getChildCount(); ++ i) {
                View childView =  layout.getChildAt(i);
                if ((int)childView.getTag() == data.stream.getId()) {
                    layout.removeViewAt(i);
                }
            }
        });
        data.rendererView = null;
        // Dispose renderer
        data.renderer.dispose();
        data.renderer = null;
    }

    static class StreamData {
        RemoteVideoStream stream;
        VideoStreamRenderer renderer;
        VideoStreamRendererView rendererView;
        StreamData(RemoteVideoStream stream, VideoStreamRenderer renderer, VideoStreamRendererView rendererView) {
            this.stream = stream;
            this.renderer = renderer;
            this.rendererView = rendererView;
        }
    }

    public void onCallTypeSelected(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        EditText callIdView = findViewById(R.id.call_id);

        switch(view.getId()) {
            case R.id.acs_call:
                if(checked){
                    isCte = false;
                    setupAgent();
                }
                break;
            case R.id.cte_call:
                if(checked){
                    isCte = true;
                    setupAgent();
                }
                break;
            case R.id.one_to_one_call:
                if (checked){
                    callIdView.setHint("Callee id");
                }
                break;
            case R.id.group_call:
                if (checked){
                    callIdView.setHint("Group Call GUID");
                }
                break;
        }
    }

    private void setupButtonListener(){
        Button callButton = findViewById(R.id.call_button);
        if(isCte) {
            callButton.setOnClickListener(l -> startTeamsCall());
        }else{
            callButton.setOnClickListener(l -> startCall());
        }
    }
}
