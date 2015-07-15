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

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.Arrays;


public class TheBlinkieFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final float TAU = (float) (2 * Math.PI);
    private static final float QUARTER_TAU = TAU / 4;
    private static final float TO_DEGS = 360.0f / TAU;

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

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

//            screenRotation = (float) (-(Math.atan2(gravity[1], gravity[0]) - QUARTER_TAU) * TO_DEGS);
            screenRotation = alpha * screenRotation + (1 - alpha) * (float) (-(Math.atan2(gravity[1], gravity[0]) - QUARTER_TAU) * TO_DEGS);
            Log.v("FOO", "Rotation: " + screenRotation);
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


        private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
        private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

        private static final int   TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
        private static final int   TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_DIGITS_HEIGHT = 0.13f;  // as a factor of screen height


        private boolean mRegisteredTimeZoneReceiver = false;
        //        private boolean mLowBitAmbient;
        //        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Time mTime;

        private Paint mTextPaintInteractive, mTextPaintAmbient;
        private float mTextHeight;
        private final Rect textBounds = new Rect();

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

//        private Paint mLinePaint;
        private int glances = 0;  // how many times did the watch go from ambient to interactive?
        private Eye eyeBig, eyeSmall;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheBlinkieFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(NORMAL_TYPEFACE);
            mTextPaintInteractive.setTextAlign(Paint.Align.RIGHT);
            mTextPaintInteractive.setAntiAlias(true);

            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(NORMAL_TYPEFACE);
            mTextPaintAmbient.setTextAlign(Paint.Align.RIGHT);
            mTextPaintAmbient.setAntiAlias(false);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(TheBlinkieFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

            eyeBig = new Eye(0, 0, 100, 50, 1);
            eyeSmall = new Eye(50, -50, 50, 25, 1);

            mTime  = new Time();
        }

        @Override
        public void onDestroy() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            mSensorManager.unregisterListener(TheBlinkieFaceService.this);
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
                unregisterTimeZoneReceiver();
                unregisterAccelerometerSensor();

            } else {
                registerTimeZoneReceiver();
                registerAccelerometerSensor();

                glances++;

            }

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

            mTextHeight = TEXT_DIGITS_HEIGHT * mHeight;
            mTextPaintInteractive.setTextSize(mTextHeight);
            mTextPaintAmbient.setTextSize(mTextHeight);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

//            final String hours = String.format("%02d", mTime.hour % 12 == 0 ? 12 : mTime.hour % 12);
//            final String minutes = String.format("%02d", mTime.minute);

            final String time = (mTime.hour % 12 == 0 ? 12 : mTime.hour % 12) + ":" + String.format("%02d", mTime.minute);

            // Start drawing watch elements
            canvas.save();

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT);
                canvas.drawText(time, mWidth - 0.5f * mTextHeight, 1.5f * mTextHeight, mTextPaintAmbient);  // @TODO: be screen programmatic here

            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);
                canvas.drawText(time, mWidth - 0.5f * mTextHeight, 1.5f * mTextHeight, mTextPaintInteractive);  // @TODO: be screen programmatic here

                canvas.translate(mCenterX, mCenterY);
                canvas.rotate(screenRotation);
//                canvas.drawLine(0, 0, 0, 100, mLinePaint);
//                canvas.drawLine(-50, 0, 50, 0, mLinePaint);
                eyeBig.render(canvas);
                eyeSmall.render(canvas);
            }

            canvas.restore();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
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





        class Eye {

            static final int EYELID_COLOR = Color.GRAY;

            float x, y;
            float width, height;
            float scale;
            Path eyelid;
            Paint eyelidPaint, corneaPaint, irisPaint;

            Eye(float x_, float y_, float width_, float height_, float scale_) {
                x = x_;
                y = y_;
                width = width_;
                height = height_;
                scale = scale_;

                eyelidPaint = new Paint();
                eyelidPaint.setColor(EYELID_COLOR);
                eyelidPaint.setAntiAlias(true);

                corneaPaint = new Paint();
                corneaPaint.setColor(BACKGROUND_COLOR_INTERACTIVE);
                corneaPaint.setAntiAlias(true);

                irisPaint = new Paint();
                irisPaint.setColor(randomColor());
                irisPaint.setAntiAlias(true);

                eyelid = new Path();
                eyelid.moveTo(x - 0.5f * width, y);
                eyelid.quadTo(x, y - height, x + 0.5f * width, y);
                eyelid.quadTo(x, y + height, x - 0.5f * width, y);
                eyelid.close();
            }

            void render(Canvas canvas) {
                canvas.drawPath(eyelid, eyelidPaint);
                canvas.drawCircle(x, y, 0.375f * height, corneaPaint);
                canvas.drawCircle(x, y, 0.187f * height, irisPaint);
            }

            int randomColor() {
                return Color.rgb(
                        (int) (255 * Math.random()),
                        (int) (255 * Math.random()),
                        (int) (255 * Math.random())
                );
            }
        }

    }





}
