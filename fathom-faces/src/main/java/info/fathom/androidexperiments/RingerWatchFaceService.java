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
import java.util.concurrent.TimeUnit;

public class RingerWatchFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "RingerWatchFaceService";

    private static final float TAU = (float) (2 * Math.PI);

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

    private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
    private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

    private static final String  RALEWAY_TYPEFACE_PATH = "fonts/raleway-regular-enhanced.ttf";
    private static final String  RALEWAY_MED_TYPEFACE_PATH = "fonts/Raleway-Medium.ttf";
    private static final String  RALEWAY_SEMI_TYPEFACE_PATH = "fonts/Raleway-SemiBold.ttf";

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

    private static final int     TEXT_AMBIENT_SHADOW_RADIUS = 1;

    private static final int     RESET_HOUR = 4;                                                    // at which hour will watch face reset [0...23], -1 to deactivate

//    private static final int     INITIAL_FREE_STEPS = 5;

    // DEBUG

    private static final boolean DEBUG_LOGS = true;

    private static final boolean GENERATE_FAKE_STEPS = true;
    private static final int     RANDOM_FAKE_STEPS = 3000;
    private static final int     MAX_STEP_THRESHOLD = 1000000;
    private static final boolean SHOW_BUBBLE_VALUE_TAGS = false;
    private static final boolean RANDOM_TIME_PER_GLANCE = true;  // this will add an hour to the time at each glance
    private static final int     RANDOM_MINUTES_INC = 300;
    private static final boolean VARIABLE_FRICTION = false;
    private static final boolean DEBUG_STEP_COUNTERS = false;

    private static final boolean DEBUG_FAKE_START_TIME = true;
    private static final int     DEBUG_FAKE_START_HOUR = 14;
    private static final int     DEBUG_FAKE_START_MINUTE = 0;





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

        private TimeManager mTimeManager;
        private String mTimeStr;
        private int mLastAmbientHour;
        private int glances;

        private Paint mTextDigitsPaintInteractive, mTextDigitsPaintAmbient;
        private float mTextDigitsHeight, mTextDigitsBaselineHeight, mTextDigitsRightMargin;
        private Paint mTextStepsPaintInteractive, mTextStepsPaintAmbient;
        private float mTextStepsHeight, mTextStepsBaselineHeight, mTextStepsRightMargin;
        private Paint mTextDigitsShadowPaintInteractive, mTextStepsShadowPaintInteractive;
        private Typeface mTextTypeface, mTextTypefaceMed, mTextTypefaceSemi;
        private DecimalFormat mTestStepFormatter = new DecimalFormat("##,###");
        private final Rect textBounds = new Rect();
        private int mTextAlpha = 255;

        private int mStepBuffer = 0;
        private boolean firstLoad = true;
        private int mPrevSteps = 0;
        private int mCurrentSteps = 0;
        private float mStepCountDisplay;

        private Paint mBubbleTextPaint;

        private int mWidth;
        private int mHeight;
        private float mCenterX, mCenterY;
        private boolean mIsRound;
        private float mRadius;

        private BubbleManager bubbleManager;
        private SplashScreen splashScreen;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(RingerWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mTextTypeface = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    RALEWAY_TYPEFACE_PATH);
            mTextTypefaceMed = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    RALEWAY_MED_TYPEFACE_PATH);
            mTextTypefaceSemi = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    RALEWAY_SEMI_TYPEFACE_PATH);

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

            mTextDigitsShadowPaintInteractive = new Paint();
            mTextDigitsShadowPaintInteractive.setColor(BACKGROUND_COLOR_AMBIENT);
            mTextDigitsShadowPaintInteractive.setTypeface(mTextTypeface);
            mTextDigitsShadowPaintInteractive.setAntiAlias(false);
            mTextDigitsShadowPaintInteractive.setTextAlign(Paint.Align.RIGHT);

            mTextStepsShadowPaintInteractive = new Paint();
            mTextStepsShadowPaintInteractive.setColor(BACKGROUND_COLOR_AMBIENT);
            mTextStepsShadowPaintInteractive.setTypeface(mTextTypeface);
            mTextStepsShadowPaintInteractive.setAntiAlias(false);
            mTextStepsShadowPaintInteractive.setTextAlign(Paint.Align.RIGHT);

            mBubbleTextPaint = new Paint();
            mBubbleTextPaint.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mBubbleTextPaint.setTypeface(mTextTypeface);
            mBubbleTextPaint.setAntiAlias(true);
            mBubbleTextPaint.setTextAlign(Paint.Align.CENTER);

            bubbleManager = new BubbleManager();
            splashScreen = new SplashScreen();

            mTimeManager = new TimeManager() {
                @Override
                public void onReset() {
                    if (DEBUG_LOGS) Log.v(TAG, "RESETTING!!");
                    mStepBuffer = (int) mSensorStep.values[0];
                    mPrevSteps = 0;
                    mCurrentSteps = 0;
                    bubbleManager.clearBubbles();
                    bubbleManager.prevSteps = 0;
                    bubbleManager.currentSteps = 0;
                }
            };
            if (RESET_HOUR >= 0) {
                mTimeManager.setOvernightResetHour(RESET_HOUR);
            }

            glances = 0;

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = new SensorWrapper("Accelerometer", Sensor.TYPE_ACCELEROMETER, 3,
                    RingerWatchFaceService.this, mSensorManager);
            mSensorAccelerometer.register();
            mSensorStep = new SensorWrapper("Steps", Sensor.TYPE_STEP_COUNTER, 1,
                    RingerWatchFaceService.this, mSensorManager);
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

            // Last bubble must be updated here because onDraw() happens before
            // receiving the SCREEN_OFF Intent, and this is useless in non-ambient mode anyway...
            if (inAmbientMode) {
                bubbleManager.updateLatestBubble();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (DEBUG_LOGS) Log.v(TAG, "onVisibilityChanged: " + visible);
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
            RingerWatchFaceService.this.registerReceiver(mScreenReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_ON));
            RingerWatchFaceService.this.registerReceiver(mScreenReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }

        private void unregisterScreenReceiver() {
            RingerWatchFaceService.this.unregisterReceiver(mScreenReceiver);
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
                mWasStepSensorUpdatedThisGlance = false;
                mWereStepCountsUpdatedThisGlance = false;

                glances++;

                bubbleManager.newGlance();

                if (RANDOM_TIME_PER_GLANCE) {
                    mTimeManager.addRandomInc();
                }

                registerTimeZoneReceiver();
                mSensorStep.register();
                mSensorAccelerometer.register();

            } else {
                bubbleManager.byeGlance();

                unregisterTimeZoneReceiver();
                mSensorStep.unregister();
                mSensorAccelerometer.unregister();

                bubbleManager.resetMotion();

                splashScreen.deactivate();
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

            splashScreen.reset();

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

            mTextDigitsShadowPaintInteractive.setTextSize(mTextDigitsHeight);
            mTextStepsShadowPaintInteractive.setTextSize(mTextStepsHeight);


            bubbleManager.setScreenWidth(mWidth);
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

            mTimeManager.setToNow();  // if RANDOM_TIME_PER_GLANCE it won't update toNow
            mTimeStr = (mTimeManager.hour % 12 == 0 ? 12 : mTimeManager.hour % 12) + ":" + String.format("%02d", mTimeManager.minute);

            if (mAmbient) {
                if (DEBUG_LOGS) Log.v(TAG, "Drawing ambient canvas");

                canvas.drawColor(BACKGROUND_COLOR_AMBIENT);

                bubbleManager.renderAmbient(canvas);

                drawFakeShadowedText(canvas, mTimeStr,
                        mWidth - (int) mTextDigitsRightMargin, (int) mTextDigitsBaselineHeight,
                        TEXT_AMBIENT_SHADOW_RADIUS, mTextDigitsShadowPaintInteractive, mTextDigitsPaintAmbient);
                drawFakeShadowedText(canvas, mTestStepFormatter.format(mCurrentSteps) + "#",
                        mWidth - (int) mTextStepsRightMargin, (int) mTextStepsBaselineHeight,
                        TEXT_AMBIENT_SHADOW_RADIUS, mTextStepsShadowPaintInteractive, mTextStepsPaintAmbient);

                if (DEBUG_STEP_COUNTERS) {
                    canvas.drawText((int) mSensorStep.values[0] + " S", 0.75f * mWidth,
                            0.75f * mHeight, mTextStepsPaintAmbient);
                    canvas.drawText(mStepBuffer + " B", 0.75f * mWidth,
                            0.85f * mHeight, mTextStepsPaintAmbient);
                }

            } else {

                if (mWasStepSensorUpdatedThisGlance && !mWereStepCountsUpdatedThisGlance) {
                    if (DEBUG_LOGS) Log.v(TAG, "Triggered updateStepCounts()");
                    updateStepCounts();
                    mWereStepCountsUpdatedThisGlance = true;
                }

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

                if (splashScreen.active) {
                    mTextAlpha -= splashScreen.FADE_IN_SPEED;
                    if (mTextAlpha < 0) mTextAlpha = 0;
                    mTextDigitsPaintInteractive.setColor(Color.argb(mTextAlpha, 255, 255, 255));
                    mTextStepsPaintInteractive.setColor(Color.argb(mTextAlpha, 255, 255, 255));
                }

                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintInteractive);
                canvas.drawText(mTestStepFormatter.format(mStepCountDisplay) + "#", mWidth - mTextStepsRightMargin,
                        mTextStepsBaselineHeight, mTextStepsPaintInteractive);

                if (DEBUG_STEP_COUNTERS) {
                    canvas.drawText((int) mSensorStep.values[0] + " S", 0.75f * mWidth,
                            0.75f * mHeight, mTextStepsPaintInteractive);
                    canvas.drawText(mStepBuffer + " B", 0.75f * mWidth,
                            0.85f * mHeight, mTextStepsPaintInteractive);
                }

                if (splashScreen.active) splashScreen.render(canvas);

            }
        }


        private boolean mRegisteredTimeZoneReceiver = false;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTimeManager.setTimeZone(intent);
            }
        };

        private void registerTimeZoneReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            RingerWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            RingerWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
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








        private class TimeManager {

            private static final boolean DEBUG_FAKE_TIME = RANDOM_TIME_PER_GLANCE;
            private final long DEBUG_FAKE_TIME_INC = TimeUnit.MINUTES.toMillis(RANDOM_MINUTES_INC);
            private static final long DAY_IN_MILLIS = 86400000;

            private static final boolean FAKE_START_TIME = DEBUG_FAKE_START_TIME;
            private static final int     FAKE_START_HOUR = DEBUG_FAKE_START_HOUR;
            private static final int     FAKE_START_MINUTE = DEBUG_FAKE_START_MINUTE;



            private Time currentTime;
            public int year, month, monthDay, hour, minute, second;

            private boolean overnightReset;
            private int overnightResetHour;
            private Time nextResetTime;

            TimeManager() {
                overnightReset = false;
                overnightResetHour = 0;

                currentTime = new Time();
                currentTime.setToNow();

                if (FAKE_START_TIME) {
                    currentTime.setToNow();
                    currentTime.set(currentTime.second, FAKE_START_MINUTE, FAKE_START_HOUR,
                            currentTime.monthDay, currentTime.month, currentTime.year);
                    updateFields();
                } else {
                    setToNow();
                }
            }

            public void setToNow() {
                if (!DEBUG_FAKE_TIME) {
                    currentTime.setToNow();
                }

                updateFields();
                if (overnightReset) resetCheck();
            }

            public void setTimeZone(Intent intent) {
                if (!DEBUG_FAKE_TIME) {
                    currentTime.clear(intent.getStringExtra("time-zone"));
                    currentTime.setToNow();
                }

                updateFields();
                if (overnightReset) resetCheck();
            }

            public void addRandomInc() {
                long rInc = (long) (DEBUG_FAKE_TIME_INC * Math.random());
                if (DEBUG_LOGS) Log.v(TAG, "Adding randomInc: " + rInc);

                currentTime.set(currentTime.toMillis(false) + rInc);

                updateFields();
                if (overnightReset) resetCheck();
            }

            public void resetCheck() {
                if (currentTime.after(nextResetTime)) {
                    nextResetTime = computeNextResetTime();
                    if (DEBUG_LOGS) Log.v(TAG, "Reset check: true");
                    onReset();
                }
            }

            public void onReset() { Log.v(TAG, "onReset"); }

            public void setOvernightResetHour(int hour_) {
                overnightReset = true;
                overnightResetHour = hour_;
                nextResetTime = computeNextResetTime();
            }

            private Time computeNextResetTime() {
                Time resetTime = new Time();
                resetTime.set(0, 0, overnightResetHour, monthDay, month, year);  // Set to today's reset time

                // If already past, add a day
                if (currentTime.after(resetTime)) {
                    resetTime.set(resetTime.toMillis(false) + DAY_IN_MILLIS);
                }

                if (DEBUG_LOGS) Log.v(TAG, "Next reset time: " + resetTime.format2445());

                return resetTime;
            }

            private void updateFields() {
                year = currentTime.year;
                month = currentTime.month;
                monthDay = currentTime.monthDay;
                hour = currentTime.hour;
                minute = currentTime.minute;
                second = currentTime.second;
            }

            public void toDebugLog() {
                Log.v(TAG, "--> Curr time: " + currentTime.format2445());
            }


        }









        private void updateStepCounts() {

            if (firstLoad) {
                firstLoad = false;
                mStepBuffer = (int) mSensorStep.values[0];
                mPrevSteps = 0;
                mCurrentSteps = 0;
                bubbleManager.updateSteps(mCurrentSteps);
                return;
            }

            if (DEBUG_LOGS) Log.v(TAG, "mPrevSteps: " + mPrevSteps);
            if (DEBUG_LOGS) Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);

            mPrevSteps = mCurrentSteps;

            if (GENERATE_FAKE_STEPS) {
                int fakeSteps = (int) (RANDOM_FAKE_STEPS * Math.random());
                if (DEBUG_LOGS) Log.v(TAG, "Generating fake steps: " + fakeSteps);
                mCurrentSteps += fakeSteps;
            } else {
                mCurrentSteps = (int) mSensorStep.values[0] - mStepBuffer;  // read from the sensor
            }

            int stepInc = mCurrentSteps - mPrevSteps;

            if (DEBUG_LOGS) Log.v(TAG, stepInc + " new steps!");

