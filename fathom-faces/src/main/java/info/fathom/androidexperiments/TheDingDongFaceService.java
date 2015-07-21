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

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class TheDingDongFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "TheDingDongFaceService";

    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

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
    private static final boolean GENERATE_FAKE_STEPS = false;
    private static final int RANDOM_FAKE_STEPS = 500;
    private static final int MAX_STEP_THRESHOLD = 50000;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_COUNTER:
                if (!GENERATE_FAKE_STEPS) {
                    Log.i(TAG, "New step count: " + Float.toString(event.values[0]));
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

        private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
        private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

        private static final int   TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
        private static final int   TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_DIGITS_HEIGHT = 0.20f;  // as a factor of screen height

        private static final int   TEXT_STEPS_COLOR_INTERACTIVE = Color.WHITE;
        private static final int   TEXT_STEPS_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_STEPS_HEIGHT = 0.05f;  // as a factor of screen height

        private boolean mRegisteredTimeZoneReceiver = false;
        //        private boolean mLowBitAmbient;
        //        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Time mTime;

        private Paint mTextDigitsPaintInteractive, mTextDigitsPaintAmbient;
        private Paint mTextStepsPaintInteractive, mTextStepsPaintAmbient;
        private float mTextDigitsHeight;
        private float mTextStepsHeight;
        private final Rect textBounds = new Rect();

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

        private BubbleManager bubbleManager;
//        private static final float FRICTION = 0.97f;
//        private static final float PLANE_ACCEL_FACTOR = 0.25f;

        private Typeface RALEWAY_REGULAR_TYPEFACE;




        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheDingDongFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Context context = getApplicationContext();
//            Log.v(TAG, context.toString());
            AssetManager mgr = context.getAssets();
//            Log.v(TAG, mgr.toString());
            RALEWAY_REGULAR_TYPEFACE = Typeface.createFromAsset(mgr, "fonts/raleway-regular.ttf");
//            Log.v(TAG, RALEWAY_REGULAR_TYPEFACE.toString());

            /**
             * STEP SENSING
             */
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            mTextDigitsPaintInteractive = new Paint();
            mTextDigitsPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
//            mTextDigitsPaintInteractive.setTypeface(NORMAL_TYPEFACE);
            mTextDigitsPaintInteractive.setTypeface(RALEWAY_REGULAR_TYPEFACE);
            mTextDigitsPaintInteractive.setAntiAlias(true);

            mTextDigitsPaintAmbient = new Paint();
            mTextDigitsPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
//            mTextDigitsPaintAmbient.setTypeface(NORMAL_TYPEFACE);
            mTextDigitsPaintAmbient.setTypeface(RALEWAY_REGULAR_TYPEFACE);
            mTextDigitsPaintAmbient.setAntiAlias(false);

            mTextStepsPaintInteractive = new Paint();
            mTextStepsPaintInteractive.setColor(TEXT_STEPS_COLOR_INTERACTIVE);
            mTextStepsPaintInteractive.setTypeface(BOLD_TYPEFACE);
            mTextStepsPaintInteractive.setAntiAlias(true);
//            mTextStepsPaintInteractive.setTextAlign(Paint.Align.CENTER);
            mTextStepsPaintInteractive.setTextAlign(Paint.Align.RIGHT);

            mTextStepsPaintAmbient = new Paint();
            mTextStepsPaintAmbient.setColor(TEXT_STEPS_COLOR_AMBIENT);
            mTextStepsPaintAmbient.setTypeface(BOLD_TYPEFACE);
            mTextStepsPaintAmbient.setAntiAlias(false);
//            mTextStepsPaintAmbient.setTextAlign(Paint.Align.CENTER);
            mTextStepsPaintAmbient.setTextAlign(Paint.Align.RIGHT);

            bubbleManager = new BubbleManager(){};

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
            Log.v(TAG, "mPrevSteps: " + mPrevSteps);
            Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);

            if (GENERATE_FAKE_STEPS) {
                int fakeSteps = (int) (RANDOM_FAKE_STEPS * Math.random());
                Log.v(TAG, "Generating fake steps: " + fakeSteps);
                mCurrentSteps += fakeSteps;
            }

            int stepInc = mCurrentSteps - mPrevSteps;
            mPrevSteps = mCurrentSteps;

            Log.v(TAG, stepInc + " new steps!");

            if (mCurrentSteps > MAX_STEP_THRESHOLD) {
                Log.v(TAG, "Resetting step counts");
                mPrevSteps = 0;
                mCurrentSteps = 0;
                Log.v(TAG, "mPrevSteps: " + mPrevSteps);
                Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);
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
            mTextDigitsPaintInteractive.setTextSize(mTextDigitsHeight);
            mTextDigitsPaintAmbient.setTextSize(mTextDigitsHeight);

            mTextStepsHeight = TEXT_STEPS_HEIGHT * mHeight;
            mTextStepsPaintInteractive.setTextSize(mTextStepsHeight);
            mTextStepsPaintAmbient.setTextSize(mTextStepsHeight);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

            final String hours = Integer.toString(mTime.hour % 12 == 0 ? 12 : mTime.hour % 12);
            final String minutes = String.format("%02d", mTime.minute);

            // Start drawing watch elements
