package info.fathom.androidexperiments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.WindowInsets;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class TheDingDongFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "TheDingDongFaceService";

    private static final float TAU = (float) (2 * Math.PI);

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

    private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
    private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

    private static final String   RALEWAY_TYPEFACE_PATH = "fonts/raleway-regular-enhanced.ttf";
    private static final int     TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int     TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
    private static final float   TEXT_DIGITS_HEIGHT = 0.20f;                                        // as a factor of screen height
    private static final float   TEXT_DIGITS_BASELINE_HEIGHT = 0.43f;                               // as a factor of screen height
    private static final float   TEXT_DIGITS_RIGHT_MARGIN = 0.08f;                                  // as a factor of screen width

    private static final int     TEXT_STEPS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int     TEXT_STEPS_COLOR_AMBIENT = Color.WHITE;
    private static final float   TEXT_STEPS_HEIGHT = 0.10f;                                         // as a factor of screen height
    private static final float   TEXT_STEPS_BASELINE_HEIGHT = TEXT_DIGITS_BASELINE_HEIGHT + 0.15f;  // as a factor of screen height
    private static final float   TEXT_STEPS_RIGHT_MARGIN = 0.07f;                                   // as a factor of screen width
    private static final float   TEXT_STEPS_ROLL_EASE_SPEED = 0.45f;                                // 0...1

    private static final int     RESET_HOUR = 4;                                                    // at which hour will watch face reset [0...23], -1 to deactivate


    // DEBUG
    private static final boolean DEBUG_LOGS = true;
    private static final boolean GENERATE_FAKE_STEPS = false;
    private static final int     RANDOM_FAKE_STEPS = 500;
    private static final int     MAX_STEP_THRESHOLD = 21000;



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


        //        private boolean mLowBitAmbient;
        //        private boolean mBurnInProtection;
        private boolean mAmbient, mScreenOn;

        private Time mTime;
        private String mTimeStr;
        private int mHourInt, mMinuteInt;
        private int mLastAmbientHour;

        private Paint mTextDigitsPaintInteractive, mTextDigitsPaintAmbient;
        private float mTextDigitsHeight, mTextDigitsBaselineHeight, mTextDigitsRightMargin;
        private Paint mTextStepsPaintInteractive, mTextStepsPaintAmbient;
        private float mTextStepsHeight, mTextStepsBaselineHeight, mTextStepsRightMargin;
        private Typeface mTextTypeface;
        private DecimalFormat mTestStepFormatter = new DecimalFormat("##,###");
        private final Rect textBounds = new Rect();

        private int mPrevSteps = 0;
        private int mCurrentSteps = 0;
        private float mStepCountDisplay;  // , mStepCountDisplayTarget;
        private boolean showSplash10KScreen = false;

        private Paint mBubbleTextPaint;

        private int mWidth;
        private int mHeight;
        private float mCenterX, mCenterY;
        private boolean mIsRound;
        private float mRadius;

        private BubbleManager bubbleManager;
        private Splash10KScreen splash10KScreen;


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

            mBubbleTextPaint = new Paint();
            mBubbleTextPaint.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mBubbleTextPaint.setTypeface(mTextTypeface);
            mBubbleTextPaint.setAntiAlias(true);
            mBubbleTextPaint.setTextAlign(Paint.Align.CENTER);

            bubbleManager = new BubbleManager();
            splash10KScreen = new Splash10KScreen();

            mTime  = new Time();

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = new SensorWrapper("Accelerometer", Sensor.TYPE_ACCELEROMETER, 3,
                    TheDingDongFaceService.this, mSensorManager);
            mSensorAccelerometer.register();
            mSensorStep = new SensorWrapper("Steps", Sensor.TYPE_STEP_COUNTER, 1,
                    TheDingDongFaceService.this, mSensorManager);
            mSensorStep.register();

            registerScreenReceiver();
        }

        @Override
        public void onDestroy() {
            if (DEBUG_LOGS) Log.v(TAG, "onDestroy()");
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            unregisterTimeZoneReceiver();
            unregisterScreenReceiver();
            mSensorStep.unregister();
            mSensorAccelerometer.unregister();
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            if (DEBUG_LOGS) Log.v(TAG, "onAmbientModeChanged: " + inAmbientMode);
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.v(TAG, "onVisibilityChanged: " + visible);
            super.onVisibilityChanged(visible);

            if (visible) {
                mSensorStep.register();
                mSensorAccelerometer.register();

            } else {
                mSensorStep.unregister();
                mSensorAccelerometer.unregister();
            }

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }


        private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DEBUG_LOGS) Log.v(TAG, "Received intent: " + action);
                if (action.equals(Intent.ACTION_SCREEN_ON)) {
                    if (DEBUG_LOGS) Log.v(TAG, "Screen ON");
                    mScreenOn = true;
                    onScreenChange(true);
                } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    if (DEBUG_LOGS) Log.v(TAG, "Screen OFF");
                    mScreenOn = false;
                    onScreenChange(false);
                }
            }
        };

        private void registerScreenReceiver() {
            if (DEBUG_LOGS) Log.v(TAG, "ScreenReceiver registered");
            TheDingDongFaceService.this.registerReceiver(mScreenReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_ON));
            TheDingDongFaceService.this.registerReceiver(mScreenReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }

        private void unregisterScreenReceiver() {
            TheDingDongFaceService.this.unregisterReceiver(mScreenReceiver);
        }

        /**
         * This is a dedicated method to account for screen changes, which will happen when
         * the watch goes to ambient mode (if active), or if visibility changes (if ambient
         * mode is off).
         * This method should be called from a Broadcast receiver targeting Intent.ACTION_SCREEN_ON/OFF
         * @param turnedOn
         */
        public void onScreenChange(boolean turnedOn) {
            if (DEBUG_LOGS) Log.v(TAG, "onScreenChange: " + turnedOn);

            if (turnedOn) {
                registerTimeZoneReceiver();
                mSensorStep.register();
                mSensorAccelerometer.register();

                updateStepCounts();

            } else {
                bubbleManager.newGlance();

                if (timelyReset()) {
                    if (DEBUG_LOGS) Log.v(TAG, "Resetting watchface");
                    mPrevSteps = 0;
                    mCurrentSteps = 0;
                    bubbleManager.updateSteps(mCurrentSteps);
                }

                unregisterTimeZoneReceiver();
                mSensorStep.unregister();
                mSensorAccelerometer.unregister();

                bubbleManager.resetMotion();
            }

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (DEBUG_LOGS) Log.v(TAG, "onSurfaceChanged: " + format + " " + width + " " + height);
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;
            mCenterX = 0.50f * mWidth;
            mCenterY = 0.50f * mHeight;
            mRadius = 0.50f * mWidth;

            splash10KScreen.initialize();

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
        public void onApplyWindowInsets(WindowInsets insets) {
            if (DEBUG_LOGS) Log.d(TAG, "onApplyWindowInsets");
            super.onApplyWindowInsets(insets);

            mIsRound = insets.isRound();
            if (DEBUG_LOGS) Log.v(TAG, "mIsRound? " + mIsRound);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
//            if (DEBUG_LOGS) Log.v(TAG, "Drawing canvas");

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

                if (mCurrentSteps != mStepCountDisplay) {
//                    if (DEBUG_LOGS) Log.v(TAG, "Updating step counter: " + mStepCountDisplay + " -> " + mCurrentSteps);
                    float diff = mCurrentSteps - mStepCountDisplay;
                    if (Math.abs(diff) > 1) {
                        mStepCountDisplay += TEXT_STEPS_ROLL_EASE_SPEED * diff;
                    } else {
                        mStepCountDisplay = mCurrentSteps;
                    }
                }

                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintInteractive);
                canvas.drawText(mTestStepFormatter.format(mStepCountDisplay) + "#", mWidth - mTextStepsRightMargin,
                        mTextStepsBaselineHeight, mTextStepsPaintInteractive);

                if (showSplash10KScreen) {
                    splash10KScreen.render(canvas);
                }

            }
        }


        private boolean mRegisteredTimeZoneReceiver = false;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        private void registerTimeZoneReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            TheDingDongFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TheDingDongFaceService.this.unregisterReceiver(mTimeZoneReceiver);
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

        // Checks if watchface should reset, like overnight
        private boolean timelyReset() {
            boolean reset = false;
            if (mHourInt == RESET_HOUR && mLastAmbientHour == RESET_HOUR - 1) {
                reset = true;
            }
            mLastAmbientHour = mHourInt;
            return reset;
        }

        private void updateStepCounts() {
            if (DEBUG_LOGS) Log.v(TAG, "mPrevSteps: " + mPrevSteps);
            if (DEBUG_LOGS) Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);

            mPrevSteps = mCurrentSteps;

            if (GENERATE_FAKE_STEPS) {
                int fakeSteps = (int) (RANDOM_FAKE_STEPS * Math.random());
                if (DEBUG_LOGS) Log.v(TAG, "Generating fake steps: " + fakeSteps);
                mCurrentSteps += fakeSteps;
            } else {
                mCurrentSteps = (int) mSensorStep.values[0];  // read from the sensor
            }

            int stepInc = mCurrentSteps - mPrevSteps;
