package info.fathom.androidexperiments;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.provider.WearableCalendarContract;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.SurfaceHolder;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataSourcesResult;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class FitDataFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "FitDataFaceService";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);



//    ????????????????????????????????     ????????????????????   ??????????????????   ??? ???????
//    ?????????????????????????????????    ?????????????????????  ???????????????????  ???????????
//    ????????   ???   ??????  ????????    ??????????????  ?????? ???????????????????? ??????  ????
//    ????????   ???   ??????  ???????     ??????????????  ??????????????????????????????????   ???
//    ????????   ???   ???????????         ??????????????????? ???????????????????? ???????????????
//    ????????   ???   ???????????         ???????????????????  ???????????????????  ????? ???????
    private SensorManager mSensorStepCountManager;
    private Sensor mSensorStepCount;
    private boolean mIsSensorStepCountRegistered;
    private int mStepsToday;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
//            Log.i(TAG, event.toString());
//            Log.i(TAG, event.values.toString());
            Log.i(TAG, "New step count: " + Float.toString(event.values[0]));
            mStepsToday = Math.round(event.values[0]);
        }
    }




//    ????????????????????     ?????? ??????? ???
//    ????????????????????    ???????????????????
//    ??????  ???   ???       ???????????????????
//    ??????  ???   ???       ??????????????? ???
//    ???     ???   ???       ???  ??????     ???
//    ???     ???   ???       ???  ??????     ???
    /**
     * https://developers.google.com/fit/android/get-started#step_5_connect_to_the_fitness_service
     */
    private static final int REQUEST_OAUTH = 1;
    /**
     *  Track whether an authorization activity is stacking over the current activity, i.e. when
     *  a known auth error is being resolved, such as showing the account chooser or presenting a
     *  consent dialog. This avoids common duplications as might happen on screen rotations, etc.
     */
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private GoogleApiClient mClient = null;

    /**
     *  Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     *  to connect to Fitness APIs. The scopes included should match the scopes your app needs
     *  (see documentation for details). Authentication will occasionally fail intentionally,
     *  and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     *  can address. Examples of this include the user never having signed in before, or having
     *  multiple accounts on the device and needing to specify which account to use, etc.
     */
    private void buildFitnessClient() {
        Log.i(TAG, "Building Fitness Client");
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.SENSORS_API)
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                                Log.i(TAG, bundle.toString());
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
//                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
//                                            FitDataFaceService.this, 0).show();
                                    Log.e(TAG, "Error number: " +
                                            GooglePlayServicesUtil.getErrorString(result.getErrorCode()));
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
//                                    try {
                                    Log.i(TAG, "Attempting to resolve failed connection");
                                    authInProgress = true;
//                                        result.startResolutionForResult(MainActivity.this,
//                                                REQUEST_OAUTH);
//                                    } catch (IntentSender.SendIntentException e) {
//                                        Log.e(TAG,
//                                                "Exception while starting resolution activity", e);
//                                    }
                                } else {
                                    Log.i(TAG, "Down here...");
                                }
                            }
                        }
                )
                .build();
    }












    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private class Engine extends CanvasWatchFaceService.Engine {

        private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
        private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

//        private final int TEXT_COLOR_INTERACTIVE = Color.argb(127, 175, 175, 175);
        private final int TEXT_COLOR_INTERACTIVE = Color.WHITE;
        private final int TEXT_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_HEIGHT = 0.05f;  // as a factor of screen height

        int mNumMeetings;
        int mNumWatchPeeps = 0;
        private AsyncTask<Void, Void, Integer> mLoadMeetingsTask;

        private static final int MSG_UPDATE_TIMER = 0;
        private static final int MSG_LOAD_MEETINGS = 1;





        /* Handler to update the time once a second in interactive mode. */
        final Handler mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                Log.i(TAG, "handling msg " + message.what);
                switch (message.what) {
                    case MSG_UPDATE_TIMER:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                          long timeMs = System.currentTimeMillis();
                          long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                  - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mMainHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIMER, delayMs);
                        }
                        break;

                    case MSG_LOAD_MEETINGS:
                        cancelLoadMeetingTask();
                        mLoadMeetingsTask = new LoadMeetingsTask();
                        mLoadMeetingsTask.execute();
//                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
//                            Log.v(TAG, "mLoadMeetingsHandler triggered");
//                        }
                        break;
                }
            }
        };


        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mAmbient;

        private Time mTime;

        private Paint mTextPaintInteractive, mTextPaintAmbient;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

//        private final Rect textBounds = new Rect();
        private float mTextHeight;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(FitDataFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            /**
             * STEP SENSING
             */
            mSensorStepCountManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorStepCount = mSensorStepCountManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            // REGISTERING MOVED TO onVisibilityChanged

            /**
             * FIT API
             */
            // this doesn't work, since the Engine.onCreate method works differently than an activity
//            if (savedInstanceState != null) {
//                authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
//            }

            authInProgress = true;

            buildFitnessClient();

            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(NORMAL_TYPEFACE);
            mTextPaintInteractive.setAntiAlias(true);

            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(NORMAL_TYPEFACE);
            mTextPaintAmbient.setAntiAlias(false);

            mTime  = new Time();
        }

        @Override
        public void onDestroy() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            mMainHandler.removeMessages(MSG_LOAD_MEETINGS);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                invalidate();
            }

            if (!inAmbientMode) mNumWatchPeeps++;

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();