//            if (mCurrentSteps > MAX_STEP_THRESHOLD) {
//                if (DEBUG_LOGS) Log.v(TAG, "Resetting step counts");
////                mDayBufferedSteps += mCurrentSteps;  // @TODO Store previous day steps somwhere and account for them
//                mPrevSteps = 0;
//                mCurrentSteps = 0;
//                if (DEBUG_LOGS) Log.v(TAG, "mPrevSteps: " + mPrevSteps);
//                if (DEBUG_LOGS) Log.v(TAG, "mCurrentSteps: " + mCurrentSteps);
//            }

            if (stepInc > 0) {
                bubbleManager.updateSteps(mCurrentSteps);

            // Sometimes the sensor yields a repeated reading
            } else {
                mWereStepCountsUpdatedThisGlance = false;
            }

//            bubbleManager.updateSteps(mCurrentSteps);

        }

        // http://stackoverflow.com/a/24969713/1934487
        private void drawTextVerticallyCentered(Canvas canvas, Paint paint, String text, float cx, float cy) {
            paint.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, cx, cy - textBounds.exactCenterY(), paint);
        }

        private void drawFakeShadowedText(Canvas canvas, String txt, int x, int y, int radius,
                                          Paint shadowPaint, Paint drawPaint) {
            for (int i = x - radius; i <= x + radius; i++) {
                for (int j = y - radius; j <= y + radius; j++) {
                    canvas.drawText(txt, i, j, shadowPaint);
                }
            }
            canvas.drawText(txt, x, y, drawPaint);
        }












        private class BubbleManager {

            private final static float ANIMATION_RATE           = 0.25f;

            private final static float FRICTION_START           = 0.99f;
            private final static float FRICTION_TARGET          = 0.55f;
            private final static float FRICTION_ANIMATION_RATE  = 0.02f;

            private final static int STEP_RATIO_XBIG    = 10000;
            private final static int STEP_RATIO_MBIG    = 5000;
            private final static int STEP_RATIO_BIG     = 1000;     // a BIG bubble represents this many steps
            private final static int STEP_RATIO_MEDIUM  = 100;
            private final static int STEP_RATIO_SMALL   = 10;
            private final static int STEP_RATIO_XSMALL  = 1;

//            private final static int RADIUS_XBIG    = 100;
//            private final static int RADIUS_MBIG    = 70;
//            private final static int RADIUS_BIG     = 45;
//            private final static int RADIUS_MEDIUM  = 25;
//            private final static int RADIUS_SMALL   = 15;
//            private final static int RADIUS_XSMALL  = 7;

            // Radii as a factor of screen width
            private final static float RADIUS_XBIG    = 0.37500f;
            private final static float RADIUS_MBIG    = 0.25000f;
            private final static float RADIUS_BIG     = 0.12500f;
            private final static float RADIUS_MEDIUM  = 0.06250f;
            private final static float RADIUS_SMALL   = 0.03125f;
            private final static float RADIUS_XSMALL  = 0.01562f;

            private final static float WEIGHT_XBIG      = 2;
            private final static float WEIGHT_MBIG      = 2;
            private final static float WEIGHT_BIG       = 2;
            private final static float WEIGHT_MEDIUM    = 3;
            private final static float WEIGHT_SMALL     = 3;
            private final static float WEIGHT_XSMALL    = 3;

//            private final int COLOR_XBIG    = Color.argb(204, 238, 42, 123);
//            private final int COLOR_MBIG    = Color.argb(204, 141, 198, 63);
//            private final int COLOR_BIG     = Color.argb(204, 255, 167, 39);
//            private final int COLOR_MEDIUM  = Color.argb(204, 146, 39, 143);
//            private final int COLOR_SMALL   = Color.argb(204, 39, 170, 225);
//            private final int COLOR_XSMALL  = Color.argb(204, 141, 198, 63);

            public final int[] GROUP_COLORS = {
                    Color.argb(204, 255, 255, 0),  // XBIG
                    Color.argb(204, 237, 41, 122),  // MBIG
                    Color.argb(204, 140, 199, 64),  // BIG
                    Color.argb(204, 0, 173, 240),  // MEDIUM
                    Color.argb(204, 242, 102, 33),  // SMALL
                    Color.argb(204, 128, 130, 133)   // XSMALL
            };

            private final int GROUP_COUNT = GROUP_COLORS.length;

            private final static float INNER_RING_RADIUS_FACTOR_XBIG   = 0.82f;
            private final static float INNER_RING_RADIUS_FACTOR_MBIG   = 0.80f;
            private final static float INNER_RING_RADIUS_FACTOR_BIG    = 0.78f;
            private final static float INNER_RING_RADIUS_FACTOR_MEDIUM = 0.75f;
            private final static float INNER_RING_RADIUS_FACTOR_SMALL  = 0.70f;
            private final static float INNER_RING_RADIUS_FACTOR_XSMALL = 0.65f;


            private BubbleCollection bubblesXBig, bubblesBig, bubblesMBig,
                    bubblesMedium, bubblesSmall, bubblesXSmall;

            private Bubble lastBubble;  // last created bubble with the greatest value

            private int prevSteps, currentSteps;
            private int updateKeyframe;  // @TODO add explanation here

            private float currentFriction;

            private Paint bubblePaintAmbient;

            List<Bubble> toDefeatureBuffer = new ArrayList<>();

            BubbleManager() {
                bubblesXBig = new BubbleCollection(this, STEP_RATIO_XBIG, RADIUS_XBIG,
                        WEIGHT_XBIG, GROUP_COLORS[0], INNER_RING_RADIUS_FACTOR_XBIG, true);
                bubblesMBig = new BubbleCollection(this, STEP_RATIO_MBIG, RADIUS_MBIG,
                        WEIGHT_MBIG, GROUP_COLORS[1], INNER_RING_RADIUS_FACTOR_MBIG, false);
                bubblesBig = new BubbleCollection(this, STEP_RATIO_BIG, RADIUS_BIG,
                        WEIGHT_BIG, GROUP_COLORS[2], INNER_RING_RADIUS_FACTOR_BIG, false);
                bubblesMedium = new BubbleCollection(this, STEP_RATIO_MEDIUM, RADIUS_MEDIUM,
                        WEIGHT_MEDIUM, GROUP_COLORS[3], INNER_RING_RADIUS_FACTOR_MEDIUM, false);
                bubblesSmall = new BubbleCollection(this, STEP_RATIO_SMALL, RADIUS_SMALL,
                        WEIGHT_SMALL, GROUP_COLORS[4], INNER_RING_RADIUS_FACTOR_SMALL, false);
                bubblesXSmall = new BubbleCollection(this, STEP_RATIO_XSMALL, RADIUS_XSMALL,
                        WEIGHT_XSMALL, GROUP_COLORS[5], INNER_RING_RADIUS_FACTOR_XSMALL, false);

                prevSteps = 0;
                currentSteps = 0;

                updateKeyframe = 0;  // do not update

                currentFriction = FRICTION_START;

                bubblePaintAmbient = new Paint();
                bubblePaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
                bubblePaintAmbient.setAntiAlias(false);
                bubblePaintAmbient.setStyle(Paint.Style.STROKE);
            }

            public void render(Canvas canvas) {
                bubblesXSmall.render(canvas);
                bubblesSmall.render(canvas);
                bubblesMedium.render(canvas);
                bubblesBig.render(canvas);
                bubblesMBig.render(canvas);
                bubblesXBig.render(canvas);
            }

            public void renderAmbient(Canvas canvas) {
                if (lastBubble != null) lastBubble.renderAmbient(canvas, bubblePaintAmbient);
            }

            public void update() {

                switch (updateKeyframe) {
                    // @TODO verify if bubble count is working on the long run
                    case 1:
                        bubblesXSmall.add((currentSteps % STEP_RATIO_SMALL) - bubblesXSmall.bubbles.size(), false, false, 0);
                        int stepInc = currentSteps - prevSteps;
                        currentSteps -= stepInc % STEP_RATIO_SMALL;  // account for the remainder of the division
                        bubblesSmall.add(stepInc / STEP_RATIO_SMALL, false, false, 0);
                        updateKeyframe++;
                        break;
                    case 2:
                        boolean continueUpdating1 = bubblesXSmall.update() && bubblesSmall.update();
                        if (!continueUpdating1) updateKeyframe++;
                        break;
                    case 3:
                        int scaleRatioMS = STEP_RATIO_MEDIUM / STEP_RATIO_SMALL;
                        int smallBubbleCount = bubblesSmall.bubbles.size();
                        int newMediumBubbleCount = smallBubbleCount / scaleRatioMS;
                        bubblesSmall.remove(newMediumBubbleCount * scaleRatioMS);
                        bubblesMedium.add(newMediumBubbleCount, false, false, 0);
                        updateKeyframe++;
                        break;
                    case 4:
                        bubblesSmall.update();
                        bubblesMedium.update();
                        boolean continueUpdating3 =
                                bubblesSmall.needsUpdate || bubblesMedium.needsUpdate;
                        if (!continueUpdating3) updateKeyframe++;
                        break;
                    case 5:
                        int scaleRatioBM = STEP_RATIO_BIG / STEP_RATIO_MEDIUM;
                        int mediumBubbleCount = bubblesMedium.bubbles.size();
                        int newBigBubbleCount = mediumBubbleCount / scaleRatioBM;
                        bubblesMedium.remove(newBigBubbleCount * scaleRatioBM);
                        bubblesBig.add(newBigBubbleCount, SHOW_BUBBLE_VALUE_TAGS,
                                mPrevSteps < STEP_RATIO_BIG && mCurrentSteps >= STEP_RATIO_BIG, 0);
                        updateKeyframe++;
                        break;
                    case 6:
                        bubblesMedium.update();
                        bubblesBig.update();
                        boolean continueUpdating5 =
                                bubblesMedium.needsUpdate || bubblesBig.needsUpdate;
                        if (!continueUpdating5) updateKeyframe++;
                        break;
                    case 7:
                        int scaleRatioBMB = STEP_RATIO_MBIG / STEP_RATIO_BIG;
                        int bigBubbleCount = bubblesBig.bubbles.size();
                        int newMBigBubbleCount = bigBubbleCount / scaleRatioBMB;
                        bubblesBig.remove(newMBigBubbleCount * scaleRatioBMB);
                        bubblesMBig.add(newMBigBubbleCount, SHOW_BUBBLE_VALUE_TAGS,
                                mPrevSteps < STEP_RATIO_MBIG && mCurrentSteps >= STEP_RATIO_MBIG, 0);
                        updateKeyframe++;
                        break;
                    case 8:
                        bubblesBig.update();
                        bubblesMBig.update();
                        boolean continueUpdating6 =
                                bubblesBig.needsUpdate || bubblesMBig.needsUpdate;
                        if (!continueUpdating6) updateKeyframe++;  // stop animation transition
                        break;
                    case 9:
                        int scaleRatioMBXB = STEP_RATIO_XBIG / STEP_RATIO_MBIG;
                        int mBigBubbleCount = bubblesMBig.bubbles.size();
                        int newXBigBubbleCount = mBigBubbleCount / scaleRatioMBXB;
                        bubblesMBig.remove(newXBigBubbleCount * scaleRatioMBXB);
                        bubblesXBig.add(newXBigBubbleCount, SHOW_BUBBLE_VALUE_TAGS,
                                (mPrevSteps < STEP_RATIO_XBIG && mCurrentSteps >= STEP_RATIO_XBIG) ||  // 10k
                                (mPrevSteps < 2 * STEP_RATIO_XBIG && mCurrentSteps >= 2 * STEP_RATIO_XBIG),  // 20k
                                0);
                        updateKeyframe++;
                        break;
                    case 10:
                        bubblesMBig.update();
                        bubblesXBig.update();
                        boolean continueUpdating7 =
                                bubblesMBig.needsUpdate || bubblesXBig.needsUpdate;
                        if (!continueUpdating7) updateKeyframe = 0;  // stop animation transition
                        break;

                    // Nuke everything
                    case 20:
                        bubblesXSmall.remove(bubblesXSmall.bubbles.size());
                        bubblesSmall.remove(bubblesSmall.bubbles.size());
                        bubblesMedium.remove(bubblesMedium.bubbles.size());
                        bubblesBig.remove(bubblesBig.bubbles.size());
                        bubblesMBig.remove(bubblesMBig.bubbles.size());
                        bubblesXBig.remove(bubblesXBig.bubbles.size());
                        updateKeyframe++;
                        break;
                    case 21:
                        bubblesXBig.update();
                        bubblesMBig.update();
                        bubblesBig.update();
                        bubblesMedium.update();
                        bubblesSmall.update();
                        bubblesXSmall.update();
                        boolean continueUpdating21 =
                                bubblesXBig.needsUpdate ||
                                bubblesMBig.needsUpdate ||
                                bubblesBig.needsUpdate ||
                                bubblesMedium.needsUpdate ||
                                bubblesSmall.needsUpdate ||
                                bubblesXSmall.needsUpdate;
                        if (continueUpdating21) updateKeyframe = 1;  // add any buffered remaining bubbles
                        break;

                    default:
                        break;
                }

                if (VARIABLE_FRICTION) {
                    currentFriction += FRICTION_ANIMATION_RATE * (FRICTION_TARGET - currentFriction);
                    if (DEBUG_LOGS) Log.v(TAG, "currentFriction: " + currentFriction);
                }

                updatePositions();
            }

            public void updateSteps(int currentSteps_) {
                prevSteps = currentSteps;
                currentSteps = currentSteps_;
//                if (updateKeyframe == 0 ) {
                updateKeyframe = 1;  // trigger size update chain
//                }
            }

            public void updatePositions() {
                bubblesXBig.updatePositions();
                bubblesMBig.updatePositions();
                bubblesBig.updatePositions();
                bubblesMedium.updatePositions();
                bubblesSmall.updatePositions();
                bubblesXSmall.updatePositions();
            }

            public void resetMotion() {
                bubblesXBig.resetMotion();
                bubblesMBig.resetMotion();
                bubblesBig.resetMotion();
                bubblesMedium.resetMotion();
                bubblesSmall.resetMotion();
                bubblesXSmall.resetMotion();
            }

            public void newGlance() {
                currentFriction = FRICTION_START;
            }

            public void byeGlance() {
                for (Bubble bubble : toDefeatureBuffer) {
                    if (--bubble.featuredGlanceDuration <= 0) {
                        bubble.isFeatured = false;
                    }
                }
                for (int i = toDefeatureBuffer.size() - 1; i >= 0; i--) {
                    if (!toDefeatureBuffer.get(i).isFeatured) toDefeatureBuffer.remove(i);
                }
            }

            public void updateLatestBubble() {
                lastBubble = getLatestBubble();
                if (lastBubble != null && DEBUG_LOGS) Log.v(TAG, "lastBubble.value: " + lastBubble.value);
            }

            private Bubble getLatestBubble() {
                if (bubblesXBig.bubbles.size() != 0)
                    return bubblesXBig.bubbles.get(bubblesXBig.bubbles.size() - 1);
                if (bubblesMBig.bubbles.size() != 0)
                    return bubblesMBig.bubbles.get(bubblesMBig.bubbles.size() - 1);
                if (bubblesBig.bubbles.size() != 0)
                    return bubblesBig.bubbles.get(bubblesBig.bubbles.size() - 1);
                if (bubblesMedium.bubbles.size() != 0)
                    return bubblesMedium.bubbles.get(bubblesMedium.bubbles.size() - 1);
                if (bubblesSmall.bubbles.size() != 0)
                    return bubblesSmall.bubbles.get(bubblesSmall.bubbles.size() - 1);
                if (bubblesXSmall.bubbles.size() != 0)
                    return bubblesXSmall.bubbles.get(bubblesXSmall.bubbles.size() - 1);

                return null;
            }

            public void setScreenWidth(float width_) {
                bubblesXBig.setScreenWidth(width_);
                bubblesMBig.setScreenWidth(width_);
                bubblesBig.setScreenWidth(width_);
                bubblesMedium.setScreenWidth(width_);
                bubblesSmall.setScreenWidth(width_);
                bubblesXSmall.setScreenWidth(width_);
            }

            public void clearBubbles() {
                bubblesXBig.bubbles.clear();
                bubblesMBig.bubbles.clear();
                bubblesBig.bubbles.clear();
                bubblesMedium.bubbles.clear();
                bubblesSmall.bubbles.clear();
                bubblesXSmall.bubbles.clear();
            }

        }


        private class BubbleCollection {

            private static final float COLOR_INTERPOLATION_RATE = 0.15f;

            BubbleManager parent;
            List<Bubble> bubbles;
            List<Bubble> killQueue;
            int stepSize;
            float radius;
            float weight;
            float innerRingFactor;
            boolean needsUpdate;
            boolean isEmpty;

            int color;
            Paint paint;
            boolean animatedColor;
            int currentColor, targetColor;

            BubbleCollection(BubbleManager parent_, int stepSize_, float radius_, float weight_,
                             int color_, float innerRingFactor_, boolean animatedColor_) {
                stepSize = stepSize_;
                parent = parent_;
                radius = radius_;
                weight = weight_;
                innerRingFactor = innerRingFactor_;
                bubbles = new ArrayList<>();
                killQueue = new ArrayList<>();
                isEmpty = true;

                color = color_;
                paint = new Paint();
                paint.setColor(color);
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                animatedColor = animatedColor_;
                currentColor = color;
                targetColor = color;
            }

            public void render(Canvas canvas) {
                if (animatedColor && !isEmpty) {
                    int prevColor = currentColor;
                    currentColor = interpolateColor(currentColor, targetColor, COLOR_INTERPOLATION_RATE);
                    if (currentColor == prevColor) {
                        targetColor = bubbleManager.GROUP_COLORS[(int) ((bubbleManager.GROUP_COUNT - 1) * Math.random())];  // avoid using the smallest bubble's color
                    }
                    paint.setColor(currentColor);
                }
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

            private void add(int count_, boolean shouldFeature, boolean showSplashScreen, int glanceDuration_) {
                if (count_ < 0) {
                    remove(-count_);
                    return;
                }

                int bubbleCount = bubbles.size();
                for (int i = 0; i < count_; i++) {
                    int newVal = ++bubbleCount * stepSize;
                    Bubble b = new Bubble(this, newVal, radius, weight, innerRingFactor,
                            shouldFeature && i == count_ - 1, glanceDuration_, paint);  // @JAMES: Only the last bubble in the group gets featured
                    b.grow();
                    bubbles.add(b);
                    isEmpty = false;
                }

                if (showSplashScreen) {
                    splashScreen.trigger(stepSize, color);
                }
            }

            private void remove(int count_) {
                for (int i = 0; i < count_; i++) {
                    bubbles.get(i).kill();
                }
                isEmpty = bubbles.size() == 0;
            }

            private void resetMotion() {
                for (Bubble bub : bubbles) {
                    bub.resetMotion();
                }
            }

            public void setScreenWidth(float width_) {
                for (Bubble bub : bubbles) {
                    bub.setScreenWidth(width_);
                }
            }

            public void reset() {
                for (Bubble bub : bubbles) {
                    bub.kill();
                }
            }

            private int interpolateColor(int sourceColor, int targetColor, float parameter) {
                int sA = (sourceColor >> 24) & 0xFF;
                int sR = (sourceColor >> 16) & 0xFF;
                int sG = (sourceColor >> 8) & 0xFF;
                int sB = (sourceColor) & 0xFF;

                int tA = (targetColor >> 24) & 0xFF;
                int tR = (targetColor >> 16) & 0xFF;
                int tG = (targetColor >> 8) & 0xFF;
                int tB = (targetColor) & 0xFF;

                int currA = sA + Math.round(parameter * (tA - sA));
                int currR = sR + Math.round(parameter * (tR - sR));
                int currG = sG + Math.round(parameter * (tG - sG));
                int currB = sB + Math.round(parameter * (tB - sB));

                return Color.argb(currA, currR, currG, currB);
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

//            private static final float FRICTION                 = 0.95f; // 0 - 1, 0 is total friction  --> Replaced by global bubbleManager.currentFriction
            private static final float PLANE_ACCEL_FACTOR       = 0.25f; // when level, how much shake?
            private static final float GRAVITY_FACTOR           = 0.80f; // how much does gravity weight in global forces
            private static final float ANCHOR_SPRING_FACTOR     = 0.02f; // how much spring from lock position
            private static final float DEPTH_ACCEL_FACTOR       = 0.40f;
            private static final float DEPTH_SPRING_FACTOR      = 0.10f;
            private static final float RANDOM_WEIGHT_FACTOR     = 0.75f; // how much variation between balls in the same category
            private static final float TEXT_HEIGHT_FACTOR       = 0.20f; // as a factor of bubble radius
            private static final float INNER_RING_OFFSET_FACTOR = 0.10f;

            BubbleCollection parent;

            int value;
            String valueStr;
            float anchorX, anchorY;
            float x, y;
            float gapAngle;
            float velX, velY;

            float accX, accY;
            float screenW, relRadius;
            float radius, weight, innerRingFactor;
            float velR, accR;

            boolean needsSizeUpdate = false;
            float currentRadius = 0;
            float targetRadius = 0;

            boolean mustDie = false;
            boolean isFeatured = false;
            int featuredGlanceDuration;  // for how many glances is this bubble featured?

            Paint paint;
            Path path;

            Bubble(BubbleCollection parent_, int value_, float radius_, float weight_,
                   float innerRingFactor_, boolean isFeatured_, int glanceDuration_,
                   Paint paint_) {
                value = value_;
                valueStr = mTestStepFormatter.format(value);
                parent = parent_;
                anchorX = (float) (mWidth * Math.random());
                anchorY = (float) (mHeight * Math.random());
                x = mCenterX;
                y = mCenterY;
//                radius = radius_;
                screenW = mWidth;  // this may have been initialized already, or be zero...
                relRadius = radius_;
                radius = screenW * relRadius;
                weight = weight_ + (float) (weight_ * RANDOM_WEIGHT_FACTOR * Math.random());  // slight random weight variation
                innerRingFactor = innerRingFactor_;
                paint = paint_;
                velX = velY = accX = accY = 0;
                velR = accR = 0;

                gapAngle = TAU * (float) Math.random();

                isFeatured = isFeatured_;
                if (isFeatured) parent.parent.toDefeatureBuffer.add(this);
                featuredGlanceDuration = glanceDuration_;

                path = new Path();
                path.addCircle(0, 0, 1.0f, Path.Direction.CW);
                path.close();
                path.addCircle(INNER_RING_OFFSET_FACTOR * (float) Math.cos(gapAngle),
                        INNER_RING_OFFSET_FACTOR * (float) Math.sin(gapAngle),
                        innerRingFactor, Path.Direction.CW);
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

            public void renderAmbient(Canvas canvas, Paint paint_) {
                canvas.save();
                canvas.translate(x, y);
                canvas.scale(currentRadius, currentRadius);
                canvas.drawPath(path, paint_);
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
//                velX *= FRICTION;
//                velY *= FRICTION;
                velX *= bubbleManager.currentFriction;
                velY *= bubbleManager.currentFriction;
                x += velX;
                y += velY;

                if (DEPTH_BOUNCING && !needsSizeUpdate) {
//                    accR = (DEPTH_ACCEL_FACTOR * linear_acceleration[2] + DEPTH_SPRING_FACTOR * (radius - currentRadius)) / weight;
                    accR = (DEPTH_ACCEL_FACTOR * linear_acceleration[2] + DEPTH_SPRING_FACTOR * (radius - currentRadius)) / 3;  // Z movement is equally weighted
                    velR += accR;
//                    velR *= FRICTION;
                    velR *= bubbleManager.currentFriction;
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
                velX = velY = accX = accY = velR = accR = 0;
            }

            public void setScreenWidth(float width_) {
                if (DEBUG_LOGS) Log.v(TAG, "Setting swidth = for bubble " + value);
                screenW = width_;
                radius = screenW * relRadius;
                grow();
                if (DEBUG_LOGS) Log.v(TAG, "set relRadius: " + relRadius + ", radius: " + radius);
            }

        }

        private class SplashScreen {
            private static final int FADE_IN_SPEED = 8;
            private static final float TEXT_SIZE = 0.10f;  // as a factor of screen height
            private static final float TEXT_SPEED = 0.25f;
            private static final int MAX_ALPHA = 225;

            private int alpha;
//            private float textSize;
            private float textX, textDigitsY, textStepsY;

            private int bgColor;
            private int r, g, b;
            private int bgColorIterator;
            private int value;
            private String text;
            private boolean active;

            private Paint digitsPaint, stepsPaint;

            SplashScreen() {
                digitsPaint = new Paint();
                digitsPaint.setColor( BACKGROUND_COLOR_AMBIENT );
                digitsPaint.setTypeface(mTextTypefaceMed);
                digitsPaint.setAntiAlias(true);
                digitsPaint.setTextAlign(Paint.Align.LEFT);

                stepsPaint = new Paint();
                stepsPaint.setColor( BACKGROUND_COLOR_AMBIENT );
                stepsPaint.setTypeface(mTextTypefaceSemi);
                stepsPaint.setAntiAlias(true);
                stepsPaint.setTextAlign(Paint.Align.LEFT);
            }

            // Must be called after onSurfaceChanged
            public void reset() {
                alpha = 0;
//                textX = mCenterX;
                textX = mWidth * TEXT_DIGITS_RIGHT_MARGIN;
//                textY = 1.25f * mHeight;
                textDigitsY = mTextDigitsBaselineHeight + mHeight;
                textStepsY = mTextStepsBaselineHeight + mHeight;
                active = false;
                bgColorIterator = 0;

//                textSize = TEXT_SIZE * mHeight;
                digitsPaint.setTextSize(mTextDigitsHeight);
                stepsPaint.setTextSize(mTextStepsHeight);
            }

            public void trigger(int value_, int color_) {
                if (DEBUG_LOGS) Log.v(TAG, "SS trigger: " + value_);
                value = value_;
                text = mTestStepFormatter.format(value);
                setColor(color_);
                reset();
                active = true;
            }

            // deactivate it
            public void deactivate() {
                if (active) {
                    active = false;
                    if (DEBUG_LOGS) Log.v(TAG, "Deactivated splashscreen");
                    mTextAlpha = 255;
                    mTextDigitsPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
                    mTextStepsPaintInteractive.setColor(TEXT_STEPS_COLOR_INTERACTIVE);
                }
            }

            public void render(Canvas canvas) {
//                if (DEBUG_LOGS) Log.v(TAG, "Rendering splashscreen, alpha: " + alpha);

                canvas.drawColor(Color.argb(alpha, r, g, b));
                canvas.drawText(text, textX, textDigitsY, digitsPaint);
                canvas.drawText("steps", textX, textStepsY, stepsPaint);

                if (alpha < MAX_ALPHA) {
                    alpha += FADE_IN_SPEED;
                    if (value == 10000 || value == 20000) {
                        cycleBGColor();
                    }
                } else {
                    alpha = MAX_ALPHA;
                    if (value == 10000 || value == 20000) {
                        setColor(bubbleManager.GROUP_COLORS[0]);
                    }
                }

                textDigitsY -= TEXT_SPEED * (textDigitsY - mTextDigitsBaselineHeight);
                textStepsY -= TEXT_SPEED * (textStepsY- mTextStepsBaselineHeight);
            }

            private void setColor(int color_) {
                bgColor = color_;
                r = Color.red(bgColor);
                g = Color.green(bgColor);
                b = Color.blue(bgColor);
            }

            private void cycleBGColor() {
                int newColor = bubbleManager.GROUP_COLORS[bgColorIterator];
                setColor(newColor);
                if (++bgColorIterator >= bubbleManager.GROUP_COUNT) bgColorIterator = 0;
            }
        }

    }









    private SensorManager mSensorManager;

    private SensorWrapper mSensorAccelerometer;
    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];

    private SensorWrapper mSensorStep;
    public boolean mWasStepSensorUpdatedThisGlance = false,
            mWereStepCountsUpdatedThisGlance = false;

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
//                if (!GENERATE_FAKE_STEPS) {
                    if (DEBUG_LOGS) Log.i(TAG, "Sensor.TYPE_STEP_COUNTER event.values[0]: " + Float.toString(event.values[0]));
//                    mCurrentSteps = Math.round(event.values[0]);
                    mSensorStep.update(event);
                    mWasStepSensorUpdatedThisGlance = true;
//                }
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