//            mPrevSteps = mCurrentSteps;

            if (DEBUG_LOGS) Log.v(TAG, stepInc + " new steps!");

            if (mCurrentSteps > MAX_STEP_THRESHOLD) {
                if (DEBUG_LOGS) Log.v(TAG, "Resetting step counts");
//                mDayBufferedSteps += mCurrentSteps;  // @TODO Store previous day steps somwhere and account for them
                mPrevSteps = 0;
                mCurrentSteps = 0;
                if (DEBUG_LOGS) Log.v(TAG, "mPrevSteps: " + mPrevSteps);
                if (DEBUG_LOGS) Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);
            }

            showSplash10KScreen = mCurrentSteps > 10000 && mPrevSteps < 10000;
            if (DEBUG_LOGS && showSplash10KScreen) Log.v(TAG, "REACHED 10K!");

            bubbleManager.updateSteps(mCurrentSteps);

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
            private final static int STEP_RATIO_XSMALL  = 1;

//            private final static int RADIUS_XBIG = 75;
//            private final static int RADIUS_BIG = 50;
//            private final static int RADIUS_MEDIUM = 35;
//            private final static int RADIUS_SMALL = 20;

            private final static int RADIUS_XBIG    = 70;
            private final static int RADIUS_BIG     = 45;
            private final static int RADIUS_MEDIUM  = 25;
            private final static int RADIUS_SMALL   = 15;
            private final static int RADIUS_XSMALL  = 7;

            private final static float WEIGHT_XBIG      = 3;
            private final static float WEIGHT_BIG       = 3;
            private final static float WEIGHT_MEDIUM    = 3;
            private final static float WEIGHT_SMALL     = 2;
            private final static float WEIGHT_XSMALL    = 2;

            private final int COLOR_XBIG    = Color.argb(204, 238, 42, 123);
            private final int COLOR_BIG     = Color.argb(204, 255, 167, 39);
            private final int COLOR_MEDIUM  = Color.argb(204, 146, 39, 143);
            private final int COLOR_SMALL   = Color.argb(204, 39, 170, 225);
            private final int COLOR_XSMALL  = Color.argb(204, 141, 198, 63);

            private final static float GAP_ANGLE_XBIG   = (float) (-0.375f * TAU);
            private final static float GAP_ANGLE_BIG    = (float) (-0.250f * TAU);
            private final static float GAP_ANGLE_MEDIUM = (float) (-0.500f * TAU);
            private final static float GAP_ANGLE_SMALL  = (float) (-0.375f * TAU);
            private final static float GAP_ANGLE_XSMALL = (float) ( 0.500f * TAU);

            private BubbleCollection bubblesXBig, bubblesBig, bubblesMedium,
                    bubblesSmall, bubblesXSmall;
            private Paint paintXBig, paintBig, paintMedium, paintSmall, paintXSmall;
            private int prevSteps, currentSteps;

            private int updateStep;  // @TODO add explanation here

            List<Bubble> toDefeatureBuffer = new ArrayList<>();

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

                bubblesXBig = new BubbleCollection(this, STEP_RATIO_XBIG, RADIUS_XBIG, WEIGHT_XBIG, GAP_ANGLE_XBIG, paintXBig);
                bubblesBig = new BubbleCollection(this, STEP_RATIO_BIG, RADIUS_BIG, WEIGHT_BIG, GAP_ANGLE_BIG, paintBig);
                bubblesMedium = new BubbleCollection(this, STEP_RATIO_MEDIUM, RADIUS_MEDIUM, WEIGHT_MEDIUM, GAP_ANGLE_MEDIUM, paintMedium);
                bubblesSmall = new BubbleCollection(this, STEP_RATIO_SMALL, RADIUS_SMALL, WEIGHT_SMALL, GAP_ANGLE_SMALL, paintSmall);
                bubblesXSmall = new BubbleCollection(this, STEP_RATIO_XSMALL, RADIUS_XSMALL, WEIGHT_XSMALL, GAP_ANGLE_XSMALL, paintXSmall);

                prevSteps = 0;
                currentSteps = 0;

                updateStep = 0;  // do not update
            }

            public void render(Canvas canvas) {
                bubblesXSmall.render(canvas);
                bubblesSmall.render(canvas);
                bubblesMedium.render(canvas);
                bubblesBig.render(canvas);
                bubblesXBig.render(canvas);
            }

            public void update() {

                switch (updateStep) {
                    // @TODO verify if bubble count is working on the long run
                    case 1:
                        bubblesXSmall.add((currentSteps % STEP_RATIO_SMALL) - bubblesXSmall.bubbles.size(), false, 0);
                        int stepInc = currentSteps - prevSteps;
                        currentSteps -= stepInc % STEP_RATIO_SMALL;  // account for the remainder of the division
                        bubblesSmall.add(stepInc / STEP_RATIO_SMALL, false, 0);
                        updateStep++;
                        break;
                    case 2:
                        boolean continueUpdating1 = bubblesXSmall.update() && bubblesSmall.update();
                        if (!continueUpdating1) updateStep++;
                        break;
                    case 3:
                        int scaleRatioMS = STEP_RATIO_MEDIUM / STEP_RATIO_SMALL;
                        int smallBubbleCount = bubblesSmall.bubbles.size();
                        int newMediumBubbleCount = smallBubbleCount / scaleRatioMS;
                        bubblesSmall.remove(newMediumBubbleCount * scaleRatioMS);
                        bubblesMedium.add(newMediumBubbleCount, false, 0);
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
                        bubblesBig.add(newBigBubbleCount, mCurrentSteps < STEP_RATIO_XBIG, 1);  // stop featuring after reaching 10k
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
                        bubblesXBig.add(newXBigBubbleCount, true, 3);
                        updateStep++;
                        break;
                    case 8:
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

            public void newGlance() {
                for (Bubble bubble : toDefeatureBuffer) {
                    if (--bubble.featuredGlanceDuration <= 0) {
                        bubble.isFeatured = false;
                    }
                }
//                toDefeatureBuffer.clear();
                for (int i = toDefeatureBuffer.size() - 1; i >= 0; i--) {
                    if (!toDefeatureBuffer.get(i).isFeatured) toDefeatureBuffer.remove(i);
                }
            }
        }


        private class BubbleCollection {

            BubbleManager parent;
            List<Bubble> bubbles;
            List<Bubble> killQueue;
            int stepSize;
            float radius;
            float weight;
            float gapAngle;
            boolean needsUpdate;
            Paint paint;

            BubbleCollection(BubbleManager parent_, int stepSize_, float radius_, float weight_, float gapAngle_, Paint paint_) {
                stepSize = stepSize_;
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

            private void add(int count_, boolean shouldFeature, int glanceDuration_) {
                if (count_ < 0) {
                    remove(-count_);
                    return;
                }

                int bubbleCount = bubbles.size();
                for (int i = 0; i < count_; i++) {
                    int newVal = ++bubbleCount * stepSize;
                    Bubble b = new Bubble(this, newVal, radius, weight, gapAngle,
                            shouldFeature && i == count_ - 1, glanceDuration_, paint);  // @JAMES: Only the last bubble in the group gets featured
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
            private static final float GRAVITY_FACTOR           = 0.80f; // how much does gravity weight in global forces
            private static final float ANCHOR_SPRING_FACTOR     = 0.02f; // how much spring from lock position

            private static final float DEPTH_ACCEL_FACTOR       = 0.40f;
            private static final float DEPTH_SPRING_FACTOR      = 0.10f;

            private static final float RANDOM_WEIGHT_FACTOR     = 0.50f; // how much variation between balls in the same category

            private static final float TEXT_HEIGHT_FACTOR       = 0.20f; // as a factor of bubble radius

            BubbleCollection parent;

            int value;
            String valueStr;
            float anchorX, anchorY;
            float x, y;
            float gapAngle;
            float velX, velY;

            float accX, accY;
            float radius, weight;
            float velR, accR;

            boolean needsSizeUpdate = false;
            float currentRadius = 0;
            float targetRadius = 0;

            boolean mustDie = false;
            boolean isFeatured = false;
            int featuredGlanceDuration;  // for how many glances is this bubble featured?

            Paint paint;
            Path path;

            Bubble(BubbleCollection parent_, int value_, float radius_, float weight_, float gapAngle_, boolean isFeatured_, int glanceDuration_, Paint paint_) {
                value = value_;
                valueStr = mTestStepFormatter.format(value);
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

//                gapAngle = gapAngle_;
                gapAngle = TAU * (float) Math.random();

                isFeatured = isFeatured_;
                if (isFeatured) parent.parent.toDefeatureBuffer.add(this);
                featuredGlanceDuration = glanceDuration_;

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
                if (isFeatured) canvas.drawCircle(0, 0, 1.0f, paint);
                canvas.drawPath(path, paint);
                canvas.restore();

                // Doing this outside the transform to avoid weirdness with tiny heighted text
                if (isFeatured) {
                    mBubbleTextPaint.setTextSize(2 * TEXT_HEIGHT_FACTOR * currentRadius);  // bubble size might be animated
                    drawTextVerticallyCentered(canvas, mBubbleTextPaint, valueStr, x, y);
                }
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

        private class Splash10KScreen {
            private static final int R = 238;
            private static final int G = 42;
            private static final int B = 132;
            private static final int FADE_IN_SPEED = 8;
            private static final float TEXT_SIZE = 0.10f;  // as a factor of screen height
            private static final float TEXT_SPEED = 0.25f;

            private int alpha;
            private float textSize;
            private float textX, textY;

            Splash10KScreen() { }

            // Must be called after onSurfaceChanged
            public void initialize() {
                alpha = 0;
                textSize = TEXT_SIZE * mHeight;
                textX = mCenterX;
                textY = 1.25f * mHeight;
            }

            public void render(Canvas canvas) {
                canvas.drawColor(Color.argb(alpha, R, G, B));

//                mBubbleTextPaint.setColor(Color.argb(alpha, 255, 255, 255));
                mBubbleTextPaint.setTextSize(textSize);
                drawTextVerticallyCentered(canvas, mBubbleTextPaint, "10,000", textX, textY);
//                mBubbleTextPaint.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);  // revert

                alpha += FADE_IN_SPEED;
                if (alpha > 255) alpha = 255;
                textY -= TEXT_SPEED * (textY - mCenterY);
                if (DEBUG_LOGS) Log.v(TAG, "textY: " + textY);
            }
        }

    }









    private SensorManager mSensorManager;

    private SensorWrapper mSensorAccelerometer;
    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];

    private SensorWrapper mSensorStep;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                mSensorAccelerometer.update(event);
                updateGravity(event);
                break;
            case Sensor.TYPE_STEP_COUNTER:
                if (!GENERATE_FAKE_STEPS) {
                    if (DEBUG_LOGS) Log.i(TAG, "New step count: " + Float.toString(event.values[0]));
//                    mCurrentSteps = Math.round(event.values[0]);
                    mSensorStep.update(event);
                }
                break;
        }
    }


    public void updateGravity(SensorEvent event) {
        // In this example, alpha is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the event delivery rate.
        final float alpha = 0.8f;

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];
    }



    private class SensorWrapper {

        SensorEventListener listener;
        SensorManager manager;
        String name;
        int type;
        Sensor sensor;
        boolean isActive, isRegistered;
        int valueCount;
        float[] values;

        SensorWrapper(String name_, int sensorType_, int valueCount_, SensorEventListener listener_, SensorManager manager_) {
            listener = listener_;
            manager = manager_;
            name = name_;
            type = sensorType_;
            valueCount = valueCount_;
            values = new float[valueCount];

            // Initialize the sensor
            sensor = manager.getDefaultSensor(type);

            // http://developer.android.com/guide/topics/sensors/sensors_overview.html#sensors-identify
            if (sensor == null) {
                if (DEBUG_LOGS) Log.v(TAG, "Sensor " + name + " not available in this device");
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
            isRegistered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (DEBUG_LOGS) Log.i(TAG, "Registered " + name + ": " + isRegistered);
            return isRegistered;
        }

        boolean unregister() {
            if (!isActive) return false;
            if (!isRegistered) return false;
            manager.unregisterListener(listener);
            isRegistered = false;
            if (DEBUG_LOGS) Log.i(TAG, "Unregistered " + name);
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


}
