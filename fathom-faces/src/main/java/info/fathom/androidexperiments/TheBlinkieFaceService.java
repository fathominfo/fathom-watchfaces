package info.fathom.androidexperiments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;


public class TheBlinkieFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "TheBlinkieFaceService";

    private static final float TAU = (float) (2 * Math.PI);

    private static final float QUARTER_TAU = TAU / 4;
    private static final float TO_DEGS = 360.0f / TAU;
    private static final float GRAVITY_THRESHOLD = 1.0f;

    private static final long  INTERACTIVE_UPDATE_RATE_MS = 33;

    private static final int   BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
    private static final int   BACKGROUND_COLOR_AMBIENT = Color.BLACK;

    private static final int   TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int   TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
    private static final float TEXT_DIGITS_HEIGHT = 0.2f;  // as a factor of screen height
    private static final float TEXT_DIGITS_BASELINE_HEIGHT = 0.43f;  // as a factor of screen height
    private static final float TEXT_DIGITS_RIGHT_MARGIN = 0.08f;  // as a factor of screen width

    private static final int   TEXT_GLANCES_COLOR_INTERACTIVE = Color.WHITE;
    private static final int   TEXT_GLANCES_COLOR_AMBIENT = Color.WHITE;
    private static final float TEXT_GLANCES_HEIGHT = 0.10f;  // as a factor of screen height
    private static final float TEXT_GLANCES_BASELINE_HEIGHT = TEXT_DIGITS_BASELINE_HEIGHT + 0.15f;  // as a factor of screen height
    private static final float TEXT_GLANCES_RIGHT_MARGIN = 0.08f;  // as a factor of screen width

    private static final String RALEWAY_TYPEFACE_PATH = "fonts/raleway-regular-enhanced.ttf";

    private static final int[] EYE_COLORS = {
            Color.rgb(255, 102, 51),
            Color.rgb(0, 153, 255),
            Color.rgb(125, 114, 163),
            Color.rgb(215, 223, 35),
            Color.rgb(238, 42, 123),
            Color.rgb(0, 167, 157),
            Color.rgb(117, 76, 41),
            Color.rgb(141, 198, 63),
            Color.rgb(196, 154, 108),
            Color.rgb(128, 130, 133)
    };
    private static final int   EYE_COLOR_COUNT = EYE_COLORS.length;

    private static final int   NEW_EYE_EVERY_N_GLANCES = 1;
    private static final float BLINK_TO_GLANCE_CHANCE_RATIO = 0.10f;  // percent possibility of a blink event happening as compared to amount of glances
    private static final int   RESET_HOUR = 4;  // at which hour will watch face reset [0...23], -1 to deactivate
    private static final long  EYE_POPOUT_BASE_THRESHOLD = TimeUnit.SECONDS.toMillis(10);  // baseline threshold over which eyes will start popping out
    private static final long  EYE_POPOUT_PERIOD = TimeUnit.SECONDS.toMillis(5);  // beyond baseline, an eye will pop out every N millis

    // DEBUG
    private static final boolean DEBUG_ACCELERATE_INTERACTION = false;  // adds more eyes and blink factor per glance
    private static final int     DEBUG_EYES_PER_GLANCE = 5;
    private static final boolean DEBUG_SHOW_GLANCE_COUNTER = true;






    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private boolean mSensorAccelerometerIsRegistered;
    private float[] gravity = new float[2];
    private float screenRotation = 0;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            final float alpha = 0.8f;

            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];

            if (Math.abs(gravity[0]) > GRAVITY_THRESHOLD && Math.abs(gravity[1]) > GRAVITY_THRESHOLD) {
                screenRotation = alpha * screenRotation + (1 - alpha) * (float) (-(Math.atan2(gravity[1], gravity[0]) - QUARTER_TAU) * TO_DEGS);
//                Log.v("FOO", "Rotation: " + screenRotation);
            }
        }
    }




    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final int MSG_UPDATE_TIMER = 0;

        /* Handler to updateSize the screen depending on the message */
        final Handler mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
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
        private Time mCurrentGlance;
        private long mPrevGlance;

        private Paint mTextDigitsPaintInteractive, mTextDigitsPaintAmbient;
        private float mTextDigitsHeight, mTextDigitsBaselineHeight, mTextDigitsRightMargin;
        private Paint mTextGlancesPaintInteractive, mTextGlancesPaintAmbient;
        private float mTextGlancesHeight, mTextGlancesBaselineHeight, mTextGlancesRightMargin;
        private Typeface mTextTypeface;
        private final Rect textBounds = new Rect();

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private boolean mIsRound, mIsMoto360;


