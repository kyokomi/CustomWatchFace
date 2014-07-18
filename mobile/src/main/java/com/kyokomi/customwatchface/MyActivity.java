package com.kyokomi.customwatchface;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.soundcloud.android.crop.Crop;

import java.io.File;
import java.util.List;

/**
 * Receives its own events using a listener API designed for foreground activities. Updates a data
 * item every second while it is open. Also allows user to take a photo and send that as an asset to
 * the paired wearable.
 */
public class MyActivity extends Activity implements DataApiFragment.OnFragmentInteractionListener {

    private static final String TAG = "MainActivity";

    private ListView mDataItemList;
    private Button mTakePhotoBtn;
    private Button mSendPhotoBtn;
    private ImageView mThumbView;
    private Bitmap mImageBitmap;
    private View mStartActivityBtn;

    private DataItemAdapter mDataItemListAdapter;
    private Handler mHandler;

    static final int REQUEST_GALLERY = 1;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        mHandler = new Handler();
        Log.d(TAG, "onCreate");

        setContentView(R.layout.main_activity);
        setupViews();

        // Stores DataItems received by the local broadcaster or from the paired watch.
        mDataItemListAdapter = new DataItemAdapter(this, android.R.layout.simple_list_item_1);
        mDataItemList.setAdapter(mDataItemListAdapter);

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(new DataApiFragment(), DataApiFragment.class.getSimpleName());
        ft.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Fragment fragment = getFragmentManager().findFragmentByTag(DataApiFragment.class.getSimpleName());
        if (fragment != null) {
            getFragmentManager().beginTransaction().detach(fragment);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            beginCrop(uri);
        } else if (requestCode == Crop.REQUEST_CROP && resultCode == RESULT_OK) {
            Uri uri = Crop.getOutput(data);
            mImageBitmap = BitmapFactory.decodeFile(uri.getPath());
            mThumbView.setImageBitmap(Bitmap.createScaledBitmap(mImageBitmap, 320, 320, true));
        }
    }

    private void beginCrop(Uri source) {
        Uri outputUri = Uri.fromFile(new File(getCacheDir(), "cropped"));
        new Crop(source).output(outputUri).asSquare().withMaxSize(320, 320).start(this);
    }

    @Override
    public void onConnected() {
        mStartActivityBtn.setEnabled(true);
        mSendPhotoBtn.setEnabled(true);
    }

    @Override
    public void onConnectionSuspended() {
        mStartActivityBtn.setEnabled(false);
        mSendPhotoBtn.setEnabled(false);
    }

    @Override
    public void onConnectionFailed() {
        mStartActivityBtn.setEnabled(false);
        mSendPhotoBtn.setEnabled(false);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (DataEvent event : events) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        mDataItemListAdapter.add(
                                new Event("DataItem Changed", event.getDataItem().toString()));
                    } else if (event.getType() == DataEvent.TYPE_DELETED) {
                        mDataItemListAdapter.add(
                                new Event("DataItem Deleted", event.getDataItem().toString()));
                    }
                }
            }
        });
    }

    //MessageListener
    public void onMessageReceived(final MessageEvent messageEvent) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDataItemListAdapter.add(new Event("Message from watch", messageEvent.toString()));
            }
        });
    }

    //NodeListener
    public void onPeerConnected(final Node peer) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDataItemListAdapter.add(new Event("Connected", peer.toString()));
            }
        });
    }

    //NodeListener
    public void onPeerDisconnected(final Node peer) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDataItemListAdapter.add(new Event("Disconnected", peer.toString()));
            }
        });
    }

    /**
     * Dispatches an {@link android.content.Intent} to take a photo. Result will be returned back
     * in onActivityResult().
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(Intent.ACTION_GET_CONTENT);
        takePictureIntent.setType("image/*");
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_GALLERY);
        }
    }

    /**
     * Sets up UI components and their callback handlers.
     */
    private void setupViews() {
        mTakePhotoBtn = (Button) findViewById(R.id.takePhoto);
        mTakePhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });
        mSendPhotoBtn = (Button) findViewById(R.id.sendPhoto);
        mSendPhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataApiFragment fragment = (DataApiFragment) getFragmentManager().findFragmentByTag(
                        DataApiFragment.class.getSimpleName());
                if (fragment != null) {
                    fragment.onSendPhotoClick(mImageBitmap);
                }
            }
        });

        // Shows the image received from the handset
        mThumbView = (ImageView) findViewById(R.id.imageView);
        mDataItemList = (ListView) findViewById(R.id.data_item_list);

        mStartActivityBtn = findViewById(R.id.start_wearable_activity);
        mStartActivityBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataApiFragment fragment = (DataApiFragment) getFragmentManager().findFragmentByTag(
                        DataApiFragment.class.getSimpleName());
                if (fragment != null) {
                    fragment.onStartWearableActivityClick();
                }
            }
        });
    }


    /**
     * A View Adapter for presenting the Event objects in a list
     */
    private static class DataItemAdapter extends ArrayAdapter<Event> {

        private final Context mContext;

        public DataItemAdapter(Context context, int unusedResource) {
            super(context, unusedResource);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(android.R.layout.two_line_list_item, null);
                convertView.setTag(holder);
                holder.text1 = (TextView) convertView.findViewById(android.R.id.text1);
                holder.text2 = (TextView) convertView.findViewById(android.R.id.text2);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Event event = getItem(position);
            holder.text1.setText(event.title);
            holder.text2.setText(event.text);
            return convertView;
        }

        private class ViewHolder {

            TextView text1;
            TextView text2;
        }
    }

    private class Event {

        String title;
        String text;

        public Event(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }
}