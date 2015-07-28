package info.fathom.androidexperiments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.util.Log;
import android.view.SurfaceHolder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class TheDingDongFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "TheDingDongFaceService";

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

    private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
    private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

    private static final String   RALEWAY_TYPEFACE_PATH = "fonts/raleway-regular-enhanced.ttf";
    private static final int     TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int     TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
    private static final float   TEXT_DIGITS_HEIGHT = 0.20f;  // as a factor of screen height
    private static final float   TEXT_DIGITS_BASELINE_HEIGHT = 0.43f;  // as a factor of screen height
    private static final float   TEXT_DIGITS_RIGHT_MARGIN = 0.08f;  // as a factor of screen width

    private static final int     TEXT_STEPS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int     TEXT_STEPS_COLOR_AMBIENT = Color.WHITE;
    private static final float   TEXT_STEPS_HEIGHT = 0.10f;  // as a factor of screen height
    private static final float   TEXT_STEPS_BASELINE_HEIGHT = TEXT_DIGITS_BASELINE_HEIGHT + 0.15f;  // as a factor of screen height
    private static final float   TEXT_STEPS_RIGHT_MARGIN = 0.07f;  // as a factor of screen width

    private static final int     RESET_HOUR = 4;  // at which hour will watch face reset [0...23], -1 to deactivate

    // DEBUG
    private static final boolean DEBUG_LOGS = true;
    private static final boolean GENERATE_FAKE_STEPS = true;
    private static final int     RANDOM_FAKE_STEPS = 500;
    private static final int     MAX_STEP_THRESHOLD = 21000;






    private SensorManager mSensorManager;

    // ACCELEROMETER SENSING
    private Sensor mAccelSensor;
    private boolean mAccelSensorIsRegistered;
    // Compute gravisty and lin acc manually, since these sensors may not be available on the device
    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];
    private float[] rotation = new float[3];

    private Sensor mGyroscopeSensor;
    private boolean mGyroscopeSensorIsRegistered;

    // STEP SENSING
    private Sensor mStepSensor;
    private boolean mStepSensorIsRegistered;
    private int mPrevSteps = 0;
    private int mCurrentSteps = 0;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_COUNTER:
                if (!GENERATE_FAKE_STEPS) {
                    if (DEBUG_LOGS) Log.i(TAG, "New step count: " + Float.toString(event.values[0]));
                    mCurrentSteps = Math.round(event.values[0]);
                }
                break;
            case Sensor.TYPE_ACCELEROMETER:
                processAcceleration(event.values);
//                Log.v(TAG, "Accel: [" + String.format("%.2f", linear_acceleration[0]) + ", "
//                        + String.format("%.2f", linear_acceleration[1]) + ", "
//                        + String.format("%.2f", linear_acceleration[2]) + "]");
                break;
            case Sensor.TYPE_GYROSCOPE:
                rotation = event.values;
//                Log.v(TAG, "Gyros: [" + String.format("%.2f", event.values[0]) + ", "
//                        + String.format("%.2f", event.values[1]) + ", "
//                        + String.format("%.2f", event.values[2]) + "]");
                break;
        }

//        Log.v(TAG, "Accel: [" + String.format("%.2f", linear_acceleration[0]) + ", "
//                + String.format("%.2f", linear_acceleration[1]) + ", "
//                + String.format("%.2f", linear_acceleration[2]) + "]; "
//                + "Gyros: [" + String.format("%.2f", rotation[0]) + ", "
//                + String.format("%.2f", rotation[1]) + ", "
//                + String.format("%.2f", rotation[2]) + "]");
    }


    public void processAcceleration(float[] values) {
        // In this example, alpha is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the event delivery rate.
        final float alpha = 0.8f;

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * values[2];

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = values[0] - gravity[0];
        linear_acceleration[1] = values[1] - gravity[1];
        linear_acceleration[2] = values[2] - gravity[2];
    }








    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final int MSG_UPDATE_TIMER = 0;

        /* Handler to update the screen depending on the message */
        final Handler mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
