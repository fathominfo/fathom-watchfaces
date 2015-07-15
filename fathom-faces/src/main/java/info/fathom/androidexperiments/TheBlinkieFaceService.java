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

    private static final String TAG = "TheBlinkieFaceService";

    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final float TAU = (float) (2 * Math.PI);
    private static final float QUARTER_TAU = TAU / 4;
    private static final float TO_DEGS = 360.0f / TAU;

    private static final float GRAVITY_THRESHOLD = 1.0f;

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
//        private Eye eyeBig, eyeSmall;
        private EyeMosaic eyeMosaic;

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

//            eyeBig = new Eye(0, 0, 100, 50, 1);
//            eyeSmall = new Eye(50, -50, 50, 25, 1);
            eyeMosaic = new EyeMosaic();
            eyeMosaic.addEye(0, 0, 100, 50, 1);
            eyeMosaic.addEye(50, -50, 50, 25, 1);

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
                eyeMosaic.addEye();

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
//                eyeBig.render(canvas);
//                eyeSmall.render(canvas);
                eyeMosaic.update();
                eyeMosaic.render(canvas);
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


        class EyeMosaic {

            static final float BLINK_PROBABILITY = 0.05f;  // 0 is never, 1 every frame

            Eye[] eyes;
            int eyeCount;

            List<Eye> updateList = new ArrayList<>();

            EyeMosaic() {
                eyes = new Eye[8];
                eyeCount = 0;
            }

            void update() {

                // trigger a random eye to blink
                if (Math.random() < BLINK_PROBABILITY) {
                    int id = (int) (eyeCount * Math.random());
                    if (!eyes[id].blinking) {
                        eyes[id].blink();
                        updateList.add(eyes[id]);
                    }
                }

                for (Eye eye : updateList) {
//                    Log.v(TAG, "Updating eye " + eye.id);
                    eye.update();
                }

                for (int i = updateList.size() - 1; i >= 0; i--) {
                    if (!updateList.get(i).blinking) updateList.remove(updateList.get(i));
                }

            }

            void render(Canvas canvas) {
                for (int i = 0; i < eyeCount; i++) {
                    eyes[i].render(canvas);
                }
            }


            void addEye(float x_, float y_, float width_, float height_, float scale_) {
                eyes[eyeCount++] = new Eye(eyeCount, x_, y_, width_, height_, scale_);

                // double the array size if necessary
                if (eyeCount == eyes.length) {
                    eyes = Arrays.copyOf(eyes, 2 * eyes.length);
                }
            }

            void addEye() {
                // add some algorithm to compute new eye position and sizes
                float w = (float) (50 + 50 * Math.random());
                eyes[eyeCount++] = new Eye(
                        eyeCount,
                        (float) (-110 + 220 * Math.random()),
                        (float) (-110 + 220 * Math.random()),
                        w,
                        0.5f * w,
                        1
                );

                // double the array size if necessary
                if (eyeCount == eyes.length) {
                    eyes = Arrays.copyOf(eyes, 2 * eyes.length);
                }
            }


        }


        class Eye {

            static final int EYELID_COLOR = Color.GRAY;
            static final float BLINK_SPEED = 0.25f;
            static final int END_THRESHOLD = 2;

            int id;
            float x, y;
            float width, height;
            float scale;
            Path eyelid;
            Paint eyelidPaint, corneaPaint, irisPaint;

            boolean blinking;
            float currentAperture, targetAperture;

            Eye(int id_, float x_, float y_, float width_, float height_, float scale_) {
                id = id_;
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
                resetEyelid(height);
            }

            void render(Canvas canvas) {
                canvas.drawPath(eyelid, eyelidPaint);
                canvas.drawCircle(x, y, 0.375f * height, corneaPaint);
                canvas.drawCircle(x, y, 0.187f * height, irisPaint);
            }

            boolean update() {
                float diff = targetAperture - currentAperture;
                if (Math.abs(diff) < END_THRESHOLD) {
                    switchBlink();
                    return blinking;
                }
                currentAperture += BLINK_SPEED * (diff);
                resetEyelid(currentAperture);
                return blinking;
            }

            void switchBlink() {
                // if eye was closing
                if (targetAperture == 0) {
                    targetAperture = height;
//                    blinking = true;

                } else {
                    resetEyelid(height);
                    blinking = false;
                }
            }

            void resetEyelid(float newHeight) {
                eyelid.rewind();
                eyelid.moveTo(x - 0.5f * width, y);
                eyelid.quadTo(x, y - newHeight, x + 0.5f * width, y);
                eyelid.quadTo(x, y + newHeight, x - 0.5f * width, y);
                eyelid.close();
            }


            void blink() {
                currentAperture = height;
                targetAperture = 0;
                blinking = true;
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
