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

    private Sensor mAccelerometerSensor;
    private boolean mAccelerometerIsRegistered;
    private float[] mAccelerometerValues = new float[3];
    private float[] mAccelerometerLinearAcceleration = new float[3];
    private float[] mAccelerometerGravity = new float[3];

    private Sensor mGravitySensor;
    private boolean mGravityIsRegistered;
    private float[] mGravityValues = new float[3];

    private Sensor mLinearAccelerationSensor;
    private boolean mLinearAccelerationIsRegistered;
    private float[] mLinearAccelerationValues = new float[3];

    private Sensor mGyroscopeSensor;
    private boolean mGyroscopeIsRegistered;
    private float[] mGyroscopeValues = new float[3];

    private Sensor mTemperatureSensor;
    private boolean mTemperatureIsRegistered;
    private float mTemperatureValue = 0.0f;

    private Sensor mLightSensor;
    private boolean mLightIsRegistered;
    private float mLightValue = 0.0f;




    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_COUNTER:
                mStepValue = Math.round(event.values[0]);
                break;

            case Sensor.TYPE_ACCELEROMETER:
                updateAccelerometerValues(event.values);
                break;

            case Sensor.TYPE_GRAVITY:
                mGravityValues = event.values;
                break;

            case Sensor.TYPE_LINEAR_ACCELERATION:
                mLinearAccelerationValues = event.values;
                break;

            case Sensor.TYPE_GYROSCOPE:
                mGyroscopeValues = event.values;
                break;

            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                mTemperatureValue = event.values[0];
                break;

            case Sensor.TYPE_LIGHT:
                mLightValue = event.values[0];
                break;
        }

    }

    public void initializeSensors() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mLinearAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        //mTemperatureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        //mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }

    public void registerAllSensors() {
        mStepIsRegistered = registerSensor(mStepSensor, mStepIsRegistered);
        mAccelerometerIsRegistered = registerSensor(mAccelerometerSensor, mAccelerometerIsRegistered);
        mGravityIsRegistered = registerSensor(mGravitySensor, mGravityIsRegistered);
        mLinearAccelerationIsRegistered = registerSensor(mLinearAccelerationSensor, mLinearAccelerationIsRegistered);
        mGyroscopeIsRegistered = registerSensor(mGyroscopeSensor, mGyroscopeIsRegistered);
        //mTemperatureIsRegistered = registerSensor(mTemperatureSensor, mTemperatureIsRegistered);
        //mLightIsRegistered = registerSensor(mLightSensor, mLightIsRegistered);
    }

    public void unregisterAllSensors() {
        mStepIsRegistered = unregisterSensor(mStepSensor, mStepIsRegistered);
        mAccelerometerIsRegistered = unregisterSensor(mAccelerometerSensor, mAccelerometerIsRegistered);
        mGravityIsRegistered = unregisterSensor(mGravitySensor, mGravityIsRegistered);
        mLinearAccelerationIsRegistered = unregisterSensor(mLinearAccelerationSensor, mLinearAccelerationIsRegistered);
        mGyroscopeIsRegistered = unregisterSensor(mGyroscopeSensor, mGyroscopeIsRegistered);
        //mTemperatureIsRegistered = unregisterSensor(mTemperatureSensor, mTemperatureIsRegistered);
        //mLightIsRegistered = unregisterSensor(mLightSensor, mLightIsRegistered);
    }



    private boolean registerSensor(Sensor sensor, boolean sensorFlag) {
        if (sensorFlag) return true;
        sensorFlag = mSensorManager.registerListener(AllSensorsFaceService.this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(TAG, "Registered " + sensor.getName() + ": " + sensorFlag);
        return sensorFlag;
    }

    private boolean unregisterSensor(Sensor sensor, boolean sensorFlag) {
        if (!sensorFlag) return false;
        mSensorManager.unregisterListener(AllSensorsFaceService.this, sensor);
        Log.i(TAG, "Unregistered " + sensor.getName());
        return false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor: " + sensor.getName() + ", onAccuracyChanged - accuracy: " + accuracy);
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
            final String accel = "[" + String.format("%.2f", mAccelerometerValues[0]) + ", "
                    + String.format("%.2f", mAccelerometerValues[1]) + ", "
                    + String.format("%.2f", mAccelerometerValues[2]) + "]";
            final String linAccelComp = "[" + String.format("%.2f", mAccelerometerLinearAcceleration[0]) + ", "
                    + String.format("%.2f", mAccelerometerLinearAcceleration[1]) + ", "
                    + String.format("%.2f", mAccelerometerLinearAcceleration[2]) + "]";
            final String linAccel = "[" + String.format("%.2f", mLinearAccelerationValues[0]) + ", "
                    + String.format("%.2f", mLinearAccelerationValues[1]) + ", "
                    + String.format("%.2f", mLinearAccelerationValues[2]) + "]";
            final String gravityComp = "[" + String.format("%.2f", mAccelerometerGravity[0]) + ", "
                    + String.format("%.2f", mAccelerometerGravity[1]) + ", "
                    + String.format("%.2f", mAccelerometerGravity[2]) + "]";
            final String gravity = "[" + String.format("%.2f", mGravityValues[0]) + ", "
                    + String.format("%.2f", mGravityValues[1]) + ", "
                    + String.format("%.2f", mGravityValues[2]) + "]";
            final String gyroscope = "[" + String.format("%.2f", mGyroscopeValues[0]) + ", "
                    + String.format("%.2f", mGyroscopeValues[1]) + ", "
                    + String.format("%.2f", mGyroscopeValues[2]) + "]";
            final String temperature = "[" + String.format("%.2f", mTemperatureValue) + "]";
            final String light = "[" + String.format("%.2f", mLightValue) + "]";


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
            canvas.drawText(mStepValue + " steps today", 25, 55, currentPaint);
            canvas.drawText("Accelerometer: " + accel, 25, 70, currentPaint);
            canvas.drawText("Lin accel: " + linAccelComp, 25, 85, currentPaint);
            canvas.drawText("Lin accel*: " + linAccelComp, 25, 100, currentPaint);
            canvas.drawText("Gravity: " + gravity, 25, 115, currentPaint);
            canvas.drawText("Gravity*: " + gravityComp, 25, 130, currentPaint);
            canvas.drawText("Gyroscope: " + gyroscope, 25, 145, currentPaint);
            canvas.drawText("Temperature: " + temperature, 25, 160, currentPaint);
            canvas.drawText("Light: " + light, 25, 175, currentPaint);

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














    public void updateAccelerometerValues(float[] accelValues) {
        // In this example, alpha is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the event delivery rate.
        final float alpha = 0.8f;

        for (int i = 0; i < 3; i++) {
            // Raw sensor values
            mAccelerometerValues[i] = accelValues[i];

            // Isolate the force of gravity with the low-pass filter.
            mAccelerometerGravity[i] = alpha * mAccelerometerGravity[i] + (1 - alpha) * accelValues[i];

            // Remove the gravity contribution with the high-pass filter.
            mAccelerometerLinearAcceleration[i] = accelValues[i] - mAccelerometerGravity[i];
        }

    }





}