//                Log.v(TAG, "Handler tick");
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


        private boolean mRegisteredTimeZoneReceiver = false;
        //        private boolean mLowBitAmbient;
        //        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Time mTime;
        private String mTimeStr;
        private int mHourInt, mMinuteInt;
        private int mLastAmbientHour;

        private Paint mTextDigitsPaintInteractive, mTextDigitsPaintAmbient;
        private float mTextDigitsHeight, mTextDigitsBaselineHeight, mTextDigitsRightMargin;
        private Paint mTextStepsPaintInteractive, mTextStepsPaintAmbient;
        private float mTextStepsHeight, mTextStepsBaselineHeight, mTextStepsRightMargin;
        private Typeface mTextTypeface;
        private final Rect textBounds = new Rect();
        private DecimalFormat mTestStepFormatter = new DecimalFormat("##,###");

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

        private BubbleManager bubbleManager;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheDingDongFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mTextTypeface = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    RALEWAY_TYPEFACE_PATH);


            /**
             * STEP SENSING
             */
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            mTextDigitsPaintInteractive = new Paint();
            mTextDigitsPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextDigitsPaintInteractive.setTypeface(mTextTypeface);
            mTextDigitsPaintInteractive.setAntiAlias(true);
            mTextDigitsPaintInteractive.setTextAlign(Paint.Align.RIGHT);

            mTextDigitsPaintAmbient = new Paint();
            mTextDigitsPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextDigitsPaintAmbient.setTypeface(mTextTypeface);
            mTextDigitsPaintAmbient.setAntiAlias(false);
            mTextDigitsPaintAmbient.setTextAlign(Paint.Align.RIGHT);

            mTextStepsPaintInteractive = new Paint();
            mTextStepsPaintInteractive.setColor(TEXT_STEPS_COLOR_INTERACTIVE);
            mTextStepsPaintInteractive.setTypeface(mTextTypeface);
            mTextStepsPaintInteractive.setAntiAlias(true);
            mTextStepsPaintInteractive.setTextAlign(Paint.Align.RIGHT);

            mTextStepsPaintAmbient = new Paint();
            mTextStepsPaintAmbient.setColor(TEXT_STEPS_COLOR_AMBIENT);
            mTextStepsPaintAmbient.setTypeface(mTextTypeface);
            mTextStepsPaintAmbient.setAntiAlias(false);
            mTextStepsPaintAmbient.setTextAlign(Paint.Align.RIGHT);

            bubbleManager = new BubbleManager(){};

            mTime  = new Time();


        }

        @Override
        public void onDestroy() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            unregisterTimeReceiver();
            unregisterStepSensor();
            unregisterAccelSensor();
            unregisterGyroscopeSensor();
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

            if (inAmbientMode) {
                if (timelyReset()) {
                    if (DEBUG_LOGS) Log.v(TAG, "Resetting watchface");
                    mPrevSteps = 0;
                    mCurrentSteps = 0;
                    bubbleManager.updateSteps(mCurrentSteps);
                }
            }

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            if (mAmbient) {
                unregisterTimeReceiver();
                unregisterStepSensor();
                unregisterAccelSensor();
                unregisterGyroscopeSensor();

                bubbleManager.resetMotion();
            } else {
                registerTimeReceiver();
                registerStepSensor();
                registerAccelSensor();
                registerGyroscopeSensor();

                updateStepCounts();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        private void updateStepCounts() {
            if (DEBUG_LOGS) Log.v(TAG, "mPrevSteps: " + mPrevSteps);
            if (DEBUG_LOGS) Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);

            if (GENERATE_FAKE_STEPS) {
                int fakeSteps = (int) (RANDOM_FAKE_STEPS * Math.random());
                if (DEBUG_LOGS) Log.v(TAG, "Generating fake steps: " + fakeSteps);
                mCurrentSteps += fakeSteps;
            }

            int stepInc = mCurrentSteps - mPrevSteps;
            mPrevSteps = mCurrentSteps;

            if (DEBUG_LOGS) Log.v(TAG, stepInc + " new steps!");

            if (mCurrentSteps > MAX_STEP_THRESHOLD) {
                if (DEBUG_LOGS) Log.v(TAG, "Resetting step counts");
                mPrevSteps = 0;
                mCurrentSteps = 0;
                if (DEBUG_LOGS) Log.v(TAG, "mPrevSteps: " + mPrevSteps);
                if (DEBUG_LOGS) Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);
            }

            bubbleManager.updateSteps(mCurrentSteps);

        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

            mTextDigitsHeight = TEXT_DIGITS_HEIGHT * mHeight;
            mTextDigitsBaselineHeight = TEXT_DIGITS_BASELINE_HEIGHT * mHeight;
            mTextDigitsRightMargin = TEXT_DIGITS_RIGHT_MARGIN * mWidth;
            mTextDigitsPaintInteractive.setTextSize(mTextDigitsHeight);
            mTextDigitsPaintAmbient.setTextSize(mTextDigitsHeight);

            mTextStepsHeight = TEXT_STEPS_HEIGHT * mHeight;
            mTextStepsBaselineHeight = TEXT_STEPS_BASELINE_HEIGHT * mHeight;
            mTextStepsRightMargin = TEXT_STEPS_RIGHT_MARGIN * mWidth;
            mTextStepsPaintInteractive.setTextSize(mTextStepsHeight);
            mTextStepsPaintAmbient.setTextSize(mTextStepsHeight);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();
            mHourInt = mTime.hour;
            mMinuteInt = mTime.minute;
            mTimeStr = (mHourInt % 12 == 0 ? 12 : mHourInt % 12) + ":" + String.format("%02d", mMinuteInt);

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT);

                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintAmbient);
                canvas.drawText(mTestStepFormatter.format(mCurrentSteps) + "#", mWidth - mTextStepsRightMargin,
                        mTextStepsBaselineHeight, mTextStepsPaintAmbient);


            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);

                // draw bubbles
                bubbleManager.update();
                bubbleManager.render(canvas);

                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintInteractive);
                canvas.drawText(mTestStepFormatter.format(mCurrentSteps) + "#", mWidth - mTextStepsRightMargin,
                        mTextStepsBaselineHeight, mTextStepsPaintInteractive);

            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerTimeReceiver();
                registerStepSensor();
                registerAccelSensor();
                registerGyroscopeSensor();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterTimeReceiver();
                unregisterStepSensor();
                unregisterAccelSensor();
                unregisterGyroscopeSensor();
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
            TheDingDongFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TheDingDongFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void registerStepSensor() {
            if (mStepSensorIsRegistered) {
                return;
            }
            mStepSensorIsRegistered = true;
            mSensorManager.registerListener(TheDingDongFaceService.this, mStepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        private void unregisterStepSensor() {
            if (!mStepSensorIsRegistered) {
                return;
            }
            mStepSensorIsRegistered = false;
            mSensorManager.unregisterListener(TheDingDongFaceService.this, mStepSensor);
        }

        private void registerAccelSensor() {
            if (mAccelSensorIsRegistered) return;
            mAccelSensorIsRegistered = true;
            mSensorManager.registerListener(TheDingDongFaceService.this, mAccelSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        private void unregisterAccelSensor() {
            if (!mAccelSensorIsRegistered) return;
            mAccelSensorIsRegistered = false;
            mSensorManager.unregisterListener(TheDingDongFaceService.this, mAccelSensor);
        }

        private void registerGyroscopeSensor() {
            if (mGyroscopeSensorIsRegistered) return;
            mGyroscopeSensorIsRegistered = true;
            mSensorManager.registerListener(TheDingDongFaceService.this, mGyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        private void unregisterGyroscopeSensor() {
            if (!mGyroscopeSensorIsRegistered) return;
            mGyroscopeSensorIsRegistered = false;
            mSensorManager.unregisterListener(TheDingDongFaceService.this, mGyroscopeSensor);
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

        // Checks if watchface should reset, like overnight
        boolean timelyReset() {
            boolean reset = false;
            if (mHourInt == RESET_HOUR && mLastAmbientHour == RESET_HOUR - 1) {
                reset = true;
            }
            mLastAmbientHour = mHourInt;
            return reset;
        }




        private class BubbleManager {

            private final static float ANIMATION_RATE = 0.25f;

            private final static int STEP_RATIO_XBIG    = 10000;
            private final static int STEP_RATIO_BIG     = 1000;     // a BIG bubble represents this many steps
            private final static int STEP_RATIO_MEDIUM  = 100;
            private final static int STEP_RATIO_SMALL   = 10;
            private final static int STEP_RATIO_XSMALL  = 1;

//            private final static int RADIUS_XBIG = 75;
//            private final static int RADIUS_BIG = 50;
//            private final static int RADIUS_MEDIUM = 35;
//            private final static int RADIUS_SMALL = 20;

            private final static int RADIUS_XBIG    = 70;
            private final static int RADIUS_BIG     = 45;
            private final static int RADIUS_MEDIUM  = 25;
            private final static int RADIUS_SMALL   = 15;
            private final static int RADIUS_XSMALL  = 5;

            private final static float WEIGHT_XBIG      = 5;
            private final static float WEIGHT_BIG       = 4;
            private final static float WEIGHT_MEDIUM    = 3;
            private final static float WEIGHT_SMALL     = 3;
            private final static float WEIGHT_XSMALL    = 3;

            private final int COLOR_XBIG    = Color.argb(204, 238, 42, 123);  // pink
            private final int COLOR_BIG     = Color.argb(204, 255, 167, 39);  // red
            private final int COLOR_MEDIUM  = Color.argb(204, 39, 170, 225);  // blue
            private final int COLOR_SMALL   = Color.argb(204, 141, 198, 63);  // yellow
            private final int COLOR_XSMALL  = Color.argb(204, 150, 150, 150);  // gray

            private final static float GAP_ANGLE_XBIG   = (float) (-0.75f * Math.PI);
            private final static float GAP_ANGLE_BIG    = (float) (-0.50f * Math.PI);
            private final static float GAP_ANGLE_MEDIUM = (float) (-1.00f * Math.PI);
            private final static float GAP_ANGLE_SMALL  = (float) (-0.75f * Math.PI);
            private final static float GAP_ANGLE_XSMALL = (float) ( 0.50f * Math.PI);

            private BubbleCollection bubblesXBig, bubblesBig, bubblesMedium,
                    bubblesSmall, bubblesXSmall;
            private Paint paintXBig, paintBig, paintMedium, paintSmall, paintXSmall;
            private int prevSteps, currentSteps;

            private int updateStep;  // @TODO add explanation here

            BubbleManager() {
                paintXBig = new Paint();
                paintXBig.setColor(COLOR_XBIG);
                paintXBig.setAntiAlias(true);
                paintXBig.setStyle(Paint.Style.FILL_AND_STROKE);

                paintBig = new Paint();
                paintBig.setColor(COLOR_BIG);
                paintBig.setAntiAlias(true);
                paintBig.setStyle(Paint.Style.FILL_AND_STROKE);

                paintMedium = new Paint();
                paintMedium.setColor(COLOR_MEDIUM);
                paintMedium.setAntiAlias(true);
                paintMedium.setStyle(Paint.Style.FILL_AND_STROKE);

                paintSmall = new Paint();
                paintSmall.setColor(COLOR_SMALL);
                paintSmall.setAntiAlias(true);
                paintSmall.setStyle(Paint.Style.FILL_AND_STROKE);

                paintXSmall = new Paint();
                paintXSmall.setColor(COLOR_XSMALL);
                paintXSmall.setAntiAlias(true);
                paintXSmall.setStyle(Paint.Style.FILL_AND_STROKE);

                bubblesXBig = new BubbleCollection(this, RADIUS_XBIG, WEIGHT_XBIG, GAP_ANGLE_XBIG, paintXBig);
                bubblesBig = new BubbleCollection(this, RADIUS_BIG, WEIGHT_BIG, GAP_ANGLE_BIG, paintBig);
                bubblesMedium = new BubbleCollection(this, RADIUS_MEDIUM, WEIGHT_MEDIUM, GAP_ANGLE_MEDIUM, paintMedium);
                bubblesSmall = new BubbleCollection(this, RADIUS_SMALL, WEIGHT_SMALL, GAP_ANGLE_SMALL, paintSmall);
                bubblesXSmall = new BubbleCollection(this, RADIUS_XSMALL, WEIGHT_XSMALL, GAP_ANGLE_XSMALL, paintXSmall);

                prevSteps = 0;
                currentSteps = 0;

                updateStep = 0;  // do not update
            }

            public void render(Canvas canvas) {
                bubblesXBig.render(canvas);
                bubblesBig.render(canvas);
                bubblesMedium.render(canvas);
                bubblesSmall.render(canvas);
                bubblesXSmall.render(canvas);
            }

            public void update() {

                switch (updateStep) {
                    case 1:
                        int stepInc = currentSteps - prevSteps;
                        currentSteps -= stepInc % STEP_RATIO_XSMALL;  // account for the remainder of the division
                        bubblesXSmall.add(stepInc / STEP_RATIO_XSMALL);
                        updateStep++;
                        break;
                    case 2:
                        boolean continueUpdating0 = bubblesXSmall.update();
                        if (!continueUpdating0) updateStep++;
                        break;
                    case 3:
                        int scaleRatioSXS = STEP_RATIO_SMALL / STEP_RATIO_XSMALL;
                        int xSmallBubbleCount = bubblesXSmall.bubbles.size();
                        int newSmallBubbleCount = xSmallBubbleCount / scaleRatioSXS;
                        bubblesXSmall.remove(newSmallBubbleCount * scaleRatioSXS);
                        bubblesSmall.add(newSmallBubbleCount);
                        updateStep++;
                        break;
                    case 4:
                        boolean continueUpdating1 = bubblesSmall.update();
                        if (!continueUpdating1) updateStep++;
                        break;
                    case 5:
                        int scaleRatioMS = STEP_RATIO_MEDIUM / STEP_RATIO_SMALL;
                        int smallBubbleCount = bubblesSmall.bubbles.size();
                        int newMediumBubbleCount = smallBubbleCount / scaleRatioMS;
                        bubblesSmall.remove(newMediumBubbleCount * scaleRatioMS);
                        bubblesMedium.add(newMediumBubbleCount);
                        updateStep++;
                        break;
                    case 6:
                        bubblesSmall.update();
                        bubblesMedium.update();
                        boolean continueUpdating3 =
                                bubblesSmall.needsUpdate || bubblesMedium.needsUpdate;
                        if (!continueUpdating3) updateStep++;
                        break;
                    case 7:
                        int scaleRatioBM = STEP_RATIO_BIG / STEP_RATIO_MEDIUM;
                        int mediumBubbleCount = bubblesMedium.bubbles.size();
                        int newBigBubbleCount = mediumBubbleCount / scaleRatioBM;
                        bubblesMedium.remove(newBigBubbleCount * scaleRatioBM);
                        bubblesBig.add(newBigBubbleCount);
                        updateStep++;
                        break;
                    case 8:
                        bubblesMedium.update();
                        bubblesBig.update();
                        boolean continueUpdating5 =
                                bubblesMedium.needsUpdate || bubblesBig.needsUpdate;
                        if (!continueUpdating5) updateStep++;
                        break;
                    case 9:
                        int scaleRatioBXB = STEP_RATIO_XBIG / STEP_RATIO_BIG;
                        int bigBubbleCount = bubblesBig.bubbles.size();
                        int newXBigBubbleCount = bigBubbleCount / scaleRatioBXB;
                        bubblesBig.remove(newXBigBubbleCount * scaleRatioBXB);
                        bubblesXBig.add(newXBigBubbleCount);
                        updateStep++;
                        break;
                    case 10:
                        bubblesBig.update();
                        bubblesXBig.update();
                        boolean continueUpdating6 =
                                bubblesBig.needsUpdate || bubblesXBig.needsUpdate;
                        if (!continueUpdating6) updateStep = 0;  // stop animation transition
                        break;


                    // WHAT WAS THIS FOR..?
                    case 11:
                        bubblesXSmall.remove(bubblesXSmall.bubbles.size());
                        bubblesSmall.remove(bubblesSmall.bubbles.size());
                        bubblesMedium.remove(bubblesMedium.bubbles.size());
                        bubblesBig.remove(bubblesBig.bubbles.size());
                        // Should add XBig bubbles here?
                        updateStep++;
                        break;
                    case 12:
                        bubblesXSmall.update();
                        bubblesSmall.update();
                        bubblesMedium.update();
                        bubblesBig.update();
                        boolean continueUpdating12 =
                                bubblesXSmall.needsUpdate || bubblesSmall.needsUpdate ||
                                bubblesMedium.needsUpdate || bubblesBig.needsUpdate;
                        if (!continueUpdating12) updateStep = 0;  // stop animation transition
                        break;


                    default:
                        break;
                }

                updatePositions();
            }

            public void updateSteps(int currentSteps_) {
                prevSteps = currentSteps;
                currentSteps = currentSteps_;
                updateStep = 1;  // trigger size update chain
            }

            public void updatePositions() {
                bubblesXBig.updatePositions();
                bubblesBig.updatePositions();
                bubblesMedium.updatePositions();
                bubblesSmall.updatePositions();
                bubblesXSmall.updatePositions();
            }

            public void resetMotion() {
                bubblesXBig.resetMotion();
                bubblesBig.resetMotion();
                bubblesMedium.resetMotion();
                bubblesSmall.resetMotion();
                bubblesXSmall.resetMotion();
            }
        }


        private class BubbleCollection {

            BubbleManager parent;
            List<Bubble> bubbles;
            List<Bubble> killQueue;
            Paint paint;
            float radius;
            float weight;
            float gapAngle;
            boolean needsUpdate;

            BubbleCollection(BubbleManager parent_, float radius_, float weight_, float gapAngle_, Paint paint_) {
                parent = parent_;
                radius = radius_;
                weight = weight_;
                gapAngle = gapAngle_;
                paint = paint_;
                bubbles = new ArrayList<>();
                killQueue = new ArrayList<>();
            }

            public void render(Canvas canvas) {
                for (Bubble bub : bubbles) {
                    bub.render(canvas);
                }
            }

            public boolean update() {
                needsUpdate = false;
                for (Bubble bub : bubbles) {
                    if (bub.needsSizeUpdate) needsUpdate |= bub.updateSize();
                }

                // Must kill remainder objects in independent loop to about iterator errors
                for (int i = killQueue.size() - 1; i >= 0; i--) {
                    bubbles.remove(killQueue.get(i));
                }

                return needsUpdate;
            }

            public void updatePositions() {
                for (Bubble bub : bubbles) {
                    bub.updatePosition();
                }
            }

            private void add(int count_) {
                for (int i = 0; i < count_; i++) {
                    Bubble b = new Bubble(this, radius, weight, gapAngle, paint);
                    b.grow();
                    bubbles.add(b);
                }
            }

            private void remove(int count_) {
                for (int i = 0; i < count_; i++) {
                    bubbles.get(i).kill();
                }
            }

            private void resetMotion() {
                for (Bubble bub : bubbles) {
                    bub.resetMotion();
                }
            }

        }

        private class Bubble {

            private static final boolean BOUNCE_FROM_BORDER     = true;
            private static final boolean DEPTH_BOUNCING         = true;

            // Originals
//            private static final float FRICTION                 = 0.95f;
//            private static final float PLANE_ACCEL_FACTOR       = 0.25f;
//            private static final float GRAVITY_FACTOR           = 1.00f;
//            private static final float ANCHOR_SPRING_FACTOR     = 0.03f;
//
//            private static final float DEPTH_ACCEL_FACTOR       = 0.50f;
//            private static final float DEPTH_SPRING_FACTOR      = 0.20f;
//
//            private static final float RANDOM_WEIGHT_FACTOR     = 0.20f;

            private static final float FRICTION                 = 0.95f; // 0 - 1, 0 is total friction
            private static final float PLANE_ACCEL_FACTOR       = 0.25f; // when level, how much shake?
            private static final float GRAVITY_FACTOR           = 1.00f; //
            private static final float ANCHOR_SPRING_FACTOR     = 0.01f; // how much spring from lock position

            private static final float DEPTH_ACCEL_FACTOR       = 0.40f;
            private static final float DEPTH_SPRING_FACTOR      = 0.15f;

            private static final float RANDOM_WEIGHT_FACTOR     = 0.50f; // how much variation between balls in the same category

            BubbleCollection parent;

            float anchorX, anchorY, baseRadius;
            float x, y;
//            boolean needsPositionUpdate = false;
            float velX, velY;
            float accX, accY;

            float radius, weight;
            float velR, accR;

            boolean needsSizeUpdate = false;
            float currentRadius = 0;
            float targetRadius = 0;
            boolean mustDie = false;

            float gapAngle;

            Paint paint;
            Path path;

            Bubble(BubbleCollection parent_, float radius_, float weight_, float gapAngle_, Paint paint_) {
                parent = parent_;
                anchorX = (float) (mWidth * Math.random());
                anchorY = (float) (mHeight * Math.random());
                x = mCenterX;
                y = mCenterY;
                radius = radius_;
                weight = weight_ + (float) (weight_ * RANDOM_WEIGHT_FACTOR * Math.random());  // slight random weight variation
                paint = paint_;
                velX = velY = accX = accY = 0;
                velR = accR = 0;

                gapAngle = gapAngle_;

                path = new Path();
                path.addCircle(0, 0, 1.0f, Path.Direction.CW);
                path.close();
                path.addCircle(0.1f * (float) Math.cos(gapAngle), 0.1f * (float) Math.sin(gapAngle), 0.8f, Path.Direction.CW);
                path.close();
                path.setFillType(Path.FillType.EVEN_ODD);
            }

            public void render(Canvas canvas) {
                canvas.save();
                canvas.translate(x, y);
                canvas.scale(currentRadius, currentRadius);
                canvas.drawPath(path, paint);
                canvas.restore();
            }

            public boolean updateSize() {
                currentRadius += (targetRadius - currentRadius) * BubbleManager.ANIMATION_RATE;
                if (Math.abs(targetRadius - currentRadius) < 1) {
                    needsSizeUpdate = false;
                    if (mustDie) {
                        parent.killQueue.add(this);
                    }
                }
                return needsSizeUpdate;
            }

            public void updatePosition() {

                accX = (PLANE_ACCEL_FACTOR * -linear_acceleration[0] * linear_acceleration[0]
                        - GRAVITY_FACTOR * gravity[0] + ANCHOR_SPRING_FACTOR * (anchorX - x)) / weight;
                accY = (PLANE_ACCEL_FACTOR *  linear_acceleration[1] * linear_acceleration[1]
                        + GRAVITY_FACTOR * gravity[1] + ANCHOR_SPRING_FACTOR * (anchorY - y)) / weight;

                velX += accX;
                velY += accY;
                velX *= FRICTION;
                velY *= FRICTION;
                x += velX;
                y += velY;

                if (DEPTH_BOUNCING && !needsSizeUpdate) {
                    accR = (DEPTH_ACCEL_FACTOR * linear_acceleration[2] + DEPTH_SPRING_FACTOR * (radius - currentRadius)) / weight;
                    velR += accR;
                    velR *= FRICTION;
                    currentRadius += velR;
                }

                if (BOUNCE_FROM_BORDER) {
                    if (x + radius > mWidth) {
                        x = 2 * mWidth - 2 * radius - x;
                        velX *= -1;
                    } else if (x < radius) {
                        x = 2 * radius - x;
                        velX *= -1;
                    }

                    if (y + radius > mHeight) {
                        y = 2 * mHeight - 2 * radius - y;
                        velY *= -1;
                    } else if (y < radius) {
                        y = 2 * radius - y;
                        velY *= -1;
                    }

                } else {
                    if (x > mWidth) {
                        x = mWidth - (x - mWidth);
                        velX *= -1;
                    } else if (x < 0) {
                        x = -x;
                        velX *= -1;
                    }

                    if (y > mHeight) {
                        y = mHeight - (y - mHeight);
                        velY *= -1;
                    } else if (y < 0) {
                        y = -y;
                        velY *= -1;
                    }
                }
            }

            public void grow() {
                targetRadius = radius;
                needsSizeUpdate = true;
            }

            public void kill() {
                targetRadius = 0;
                anchorX = mCenterX;
                anchorY = mCenterY;
                mustDie = true;
                needsSizeUpdate = true;
            }

            public void resetMotion() {
                velX = velY = accX = accY = 0;
            }

        }

    }

}