//            canvas.save();

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT); // background

//                mTextDigitsPaintAmbient.setTextAlign(Paint.Align.RIGHT);
//                drawTextVerticallyCentered(canvas, mTextDigitsPaintAmbient, hours, mCenterX - 20, 0.5f * mCenterY);  // @TODO: be screen programmatic here
//                mTextDigitsPaintAmbient.setTextAlign(Paint.Align.LEFT);
//                drawTextVerticallyCentered(canvas, mTextDigitsPaintAmbient, minutes, mCenterX + 20, 0.5f * mCenterY);
//                mTextDigitsPaintAmbient.setTextAlign(Paint.Align.CENTER);
//                drawTextVerticallyCentered(canvas, mTextDigitsPaintAmbient, ":", mCenterX, 0.5f * mCenterY);

                mTextDigitsPaintAmbient.setTextAlign(Paint.Align.RIGHT);
                drawTextVerticallyCentered(canvas, mTextDigitsPaintAmbient, hours, 1.35f * mCenterX - 10, 0.35f * mCenterY);  // @TODO: be screen programmatic here
                mTextDigitsPaintAmbient.setTextAlign(Paint.Align.LEFT);
                drawTextVerticallyCentered(canvas, mTextDigitsPaintAmbient, minutes, 1.35f * mCenterX + 10, 0.35f * mCenterY);  // @TODO: be screen programmatic here
                mTextDigitsPaintAmbient.setTextAlign(Paint.Align.CENTER);
                drawTextVerticallyCentered(canvas, mTextDigitsPaintAmbient, ":", 1.35f * mCenterX, 0.35f * mCenterY);

//                canvas.drawText(mCurrentSteps + " steps", mCenterX, mCenterY, mTextStepsPaintAmbient);
                canvas.drawText("" + mCurrentSteps, 1.35f * mCenterX, 0.65f * mCenterY, mTextStepsPaintAmbient);

            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);

                // draw bubbles
                bubbleManager.update();
                bubbleManager.render(canvas);

