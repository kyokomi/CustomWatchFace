package com.kyokomi.customwatchface;

import android.app.Activity;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link DataApiFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link DataApiFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class DataApiFragment extends Fragment implements DataApi.DataListener,
        MessageApi.MessageListener, NodeApi.NodeListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = DataApiFragment.class.getSimpleName();

    /** Request code for launching the Intent to resolve Google Play services errors. */
    private static final int REQUEST_RESOLVE_ERROR = 1000;

    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String COUNT_PATH = "/count";
    private static final String IMAGE_PATH = "/image";
    private static final String IMAGE_KEY = "photo";
    private static final String COUNT_KEY = "count";

    private OnFragmentInteractionListener mListener;

    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError = false;

    // Send DataItems.
    private ScheduledExecutorService mGeneratorExecutor;
    private ScheduledFuture<?> mDataItemGeneratorFuture;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment DataApiFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static DataApiFragment newInstance() {
        DataApiFragment fragment = new DataApiFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }
    public DataApiFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;

            mGeneratorExecutor = new ScheduledThreadPoolExecutor(1);
            mGoogleApiClient = new GoogleApiClient.Builder(activity)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        public void onConnected();
        public void onConnectionSuspended();
        public void onConnectionFailed();
        public void onDataChanged(DataEventBuffer dataEvents);
        public void onMessageReceived(final MessageEvent messageEvent);
        public void onPeerConnected(final Node peer);
        public void onPeerDisconnected(final Node peer);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mDataItemGeneratorFuture = mGeneratorExecutor.scheduleWithFixedDelay(
                new DataItemGenerator(), 1, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onPause() {
        super.onPause();
        mDataItemGeneratorFuture.cancel(true /* mayInterruptIfRunning */);
    }

    @Override
    public void onStop() {
        if (!mResolvingError) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override //ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "Google API Client was connected");
        mResolvingError = false;

        mListener.onConnected();

        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.NodeApi.addListener(mGoogleApiClient, this);
    }

    @Override //ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "Connection to Google API client was suspended");
        mListener.onConnectionSuspended();
    }

    @Override //OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(getActivity(), REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            mResolvingError = false;

            mListener.onConnectionFailed();
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
        }
    }

    @Override //DataListener
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);
        mListener.onDataChanged(dataEvents);
    }

    @Override //MessageListener
    public void onMessageReceived(final MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived() A message from watch was received:" + messageEvent
                .getRequestId() + " " + messageEvent.getPath());
        mListener.onMessageReceived(messageEvent);
    }

    @Override //NodeListener
    public void onPeerConnected(final Node peer) {
        Log.d(TAG, "onPeerConnected: " + peer);
        mListener.onPeerConnected(peer);
    }

    @Override //NodeListener
    public void onPeerDisconnected(final Node peer) {
        Log.d(TAG, "onPeerDisconnected: " + peer);
        mListener.onPeerDisconnected(peer);
    }

    /** Generates a DataItem based on an incrementing count. */
    private class DataItemGenerator implements Runnable {

        private int count = 0;

        @Override
        public void run() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(COUNT_PATH);
            putDataMapRequest.getDataMap().putInt(COUNT_KEY, count++);
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            Log.d(TAG, "Generating DataItem: " + request);
            if (!mGoogleApiClient.isConnected()) {
                return;
            }
            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.e(TAG, "ERROR: failed to putDataItem, status code: "
                                        + dataItemResult.getStatus().getStatusCode());
                            }
                        }
                    });
        }
    }

    /**
     * Sends an RPC to start a fullscreen Activity on the wearable.
     */
    public void onStartWearableActivityClick() {
        Log.d(TAG, "Generating RPC");

        // Trigger an AsyncTask that will query for a list of connected nodes and send a
        // "start-activity" message to each connected node.
        new StartWearableActivityTask().execute();
    }

    private class StartWearableActivityTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... args) {
            Collection<String> nodes = getNodes();
            for (String node : nodes) {
                sendStartActivityMessage(node);
            }
            return null;
        }
    }

    private void sendStartActivityMessage(String node) {
        Wearable.MessageApi.sendMessage(
                mGoogleApiClient, node, START_ACTIVITY_PATH, new byte[0]).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message with status code: "
                                    + sendMessageResult.getStatus().getStatusCode());
                        }
                    }
                }
        );
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }

        return results;
    }

    /**
     * Sends the asset that was created form the photo we took by adding it to the Data Item store.
     */
    private void sendPhoto(Asset asset) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(IMAGE_PATH);
        dataMap.getDataMap().putAsset(IMAGE_KEY, asset);
        dataMap.getDataMap().putLong("time", new Date().getTime());
        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        Log.d(TAG, "Sending image was successful: " + dataItemResult.getStatus()
                                .isSuccess());
                    }
                });

    }
    public void onSendPhotoClick(Bitmap imageBitmap) {
        if (null != imageBitmap && mGoogleApiClient.isConnected()) {
            sendPhoto(toAsset(imageBitmap));
        }
    }

    /**
     * Builds an {@link com.google.android.gms.wearable.Asset} from a bitmap. The image that we get
     * back from the camera in "data" is a thumbnail size. Typically, your image should not exceed
     * 320x320 and if you want to have zoom and parallax effect in your app, limit the size of your
     * image to 640x400. Resize your image before transferring to your wearable device.
     */
    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
