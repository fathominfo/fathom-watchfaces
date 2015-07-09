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
import android.util.Log;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class TheDingDongFaceService extends CanvasWatchFaceService implements SensorEventListener {
    private static final String TAG = "TheDingDongFaceService";

    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;



    // STEP SENSING
    private SensorManager mSensorManager;
    private Sensor mStepSensor;
    private boolean mStepSensorIsRegistered;
    private int mPrevSteps = 0;
    private int mCurrentSteps = 0;
    private static final boolean GENERATE_FAKE_STEPS = true;
    private static final int RANDOM_FAKE_STEPS = 1500;
    private static final int MAX_STEP_THRESHOLD = 10000;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            if (!GENERATE_FAKE_STEPS) {
                Log.i(TAG, "New step count: " + Float.toString(event.values[0]));
                mCurrentSteps = Math.round(event.values[0]);
            }
        }
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
        private static final float TEXT_DIGITS_HEIGHT = 0.2f;  // as a factor of screen height

        private boolean mRegisteredTimeZoneReceiver = false;
        //        private boolean mLowBitAmbient;
        //        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Time mTime;

        private Paint mTextPaintInteractive, mTextPaintAmbient;
        private float mTextHeight;
        private final Rect textBounds = new Rect();


        private BubbleManager bubbleManager;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;




        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheDingDongFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            /**
             * STEP SENSING
             */
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(NORMAL_TYPEFACE);
            mTextPaintInteractive.setAntiAlias(true);

            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(NORMAL_TYPEFACE);
            mTextPaintAmbient.setAntiAlias(false);

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

            if (!mAmbient) {
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

//            int newBubbles = stepInc / BUBBLE_SMALL_STEP_RATIO;  // @TODO implement something that accounts for the remainder of this division
//            Log.v(TAG, "Adding " + newBubbles + " bubbles");
//
//            for (int i = 0; i < newBubbles; i++) {
//                bubbles.add(new Bubble(5, mBublePaintSmall));
//            }

            bubbleManager.updateSteps(mCurrentSteps);

        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

            mTextHeight = TEXT_DIGITS_HEIGHT * mHeight;
            mTextPaintInteractive.setTextSize(mTextHeight);
            mTextPaintAmbient.setTextSize(mTextHeight);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

            final String hours = String.format("%02d", mTime.hour % 12 == 0 ? 12 : mTime.hour % 12);
            final String minutes = String.format("%02d", mTime.minute);

            // Start drawing watch elements
            canvas.save();

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT); // background

                mTextPaintAmbient.setTextAlign(Paint.Align.RIGHT);
                drawTextVerticallyCentered(canvas, mTextPaintAmbient, hours, mCenterX - 20, mCenterY);  // @TODO: be screen programmatic here
                mTextPaintAmbient.setTextAlign(Paint.Align.LEFT);
                drawTextVerticallyCentered(canvas, mTextPaintAmbient, minutes, mCenterX + 20, mCenterY);

            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);

                // draw bubbles
                bubbleManager.render(canvas);

                mTextPaintInteractive.setTextAlign(Paint.Align.RIGHT);
                drawTextVerticallyCentered(canvas, mTextPaintInteractive, hours, mCenterX - 20, mCenterY);
                mTextPaintInteractive.setTextAlign(Paint.Align.LEFT);
                drawTextVerticallyCentered(canvas, mTextPaintInteractive, minutes, mCenterX + 20, mCenterY);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerTimeReceiver();
                registerStepSensor();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterTimeReceiver();
                unregisterStepSensor();
            }

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
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
            mSensorManager.unregisterListener(TheDingDongFaceService.this);
        }




        private void updateTimer() {
            mMainHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mMainHandler.sendEmptyMessage(R.id.message_update);
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




        private class BubbleManager {

            private final static int STEP_RATIO_BIG = 1000;     // a BIG bubble represents this many steps
            private final static int STEP_RATIO_MEDIUM = 100;
            private final static int STEP_RATIO_SMALL = 10;

            private final static int RADIUS_BIG = 15;
            private final static int RADIUS_MEDIUM = 10;
            private final static int RADIUS_SMALL = 5;

            private final int COLOR_BIG = Color.argb(200, 86, 40, 38);  // mustard
            private final int COLOR_MEDIUM = Color.argb(200, 36, 66, 80);  // dark blue
            private final int COLOR_SMALL = Color.argb(200, 200, 189, 8);  // mustard
//            private final int COLOR_SMALL = Color.argb(200, 255, 219, 88);  // mustard

            private BubbleCollection bubblesBig, bubblesMedium, bubblesSmall;
            private Paint paintBig, paintMedium, paintSmall;
            private int prevSteps, currentSteps;

            BubbleManager() {
                paintBig = new Paint();
                paintBig.setColor(COLOR_BIG);
                paintBig.setAntiAlias(true);

                paintMedium = new Paint();
                paintMedium.setColor(COLOR_MEDIUM);
                paintMedium.setAntiAlias(true);

                paintSmall = new Paint();
                paintSmall.setColor(COLOR_SMALL);
                paintSmall.setAntiAlias(true);

                bubblesBig = new BubbleCollection(RADIUS_BIG, paintBig);
                bubblesMedium = new BubbleCollection(RADIUS_MEDIUM, paintMedium);
                bubblesSmall = new BubbleCollection(RADIUS_SMALL, paintSmall);

                prevSteps = 0;
                currentSteps = 0;
            }

            public void render(Canvas canvas) {
                bubblesBig.render(canvas);
                bubblesMedium.render(canvas);
                bubblesSmall.render(canvas);
            }

            public void updateSteps(int currentSteps_) {
                int stepInc = currentSteps_ - currentSteps;
                prevSteps = currentSteps;
                currentSteps = currentSteps_;

                this.addSteps(stepInc);
            }

            public void addSteps(int newSteps) {

                bubblesBig.add(newSteps / STEP_RATIO_BIG);
                bubblesMedium.add(newSteps / STEP_RATIO_MEDIUM);
                bubblesSmall.add(newSteps / STEP_RATIO_SMALL);

                Log.v(TAG, "Adding " + newSteps + " steps");
            }

        }

        private class BubbleCollection {

            List<Bubble> bubbles;
            Paint paint;
            float radius;
            int count;

            BubbleCollection(float radius_, Paint paint_) {
                radius = radius_;
                paint = paint_;
                bubbles = new ArrayList<>();
                count = 0;
            }

            public void render(Canvas canvas) {
                for (Bubble bub : bubbles) {
                    bub.render(canvas);
                }
            }

            public void add(int count_) {
                for (int i = 0; i < count_; i++) {
                    bubbles.add(new Bubble(radius, paint));
                }
                count += count_;
            }

            public void remove(int count_) {
                for (int i = 0; i < count_; i++) {
                    bubbles.remove(0);
                }
                count -= count_;
            }

        }

        private class Bubble {
            float x, y;
            float radius;
            Paint paint;

            Bubble(float radius_, Paint paint_) {
                x = (float) (mWidth * Math.random());
                y = (float) (mHeight * Math.random());
                radius = radius_;
                paint = paint_;
            }

            public void render(Canvas canvas) {
                canvas.drawCircle(x, y, radius, paint);
            }
        }

    }

}