//                mTextDigitsPaintInteractive.setTextAlign(Paint.Align.RIGHT);
//                drawTextVerticallyCentered(canvas, mTextDigitsPaintInteractive, hours, mCenterX - 20, 0.5f * mCenterY);
//                mTextDigitsPaintInteractive.setTextAlign(Paint.Align.LEFT);
//                drawTextVerticallyCentered(canvas, mTextDigitsPaintInteractive, minutes, mCenterX + 20, 0.5f * mCenterY);
//                mTextDigitsPaintInteractive.setTextAlign(Paint.Align.CENTER);
//                drawTextVerticallyCentered(canvas, mTextDigitsPaintInteractive, ":", mCenterX, 0.5f * mCenterY);

                mTextDigitsPaintInteractive.setTextAlign(Paint.Align.RIGHT);
                drawTextVerticallyCentered(canvas, mTextDigitsPaintInteractive, hours, 1.35f * mCenterX - 10, 0.35f * mCenterY);  // @TODO: be screen programmatic here
                mTextDigitsPaintInteractive.setTextAlign(Paint.Align.LEFT);
                drawTextVerticallyCentered(canvas, mTextDigitsPaintInteractive, minutes, 1.35f * mCenterX + 10, 0.35f * mCenterY);  // @TODO: be screen programmatic here
                mTextDigitsPaintInteractive.setTextAlign(Paint.Align.CENTER);
                drawTextVerticallyCentered(canvas, mTextDigitsPaintInteractive, ":", 1.35f * mCenterX, 0.35f * mCenterY);

