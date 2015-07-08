package info.fathom.androidexperiments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.util.Log;

import java.util.HashMap;
import java.util.TimeZone;



public class AllSensorsFaceService extends CanvasWatchFaceService implements SensorEventListener {

    // Some global stuff
    private static final String TAG = "AllSensorsFaceService";
    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    // Internal 'sensor' stuff
    private int mNumWatchPeeps = 0;

    // Some sensor stuff
    private SensorManager mSensorManager;

    private Sensor mStepSensor;
    private boolean mStepIsRegistered;
    private int mStepValue;


    SensorWrapper[] sensors = new SensorWrapper[14];


    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {

            case Sensor.TYPE_MAGNETIC_FIELD:
                sensors[0].update(event);
                break;

            case Sensor.TYPE_LIGHT:
                sensors[1].update(event);
                break;

            case Sensor.TYPE_ACCELEROMETER:
                sensors[2].update(event);
                break;

            case Sensor.TYPE_GRAVITY:
                sensors[3].update(event);
                break;

            case Sensor.TYPE_LINEAR_ACCELERATION:
                sensors[4].update(event);
                break;

            case Sensor.TYPE_GYROSCOPE:
                sensors[5].update(event);
                break;

            case Sensor.TYPE_STEP_COUNTER:
                sensors[6].update(event);
                break;

            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                sensors[7].update(event);
                break;

            case Sensor.TYPE_ORIENTATION:
                sensors[8].update(event);
                break;

            case Sensor.TYPE_PRESSURE:
                sensors[9].update(event);
                break;

            case Sensor.TYPE_PROXIMITY:
                sensors[10].update(event);
                break;

            case Sensor.TYPE_RELATIVE_HUMIDITY:
                sensors[11].update(event);
                break;

            case Sensor.TYPE_ROTATION_VECTOR:
                sensors[12].update(event);
                break;

            case Sensor.TYPE_HEART_RATE:
                sensors[13].update(event);
                break;
        }

    }

    public void initializeSensors() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        sensors[0] = new SensorWrapper("Magnetic Field", Sensor.TYPE_MAGNETIC_FIELD, 3);
        sensors[1] = new SensorWrapper("Light", Sensor.TYPE_LIGHT, 1);
        sensors[2] = new SensorWrapper("Accelerometer", Sensor.TYPE_ACCELEROMETER, 3);
        sensors[3] = new SensorWrapper("Gravity", Sensor.TYPE_GRAVITY, 3);
        sensors[4] = new SensorWrapper("Linear Acceleration", Sensor.TYPE_LINEAR_ACCELERATION, 3);
        sensors[5] = new SensorWrapper("Gyroscope", Sensor.TYPE_GYROSCOPE, 3);
        sensors[6] = new SensorWrapper("Steps", Sensor.TYPE_STEP_COUNTER, 1);
        sensors[7] = new SensorWrapper("Temperature", Sensor.TYPE_AMBIENT_TEMPERATURE, 1);
        sensors[8] = new SensorWrapper("Orientation", Sensor.TYPE_ORIENTATION, 3);
        sensors[9] = new SensorWrapper("Pressure", Sensor.TYPE_PRESSURE, 1);
        sensors[10] = new SensorWrapper("Proximity", Sensor.TYPE_PROXIMITY, 1);
        sensors[11] = new SensorWrapper("Humidity", Sensor.TYPE_RELATIVE_HUMIDITY, 1);
        sensors[12] = new SensorWrapper("Rotation", Sensor.TYPE_ROTATION_VECTOR, 3);
        sensors[13] = new SensorWrapper("Heart rate", Sensor.TYPE_HEART_RATE, 1);
    }

    public void registerAllSensors() {
        for (int i = 0; i < sensors.length; i++) {
            sensors[i].register();
        }
    }

    public void unregisterAllSensors() {
        for (int i = 0; i < sensors.length; i++) {
            sensors[i].unregister();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor: " + sensor.getName() + ", onAccuracyChanged - accuracy: " + accuracy);
    }










    private class SensorWrapper {

        String name;
        int type;
        Sensor sensor;
        boolean isActive;
        boolean isRegistered;
        int valueCount;
        float[] values;

        SensorWrapper(String name_, int sensorType_, int valueCount_) {
            name = name_;
            type = sensorType_;
            valueCount = valueCount_;
            values = new float[valueCount];

            // initialize the sensor
            sensor = mSensorManager.getDefaultSensor(type);

            // http://developer.android.com/guide/topics/sensors/sensors_overview.html#sensors-identify
            if (sensor == null) {
                Log.v(TAG, "Sensor " + name + " not available in this device");
                isActive = false;
                isRegistered = false;
            } else {
                isActive = true;
                isRegistered = true;
            }
        }

        boolean register() {
            if (!isActive) return false;
            if (isRegistered) return true;
            isRegistered = mSensorManager.registerListener(AllSensorsFaceService.this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "Registered " + name + ": " + isRegistered);
            return isRegistered;
        }

        boolean unregister() {
            if (!isActive) return false;
            if (!isRegistered) return false;
            mSensorManager.unregisterListener(AllSensorsFaceService.this);
            isRegistered = false;
            Log.i(TAG, "Unregistered " + name);
            return false;
        }

        String stringify() {
            if (!isActive) return name + " sensor not available in this device";
            String vals = name + ": [";
            for (int i = 0; i < valueCount; i++) {
                vals += String.format("%.2f", values[i]);
                if (i + 1 < valueCount) vals += ", ";
            }
            vals += "]";
            return vals;
        }

        void update(SensorEvent event) {
            for (int i = 0; i < valueCount; i++) {
                values[i] = event.values[i];
            }
        }
    }







    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private class Engine extends CanvasWatchFaceService.Engine {

        private static final int MSG_UPDATE_TIMER = 0;

        private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
        private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;
        private static final int TEXT_COLOR_INTERACTIVE = Color.WHITE;
        private static final int TEXT_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_HEIGHT = 0.05f;  // as a factor of screen height

        /* Handler to update the screen depending on the message */
        final Handler mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
//                Log.i(TAG, "handling msg " + message.what);
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
                }
            }
        };

        // Time and layout stuff
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mAmbient;
        private Time mTime;
        private Paint mTextPaintInteractive, mTextPaintAmbient;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mTextHeight;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(AllSensorsFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            // Initialize Paints
            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(NORMAL_TYPEFACE);
            mTextPaintInteractive.setAntiAlias(true);
            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(NORMAL_TYPEFACE);
            mTextPaintAmbient.setAntiAlias(false);

            // Initialize Sensors
            initializeSensors();

            mTime  = new Time();
        }

        @Override
        public void onDestroy() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
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

            if (inAmbientMode) {
                unregisterAllSensors();
            } else {
                registerAllSensors();
            }

            // If changed to interactive mode, update peep counter
            if (!inAmbientMode) mNumWatchPeeps++;

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
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

            Paint currentPaint;
            String timeStr;

            if (mAmbient) {
                currentPaint = mTextPaintAmbient;
                timeStr = hourStr + ":" + minuteStr;
            } else {
                currentPaint = mTextPaintInteractive;
                String secondStr = mTime.second > 9 ? Integer.toString(mTime.second) : "0" + Integer.toString(mTime.second);
                timeStr = hourStr + ":" + minuteStr + ":" + secondStr;
            }

            canvas.drawColor(BACKGROUND_COLOR_AMBIENT);  // background
            canvas.drawText(timeStr, 75, 25, currentPaint);
            canvas.drawText(mNumWatchPeeps + " peeps at your watch today", 25, 40, currentPaint);

            for (int i = 0, lineStart = 55; i < sensors.length; i++, lineStart += 15) {
                canvas.drawText(sensors[i].stringify(), 25, lineStart, currentPaint);
            }

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            Log.i(TAG, "onVisibilityChanged: " + visible);

            if (visible) {
                registerTimeReceiver();
                registerAllSensors();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

            } else {
                unregisterTimeReceiver();
                unregisterAllSensors();
            }

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }

        /////////////////////////
        // SENSOR REGISTRATION //
        // (to optimize)       //
        /////////////////////////
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
            AllSensorsFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            AllSensorsFaceService.this.unregisterReceiver(mTimeZoneReceiver);
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


    }





}