//            Log.v(TAG, "AMBIENT MODE: " + mAmbient);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

            mTextHeight = TEXT_HEIGHT * mHeight;
            mTextPaintInteractive.setTextSize(mTextHeight);
            mTextPaintAmbient.setTextSize(mTextHeight);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

            final int hour1to12 = mTime.hour % 12 == 0 ? 12 : mTime.hour % 12;
            final String hourStr = hour1to12 > 9 ? Integer.toString(hour1to12) : "0" + Integer.toString(hour1to12);
            final String minuteStr = mTime.minute > 9 ? Integer.toString(mTime.minute) : "0" + Integer.toString(mTime.minute);

            // Start drawing watch elements
            canvas.save();

            if (mAmbient) {
                final String timeStr = hourStr + ":" + minuteStr;

                canvas.drawColor(BACKGROUND_COLOR_AMBIENT);  // background
                canvas.drawText(timeStr, 25, 125, mTextPaintAmbient);
                canvas.drawText(mNumMeetings + " meetings today", 25, 150, mTextPaintAmbient);
                canvas.drawText(mNumWatchPeeps + " peeps at your watch today", 25, 175, mTextPaintAmbient);
                canvas.drawText(mStepsToday + " steps today", 25, 200, mTextPaintAmbient);

            } else {
                final String secondStr = mTime.second > 9 ? Integer.toString(mTime.second) : "0" + Integer.toString(mTime.second);
                final String timeStr = hourStr + ":" + minuteStr + ":" + secondStr;

                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);
                canvas.drawText(timeStr, 25, 125, mTextPaintInteractive);
                canvas.drawText(mNumMeetings + " meetings today", 25, 150, mTextPaintInteractive);
                canvas.drawText(mNumWatchPeeps + " peeps at your watch today", 25, 175, mTextPaintInteractive);
                canvas.drawText(mStepsToday + " steps today", 25, 200, mTextPaintInteractive);
            }
        }


        private boolean mIsCalendarReceiverRegistered;
        private BroadcastReceiver mCalendarReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_PROVIDER_CHANGED.equals(intent.getAction())
                        && WearableCalendarContract.CONTENT_URI.equals(intent.getData())) {
                    cancelLoadMeetingTask();
                    mMainHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);
                }
            }
        };

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            Log.i(TAG, "onVisibilityChanged: " + visible);

            if (visible) {
                registerTimeReceiver();
                registerStepSensor();

                // Register Calendar receiver
                IntentFilter filter = new IntentFilter(Intent.ACTION_PROVIDER_CHANGED);
                filter.addDataScheme("content");
                filter.addDataAuthority(WearableCalendarContract.AUTHORITY, null);
                registerReceiver(mCalendarReceiver, filter);
                mIsCalendarReceiverRegistered = true;

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

                mMainHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);

            } else {
                unregisterTimeReceiver();
                unregisterStepSensor();

                if (mIsCalendarReceiverRegistered) {
                    unregisterReceiver(mCalendarReceiver);
                    mIsCalendarReceiverRegistered = false;
                }

                mMainHandler.removeMessages(MSG_LOAD_MEETINGS);
                cancelLoadMeetingTask();
            }

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
//            mCardBounds.set(rect);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
//            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
//            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }










        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        private void registerTimeReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            FitDataFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            FitDataFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void registerStepSensor() {
            if (mIsSensorStepCountRegistered) {
                return;
            }
            mIsSensorStepCountRegistered = true;
            mSensorStepCountManager.registerListener(FitDataFaceService.this, mSensorStepCount, SensorManager.SENSOR_DELAY_NORMAL);
        }

        private void unregisterStepSensor() {
            if (!mIsSensorStepCountRegistered) {
                return;
            }
            mIsSensorStepCountRegistered = false;
            mSensorStepCountManager.unregisterListener(FitDataFaceService.this);
        }




        private void updateTimer() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            if (shouldTimerBeRunning()) {
                mMainHandler.sendEmptyMessage(MSG_UPDATE_TIMER);
            }
        }

        /**
         * Returns whether the {@link #mMainHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void onMeetingsLoaded(Integer result) {
            if (result != null) {
                mNumMeetings = result;
                invalidate();
            }
        }

        private void cancelLoadMeetingTask() {
            if (mLoadMeetingsTask != null) {
                mLoadMeetingsTask.cancel(true);
            }
        }

        /* Asynchronous task to load the meetings from the content provider and
        * report the number of meetings back using onMeetingsLoaded() */
        private class LoadMeetingsTask extends AsyncTask<Void, Void, Integer> {
            @Override
            protected Integer doInBackground(Void... voids) {
                long begin = System.currentTimeMillis();
                Uri.Builder builder =
                        WearableCalendarContract.Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(builder, begin);
                ContentUris.appendId(builder, begin + DateUtils.DAY_IN_MILLIS);
                final Cursor cursor = getContentResolver().query(builder.build(), null, null, null, null);
                int numMeetings = cursor.getCount();
//                if (Log.isLoggable(TAG, Log.VERBOSE)) {
//                    Log.v(TAG, "Num meetings: " + numMeetings);
//                }
                return numMeetings;
            }

            @Override
            protected void onPostExecute(Integer result) {
                /* get the number of meetings and set the next timer tick */
                onMeetingsLoaded(result);

            }
        }


    }


}