//                canvas.drawText(mCurrentSteps + " steps", mCenterX, mCenterY, mTextStepsPaintInteractive);
                canvas.drawText("" + mCurrentSteps, 1.35f * mCenterX, 0.65f * mCenterY, mTextStepsPaintInteractive);

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









        // http://stackoverflow.com/a/24969713/1934487
        private void drawTextVerticallyCentered(Canvas canvas, Paint paint, String text, float cx, float cy) {
            paint.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, cx, cy - textBounds.exactCenterY(), paint);
        }


        private class BubbleManager {

            private final static float ANIMATION_RATE = 0.25f;

            private final static int STEP_RATIO_XBIG    = 10000;
            private final static int STEP_RATIO_BIG     = 1000;     // a BIG bubble represents this many steps
            private final static int STEP_RATIO_MEDIUM  = 100;
            private final static int STEP_RATIO_SMALL   = 10;


//            private final static int RADIUS_XBIG = 75;
//            private final static int RADIUS_BIG = 50;
//            private final static int RADIUS_MEDIUM = 35;
//            private final static int RADIUS_SMALL = 20;

            private final static int RADIUS_XBIG = 70;
            private final static int RADIUS_BIG = 45;
            private final static int RADIUS_MEDIUM = 25;
            private final static int RADIUS_SMALL = 15;

            private final static float WEIGHT_XBIG      = 5;
            private final static float WEIGHT_BIG       = 4;
            private final static float WEIGHT_MEDIUM    = 3;
            private final static float WEIGHT_SMALL     = 3;

            private final int COLOR_XBIG    = Color.argb(204, 238, 42, 123);  // pink
            private final int COLOR_BIG     = Color.argb(204, 146, 39, 143);  // red
            private final int COLOR_MEDIUM  = Color.argb(204, 39, 170, 225);  // blue
            private final int COLOR_SMALL   = Color.argb(204, 141, 198, 63);  // yellow

            private final static float GAP_ANGLE_XBIG   = (float) (-0.75f * Math.PI);
            private final static float GAP_ANGLE_BIG    = (float) (-0.50f * Math.PI);
            private final static float GAP_ANGLE_MEDIUM = (float) (-1.00f * Math.PI);
            private final static float GAP_ANGLE_SMALL  = (float) (-0.75f * Math.PI);

            private BubbleCollection bubblesXBig, bubblesBig, bubblesMedium, bubblesSmall;
            private Paint paintXBig, paintBig, paintMedium, paintSmall;
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

                bubblesXBig = new BubbleCollection(this, RADIUS_XBIG, WEIGHT_XBIG, GAP_ANGLE_XBIG, paintXBig);
                bubblesBig = new BubbleCollection(this, RADIUS_BIG, WEIGHT_BIG, GAP_ANGLE_BIG, paintBig);
                bubblesMedium = new BubbleCollection(this, RADIUS_MEDIUM, WEIGHT_MEDIUM, GAP_ANGLE_MEDIUM, paintMedium);
                bubblesSmall = new BubbleCollection(this, RADIUS_SMALL, WEIGHT_SMALL, GAP_ANGLE_SMALL, paintSmall);

                prevSteps = 0;
                currentSteps = 0;

                updateStep = 0;  // do not update
            }

            public void render(Canvas canvas) {
                bubblesXBig.render(canvas);
                bubblesBig.render(canvas);
                bubblesMedium.render(canvas);
                bubblesSmall.render(canvas);
            }

            public void update() {

                switch (updateStep) {
                    case 1:
                        int stepInc = currentSteps - prevSteps;
                        currentSteps -= stepInc % STEP_RATIO_SMALL;  // account for the remainder of the division
                        bubblesSmall.add(stepInc / STEP_RATIO_SMALL);
                        updateStep++;
                        break;
                    case 2:
                        boolean continueUpdating1 = bubblesSmall.update();
                        if (!continueUpdating1) updateStep++;
                        break;
                    case 3:
                        int scaleRatioMS = STEP_RATIO_MEDIUM / STEP_RATIO_SMALL;
                        int smallBubbleCount = bubblesSmall.bubbles.size();
                        int newMediumBubbleCount = smallBubbleCount / scaleRatioMS;
                        bubblesSmall.remove(newMediumBubbleCount * scaleRatioMS);
                        bubblesMedium.add(newMediumBubbleCount);
                        updateStep++;
                        break;
                    case 4:
                        bubblesSmall.update();
                        bubblesMedium.update();
                        boolean continueUpdating3 =
                                bubblesSmall.needsUpdate || bubblesMedium.needsUpdate;
                        if (!continueUpdating3) updateStep++;
                        break;
                    case 5:
                        int scaleRatioBM = STEP_RATIO_BIG / STEP_RATIO_MEDIUM;
                        int mediumBubbleCount = bubblesMedium.bubbles.size();
                        int newBigBubbleCount = mediumBubbleCount / scaleRatioBM;
                        bubblesMedium.remove(newBigBubbleCount * scaleRatioBM);
                        bubblesBig.add(newBigBubbleCount);
                        updateStep++;
                        break;
                    case 6:
                        bubblesMedium.update();
                        bubblesBig.update();
                        boolean continueUpdating5 =
                                bubblesMedium.needsUpdate || bubblesBig.needsUpdate;
                        if (!continueUpdating5) updateStep++;
                        break;
                    case 7:
                        int scaleRatioBXB = STEP_RATIO_XBIG / STEP_RATIO_BIG;
                        int bigBubbleCount = bubblesBig.bubbles.size();
                        int newXBigBubbleCount = bigBubbleCount / scaleRatioBXB;
                        bubblesBig.remove(newXBigBubbleCount * scaleRatioBXB);
                        bubblesXBig.add(newXBigBubbleCount);
                        updateStep++;
                        break;
                    case 8:
                        bubblesBig.update();
                        bubblesXBig.update();
                        boolean continueUpdating6 =
                                bubblesBig.needsUpdate || bubblesXBig.needsUpdate;
                        if (!continueUpdating6) updateStep = 0;  // stop animation transition
                        break;


                    case 11:
                        bubblesSmall.remove(bubblesSmall.bubbles.size());
                        bubblesMedium.remove(bubblesMedium.bubbles.size());
                        bubblesBig.remove(bubblesBig.bubbles.size());
                        updateStep++;
                        break;
                    case 12:
                        bubblesSmall.update();
                        bubblesMedium.update();
                        bubblesBig.update();
                        boolean continueUpdating12 =
                                bubblesSmall.needsUpdate || bubblesMedium.needsUpdate || bubblesBig.needsUpdate;
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
            }

            public void resetMotion() {
                bubblesXBig.resetMotion();
                bubblesBig.resetMotion();
                bubblesMedium.resetMotion();
                bubblesSmall.resetMotion();
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
            private static final float ANCHOR_SPRING_FACTOR     = 0.02f; // how much spring from lock position

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
//                x = anchorX;
//                y = anchorY;
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
//                canvas.drawCircle(x, y, currentRadius, paint);
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