//        private Paint mLinePaint;
        private int glances = 0;  // how many times did the watch go from ambient to interactive?

        private EyeMosaic eyeMosaic;

        private AmbientManager ambientManager;



        @Override
        public void onCreate(SurfaceHolder holder) {
            Log.v(TAG, "onCreate");
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheBlinkieFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mTextTypeface = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    RALEWAY_TYPEFACE_PATH);

            mTextDigitsPaintInteractive = new Paint();
            mTextDigitsPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextDigitsPaintInteractive.setTypeface(mTextTypeface);
            mTextDigitsPaintInteractive.setTextAlign(Paint.Align.RIGHT);
            mTextDigitsPaintInteractive.setAntiAlias(true);

            mTextDigitsPaintAmbient = new Paint();
            mTextDigitsPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextDigitsPaintAmbient.setTypeface(mTextTypeface);
            mTextDigitsPaintAmbient.setTextAlign(Paint.Align.RIGHT);
            mTextDigitsPaintAmbient.setAntiAlias(false);

            mTextGlancesPaintInteractive = new Paint();
            mTextGlancesPaintInteractive.setColor(TEXT_GLANCES_COLOR_INTERACTIVE);
            mTextGlancesPaintInteractive.setTypeface(mTextTypeface);
            mTextGlancesPaintInteractive.setAntiAlias(true);
            mTextGlancesPaintInteractive.setTextAlign(Paint.Align.RIGHT);

            mTextGlancesPaintAmbient = new Paint();
            mTextGlancesPaintAmbient.setColor(TEXT_GLANCES_COLOR_AMBIENT);
            mTextGlancesPaintAmbient.setTypeface(mTextTypeface);
            mTextGlancesPaintAmbient.setAntiAlias(false);
            mTextGlancesPaintAmbient.setTextAlign(Paint.Align.RIGHT);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(TheBlinkieFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

            eyeMosaic = new EyeMosaic();
            eyeMosaic.addEye(39, 21, 49);
            eyeMosaic.addEye(39, 73, 49);
            eyeMosaic.addEye(82, 45, 49);
            eyeMosaic.addEye(158, 45, 72);
            eyeMosaic.addEye(106, 90, 72);
            eyeMosaic.addEye(218, 21, 49);
            eyeMosaic.addEye(267, 45, 49);
            eyeMosaic.addEye(218, 73, 49);
            eyeMosaic.addEye(54, 138, 97);
            eyeMosaic.addEye(139, 151, 49);
            eyeMosaic.addEye(106, 195, 72);
            eyeMosaic.addEye(39, 212, 49);
            eyeMosaic.addEye(82, 240, 49);
            eyeMosaic.addEye(39, 265, 49);
            eyeMosaic.addEye(106, 285, 72);
            eyeMosaic.addEye(201, 195, 97);
            eyeMosaic.addEye(282, 195, 49);
            eyeMosaic.addEye(253, 253, 72);
            eyeMosaic.addEye(185, 269, 49);
            eyeMosaic.addEye(228, 298, 49);

            mTime  = new Time();
            mCurrentGlance = new Time();
            mCurrentGlance.setToNow();
            mPrevGlance = mCurrentGlance.toMillis(false);

            registerScreenReceiver();
            ambientManager = new AmbientManager();
        }

        @Override
        public void onDestroy() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            mSensorManager.unregisterListener(TheBlinkieFaceService.this);
            unregisterTimeZoneReceiver();
            unregisterAccelerometerSensor();
            unregisterScreenReceiver();
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            Log.v(TAG, "onAmbientModeChanged: " + inAmbientMode);
            super.onAmbientModeChanged(inAmbientMode);

            ambientManager.ambientTick(inAmbientMode);

            if (inAmbientMode) {
                if (timelyReset()) {
                    Log.v(TAG, "Resetting watchface");
                    glances = 0;
                    eyeMosaic.reset();
                }
            }

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            if (mAmbient) {
                unregisterTimeZoneReceiver();
                unregisterAccelerometerSensor();


                mCurrentGlance.setToNow();
                mPrevGlance = mCurrentGlance.toMillis(false);

            } else {
                registerTimeZoneReceiver();
                registerAccelerometerSensor();

                glances++;

                mCurrentGlance.setToNow();
                long glanceDiff = mCurrentGlance.toMillis(false) - mPrevGlance;

//                Log.v(TAG, "Diff: " + glanceDiff);
//                Log.v(TAG, "Thresh: " + EYE_POPOUT_BASE_THRESHOLD);

                // Must eyes start popping out?
                if (glanceDiff > EYE_POPOUT_BASE_THRESHOLD) {
                    int popoutCount = (int) ((glanceDiff - EYE_POPOUT_BASE_THRESHOLD) / EYE_POPOUT_PERIOD);
//                    Log.v(TAG, "Out: " + popoutCount);

                    if (DEBUG_ACCELERATE_INTERACTION) {
                        eyeMosaic.deactivateRandomEye(popoutCount * DEBUG_EYES_PER_GLANCE);
//                        eyeMosaic.setBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO * glances * DEBUG_EYES_PER_GLANCE / NEW_EYE_EVERY_N_GLANCES);
                        eyeMosaic.increaseBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO * DEBUG_EYES_PER_GLANCE / NEW_EYE_EVERY_N_GLANCES);
                    } else {
                        eyeMosaic.deactivateRandomEye(popoutCount);
//                        eyeMosaic.setBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO * glances / NEW_EYE_EVERY_N_GLANCES);
                        eyeMosaic.increaseBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO / NEW_EYE_EVERY_N_GLANCES);
                    }


                // Or should they be added
                } else if (glances % NEW_EYE_EVERY_N_GLANCES == 0) {
                    if (DEBUG_ACCELERATE_INTERACTION) {
                        eyeMosaic.activateRandomEye(DEBUG_EYES_PER_GLANCE);
//                        eyeMosaic.setBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO * glances * DEBUG_EYES_PER_GLANCE / NEW_EYE_EVERY_N_GLANCES);
                        eyeMosaic.increaseBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO * DEBUG_EYES_PER_GLANCE / NEW_EYE_EVERY_N_GLANCES);
                    } else {
                        eyeMosaic.activateRandomEye(1);
//                        eyeMosaic.setBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO * glances / NEW_EYE_EVERY_N_GLANCES);
                        eyeMosaic.increaseBlinkChance(BLINK_TO_GLANCE_CHANCE_RATIO / NEW_EYE_EVERY_N_GLANCES);
                    }
                }

            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(TAG, "onSurfaceChanged: " + format + " " + width + " " + height);
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;
            mCenterX = 0.50f * mWidth;
            mCenterY = 0.50f * mHeight;

            mTextDigitsHeight = TEXT_DIGITS_HEIGHT * mHeight;
            mTextDigitsBaselineHeight = TEXT_DIGITS_BASELINE_HEIGHT * mHeight;
            mTextDigitsRightMargin = TEXT_DIGITS_RIGHT_MARGIN * mWidth;
            mTextDigitsPaintInteractive.setTextSize(mTextDigitsHeight);
            mTextDigitsPaintAmbient.setTextSize(mTextDigitsHeight);

            mTextGlancesHeight = TEXT_GLANCES_HEIGHT * mHeight;
            mTextGlancesBaselineHeight = TEXT_GLANCES_BASELINE_HEIGHT * mHeight;
            mTextGlancesRightMargin = TEXT_GLANCES_RIGHT_MARGIN * mWidth;
            mTextGlancesPaintInteractive.setTextSize(mTextGlancesHeight);
            mTextGlancesPaintAmbient.setTextSize(mTextGlancesHeight);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            Log.d(TAG, "onApplyWindowInsets");
            super.onApplyWindowInsets(insets);

            mIsRound = insets.isRound();
            mIsMoto360 = isMoto360();
            Log.v(TAG, "mIsRound? " + mIsRound);
            Log.v(TAG, "Is this a Moto360? " + mIsMoto360);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            Log.v(TAG, getDisplayState());

            mTime.setToNow();
            mHourInt = mTime.hour;
            mMinuteInt = mTime.minute;
            mTimeStr = (mHourInt % 12 == 0 ? 12 : mHourInt % 12) + ":" + String.format("%02d", mMinuteInt);

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT);

                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintAmbient);
                if (DEBUG_SHOW_GLANCE_COUNTER) canvas.drawText(Integer.toString(glances), mWidth - mTextGlancesRightMargin,
                        mTextGlancesBaselineHeight, mTextGlancesPaintAmbient);

            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);

                canvas.save();
                eyeMosaic.update();
                eyeMosaic.render(canvas);
                canvas.restore();

                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintInteractive);
                if (DEBUG_SHOW_GLANCE_COUNTER) canvas.drawText(Integer.toString(glances), mWidth - mTextGlancesRightMargin,
                        mTextGlancesBaselineHeight, mTextGlancesPaintInteractive);

            }



        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.v(TAG, "onVisibilityChanged: " + visible);

            super.onVisibilityChanged(visible);

            if (visible) {
                registerTimeZoneReceiver();
                registerAccelerometerSensor();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

            } else {
                unregisterTimeZoneReceiver();
                unregisterAccelerometerSensor();
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
                Log.v(TAG, "Received intent");
                if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    Log.v(TAG, "Screen ON");
//                    onAmbientModeChanged(false);
                    ambientManager.screenTick(true);
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.v(TAG, "Screen OFF");
//                    onAmbientModeChanged(true);
                    ambientManager.screenTick(false);
                }
            }
        };

        private void registerScreenReceiver() {
            Log.v(TAG, "ScreenReceiver registered");
            TheBlinkieFaceService.this.registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
            TheBlinkieFaceService.this.registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }

        private void unregisterScreenReceiver() {
            TheBlinkieFaceService.this.unregisterReceiver(mScreenReceiver);
        }

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
            TheBlinkieFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TheBlinkieFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private boolean registerAccelerometerSensor() {
            if (mSensorAccelerometerIsRegistered) return true;
            mSensorAccelerometerIsRegistered = mSensorManager.registerListener(
                    TheBlinkieFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            return mSensorAccelerometerIsRegistered;
        }

        private boolean unregisterAccelerometerSensor() {
            if (!mSensorAccelerometerIsRegistered) return false;
            mSensorManager.unregisterListener(TheBlinkieFaceService.this, mSensorAccelerometer);
            mSensorAccelerometerIsRegistered = false;
            return false;
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

        // http://stackoverflow.com/a/24969713/1934487
        private void drawTextVerticallyCentered(Canvas canvas, Paint paint, String text, float cx, float cy) {
            paint.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, cx, cy - textBounds.exactCenterY(), paint);
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


        private boolean isMoto360() {
            // Cannot rely on the width/height params passed to onSurfaceChanged,
            // because those are faking a virtual 320x320 screen...

            WindowManager wm = (WindowManager) TheBlinkieFaceService.this.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            Log.v(TAG, "isMoto360: " + mIsRound + " " + size.x + " " + size.y);
            return (mIsRound && size.x == 320 && size.y == 290);
        }

        private String getDisplayState() {
            int state = ((WindowManager) TheBlinkieFaceService.this.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getState();
            switch (state) {
                case Display.STATE_ON: return "STATE_ON";
                case Display.STATE_OFF: return "STATE_OFF";
                case Display.STATE_DOZE: return "STATE_DOZE";
                case Display.STATE_DOZE_SUSPEND: return "STATE_DOZE_SUSPEND";
                case Display.STATE_UNKNOWN: return "STATE_UNKNOWN";
                default: return "ELSE";
            }
        }





        class EyeMosaic {

//            static final float BLINK_PROBABILITY = 0.05f;  // 0 is never, 1 every frame
            float blinkChance;
            private static final int BLINK_CHANCE_FACTOR = 5;

            Eye[] eyes;
            int eyeCount;

            int activeEyesCount;
            List<Eye> activeEyes = new ArrayList<>();
            List<Eye> inactiveEyes = new ArrayList<>();
            List<Eye> updateList = new ArrayList<>();

            EyeMosaic() {
                eyes = new Eye[8];  // @TODO be programmatic here
                eyeCount = 0;
                activeEyesCount = 0;
                blinkChance = 0;
            }

            void update() {
                // trigger a random eye to blink
                if (Math.random() < blinkChance / (eyeCount * BLINK_CHANCE_FACTOR)) {
                    int id = (int) (activeEyesCount * Math.random());
                    activeEyes.get(id).blink();  // can affect an already blinking eye, but this is desired
                }

                for (Eye eye : updateList) {
                    eye.update();
                }

                // Unregister them from update list externally, to avoid iterator problems
                for (int i = updateList.size() - 1; i >= 0; i--) {
                    if (!updateList.get(i).needsUpdate) updateList.remove(updateList.get(i));
                }

            }

            void render(Canvas canvas) {
                for (Eye eye : activeEyes) {
                    eye.render(canvas);
                }
            }

            // Creates inactive eyes to be activated later
            void addEye(float x_, float y_, float width_) {
                eyes[eyeCount] = new Eye(this, eyeCount, x_, y_, width_);
                inactiveEyes.add(eyes[eyeCount]);
                eyeCount++;

                // double the array size if necessary
                if (eyeCount == eyes.length) {
                    eyes = Arrays.copyOf(eyes, 2 * eyes.length);
                }
            }

            void activateRandomEye(int count) {
                for (int i = 0; i < count; i++) {
                    if (activeEyesCount >= eyeCount) return; // if no more inactive eyes in the list
                    Eye eye = inactiveEyes.get((int) (inactiveEyes.size() * Math.random()));
                    eye.activate();
                    activeEyes.add(eye);
                    inactiveEyes.remove(eye);
                    activeEyesCount++;
                }
            }

            void deactivateRandomEye(int count) {
                for (int i = 0; i < count; i++) {
                    if (activeEyesCount <= 0) return;
                    Eye eye = activeEyes.get((int) (activeEyes.size() * Math.random()));
                    eye.deactivate();
                    inactiveEyes.add(eye);
                    activeEyes.remove(eye);
                    activeEyesCount--;
                }
            }

//            void setBlinkChance(float glances) {
////                blinkChance = glances / eyeCount;  //@TODO account for changes in blinkChance when an eye is added
//            }

            void increaseBlinkChance(float increment) {
                blinkChance += increment;
                if (blinkChance < 0) blinkChance = 0;
            }

            void openAll() {
                for (int i = 0; i < eyeCount; i++) {
                    eyes[i].open();
                }
            }

            void closeAll() {
                for (int i = 0; i < eyeCount; i++) {
                    eyes[i].close();
                }
            }

            void reset() {
                for (Eye eye : activeEyes) {
                    // @TODO improve this programmatically
                    eye.isActive = false;
                    eye.needsUpdate = false;
                    eye.blinking = false;
                    eye.currentAperture = 0;
                    eye.targetAperture = 0;
                    inactiveEyes.add(eye);
                }
                activeEyesCount = 0;
                blinkChance = 0;
                inactiveEyes.clear();
                updateList.clear();
            }

        }


        class Eye {
            // Constants
            final int   EYELID_COLOR = Color.rgb(235, 220, 220);
            static final int   PUPIL_COLOR = Color.BLACK;
            static final float BLINK_SPEED = 0.40f;
            static final int   ANIM_END_THRESHOLD = 1;  // pixel distance to stop animation
            static final float HEIGHT_RATIO = 0.68f;  // height/width ratio
            static final float IRIS_RATIO = 0.45f;  // irisDiameter/width ratio
            static final float PUPIL_RATIO = 0.29f;  // pupilDiameter/width ratio

            EyeMosaic parent;

            int id;
            float x, y;
            float width, height;
            float irisRadius, pupilRadius;
            int irisColor;

            Path eyelid;
            Paint eyelidPaint, irisPaint, pupilPaint;
            Paint eyeLinerPaint;  // @TODO make parent static or something

            boolean isActive;
            boolean blinking, needsUpdate;
            float currentAperture, targetAperture;

            Eye(EyeMosaic parent_, int id_, float x_, float y_, float width_) {
                parent = parent_;

                id = id_;
                x = x_;
                y = y_;
                width = width_;
                height = HEIGHT_RATIO * width;
                irisRadius = 0.5f * IRIS_RATIO * width;
                pupilRadius = 0.5f * PUPIL_RATIO * width;
                irisColor = randomColor();

                isActive = false;
                needsUpdate = false;
                blinking = false;
                currentAperture = 0;
                targetAperture = height;  // @TODO should this be 0?

                eyelidPaint = new Paint();
                eyelidPaint.setColor(EYELID_COLOR);
                eyelidPaint.setAntiAlias(true);

                irisPaint = new Paint();
                irisPaint.setColor(irisColor);
                irisPaint.setAntiAlias(true);

                pupilPaint = new Paint();
                pupilPaint.setColor(PUPIL_COLOR);
                pupilPaint.setAntiAlias(true);

                eyeLinerPaint = new Paint();
                eyeLinerPaint.setColor(EYELID_COLOR);
                eyeLinerPaint.setStyle(Paint.Style.STROKE);
                eyeLinerPaint.setStrokeWidth(2.0f);
                eyeLinerPaint.setAntiAlias(true);

                eyelid = new Path();
                resetEyelidPath();
            }

            void render(Canvas canvas) {
                canvas.save();
                canvas.translate(x, y);
                canvas.rotate(screenRotation);
                canvas.save();
                canvas.clipPath(eyelid);
                canvas.drawCircle(0, 0, 0.5f * width, eyelidPaint);
                canvas.drawCircle(0, 0, irisRadius, irisPaint);
                canvas.drawCircle(0, 0, pupilRadius, pupilPaint);
                canvas.restore();
                canvas.drawPath(eyelid, eyeLinerPaint);
                canvas.restore();
            }

            boolean update() {
                float diff = targetAperture - currentAperture;

                if (Math.abs(diff) < ANIM_END_THRESHOLD) {
                    currentAperture = targetAperture;
                } else {
                    currentAperture += BLINK_SPEED * (diff);
                }
                resetEyelidPath();

                // If completed an animation
                if (currentAperture == targetAperture) {
                    unregisterUpdate();

                    if (blinking) {
                        if (targetAperture == 0) {
                            open();  // restart animation (and blinking remains true)
                        } else {
                            blinking = false;
                        }
                    }
                }

                return needsUpdate;

        };
            void resetEyelidPath() {
                eyelid.rewind();
                eyelid.moveTo(-0.5f * width, 0);
                eyelid.quadTo(0, -currentAperture, 0.5f * width, 0);
                eyelid.quadTo(0,  currentAperture, -0.5f * width, 0);
                eyelid.close();
            }

            void activate() {
                isActive = true;
                open();
            }

            void deactivate() {
                isActive = false;
                needsUpdate = false;
                blinking = false;
                currentAperture = 0;
                targetAperture = height;  // @TODO should this be 0?
            }

            void open() {
                targetAperture = height;
                registerUpdate();
            }

            void close() {
                targetAperture = 0;
                registerUpdate();
            }

            void blink() {
                close();
                blinking = true;
            }

            int randomColor() {
                return EYE_COLORS[ (int) (EYE_COLOR_COUNT * Math.random()) ];
            }

            void registerUpdate() {
                if (!parent.updateList.contains(this)) {
                    parent.updateList.add(this);
                }
                needsUpdate = true;
            }

            void unregisterUpdate() {
                needsUpdate = false;
            }
        }


        private class AmbientManager {

            private Time time;
            private long lastAmbientTick, lastScreenTick;

            public boolean isAmbientScreenModeOn;

            AmbientManager() {
                time = new Time();
                time.setToNow();
                lastAmbientTick = time.toMillis(false);
                lastScreenTick = time.toMillis(false);
            }

            void ambientTick(boolean turnedAmbient) {
                Log.v(TAG, "ambientTick: " + turnedAmbient);

            }

            void screenTick(boolean turnedOn) {
                Log.v(TAG, "screenTick: " + turnedOn);

            }




        }


    }





}
