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
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.Arrays;


public class TheBlinkieFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "TheBlinkieFaceService";

    private static final float TAU = (float) (2 * Math.PI);

    private static final float QUARTER_TAU = TAU / 4;
    private static final float TO_DEGS = 360.0f / TAU;
    private static final float GRAVITY_THRESHOLD = 1.0f;

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

    private static final int BACKGROUND_COLOR_INTERACTIVE = Color.rgb(87, 88, 91);
    private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

    private static final int   TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int   TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
    private static final float TEXT_DIGITS_HEIGHT = 0.2f;  // as a factor of screen height
    private static final float TEXT_DIGITS_RIGHT_MARGIN = 0.1f;  // as a factor of screen width

    private static final String RALEWAY_TYPEFACE_PATH = "fonts/raleway-regular-enhanced.ttf";
    //    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    //    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

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
    private static final int EYE_COLOR_COUNT = EYE_COLORS.length;






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

        private Paint mTextPaintInteractive, mTextPaintAmbient;
        private float mTextHeight;
        private float mTextRightMargin;
        private final Rect textBounds = new Rect();
        private Typeface mTextTypeface;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

//        private Paint mLinePaint;
        private int glances = 0;  // how many times did the watch go from ambient to interactive?

        private EyeMosaic eyeMosaic;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheBlinkieFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mTextTypeface = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    RALEWAY_TYPEFACE_PATH);

            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(mTextTypeface);
            mTextPaintInteractive.setTextAlign(Paint.Align.RIGHT);
            mTextPaintInteractive.setAntiAlias(true);

            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(mTextTypeface);
            mTextPaintAmbient.setTextAlign(Paint.Align.RIGHT);
            mTextPaintAmbient.setAntiAlias(false);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(TheBlinkieFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);


            eyeMosaic = new EyeMosaic();
            eyeMosaic.addEye(0, 0, 100);

            eyeMosaic.addEye( 50, -40, 50);
            eyeMosaic.addEye(-50, -40, 50);
            eyeMosaic.addEye(-50,  40, 50);
            eyeMosaic.addEye( 50,  40, 50);

            eyeMosaic.addEye( 25, -60, 25);  // around topright eye
            eyeMosaic.addEye( 75, -60, 25);
            eyeMosaic.addEye( 75, -20, 25);

            eyeMosaic.addEye( 25,  60, 25);  // around bottomright eye
            eyeMosaic.addEye( 75,  60, 25);
            eyeMosaic.addEye( 75,  20, 25);

            eyeMosaic.addEye(-25,  60, 25);  // around bottomleft eye
            eyeMosaic.addEye(-75,  60, 25);
            eyeMosaic.addEye(-75,  20, 25);

            eyeMosaic.addEye(-25, -60, 25);  // around topleft eye
            eyeMosaic.addEye(-75, -60, 25);
            eyeMosaic.addEye(-75, -20, 25);

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
//                eyeMosaic.addEye();
                eyeMosaic.setBlinkChance(glances);
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
            mTextRightMargin = TEXT_DIGITS_RIGHT_MARGIN * mWidth;
            mTextPaintInteractive.setTextSize(mTextHeight);
            mTextPaintAmbient.setTextSize(mTextHeight);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

//            final String hours = String.format("%02d", mTime.hour % 12 == 0 ? 12 : mTime.hour % 12);
//            final String minutes = String.format("%02d", mTime.minute);

            final String time = (mTime.hour % 12 == 0 ? 12 : mTime.hour % 12) + ":" + String.format("%02d", mTime.minute);


            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT);
//                canvas.drawText(time, mWidth - 0.5f * mTextHeight, 1.5f * mTextHeight, mTextPaintAmbient);  // @TODO: be screen programmatic here

                drawTextVerticallyCentered(canvas, mTextPaintAmbient, time,
                        mWidth - mTextRightMargin, 0.33f * mHeight);

            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);
//                canvas.drawText(time, mWidth - 0.5f * mTextHeight, 1.5f * mTextHeight, mTextPaintInteractive);  // @TODO: be screen programmatic here

                canvas.save();
                canvas.translate(mCenterX, mCenterY);
//                canvas.rotate(screenRotation);
                canvas.scale(1.5f, 1.5f);
                eyeMosaic.update();
                eyeMosaic.render(canvas);
                canvas.restore();

                drawTextVerticallyCentered(canvas, mTextPaintAmbient, time,
                        mWidth - mTextRightMargin, 0.33f * mHeight);
            }

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

//            static final float BLINK_PROBABILITY = 0.05f;  // 0 is never, 1 every frame
            float blinkChance;

            Eye[] eyes;
            int eyeCount;

            List<Eye> updateList = new ArrayList<>();

            EyeMosaic() {
                eyes = new Eye[8];
                eyeCount = 0;
                blinkChance = 0;
            }

            void update() {

                // trigger a random eye to blink
                if (Math.random() < blinkChance) {
                    int id = (int) (eyeCount * Math.random());  //@TODO change to always hitting an eye to blink?
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


            void addEye(float x_, float y_, float width_) {
                eyes[eyeCount++] = new Eye(eyeCount, x_, y_, width_);

                // double the array size if necessary
                if (eyeCount == eyes.length) {
                    eyes = Arrays.copyOf(eyes, 2 * eyes.length);
                }
            }


            void setBlinkChance(float glances) {
                blinkChance = glances / eyeCount;  //@TODO account for changes in blinkChance when an eye is added
            }

        }


        class Eye {
            // Constants
            static final int   EYELID_COLOR = Color.WHITE;
            static final int   IRIS_COLOR = Color.BLACK;
            static final float BLINK_SPEED = 0.25f;
            static final int   BLINK_END_THRESHOLD = 2;  // pixel distance to stop blink
            static final float HEIGHT_RATIO = 0.68f;  // height/width ratio
            static final float PUPIL_RATIO = 0.45f;  // pupilDiameter/width ratio
            static final float IRIS_RATIO = 0.29f;  // irisDiam/width ratio

            int id;
            float x, y;
            float width, height;
            float pupilR, irisR;
            int pupilColor;

            Path eyelid;
            Paint eyelidPaint, pupilPaint, irisPaint;

            boolean blinking;
            float currentAperture, targetAperture;

            Eye(int id_, float x_, float y_, float width_) {
                id = id_;
                x = x_;
                y = y_;
                width = width_;
                height = HEIGHT_RATIO * width;
                pupilR = 0.5f * PUPIL_RATIO * width;
                irisR = 0.5f * IRIS_RATIO * width;
                pupilColor = randomColor();

                eyelidPaint = new Paint();
                eyelidPaint.setColor(EYELID_COLOR);
                eyelidPaint.setAntiAlias(true);

                pupilPaint = new Paint();
                pupilPaint.setColor(pupilColor);
                pupilPaint.setAntiAlias(true);

                irisPaint = new Paint();
                irisPaint.setColor(IRIS_COLOR);
                irisPaint.setAntiAlias(true);

                eyelid = new Path();
                resetEyelid(height);
            }

            void render(Canvas canvas) {
                canvas.save();
                canvas.translate(x, y);
                canvas.rotate(screenRotation);
                canvas.drawPath(eyelid, eyelidPaint);
                canvas.drawCircle(0, 0, pupilR, pupilPaint);
                canvas.drawCircle(0, 0, irisR, irisPaint);
                canvas.restore();
            }

            boolean update() {
                float diff = targetAperture - currentAperture;
                if (Math.abs(diff) < BLINK_END_THRESHOLD) {
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

                } else {
                    resetEyelid(height);
                    blinking = false;
                }
            }

            void resetEyelid(float newHeight) {
                eyelid.rewind();
                eyelid.moveTo(-0.5f * width, 0);
                eyelid.quadTo(0, -newHeight,  0.5f * width, 0);
                eyelid.quadTo(0,  newHeight, -0.5f * width, 0);
                eyelid.close();
            }

            void blink() {
                currentAperture = height;
                targetAperture = 0;
                blinking = true;
            }

            int randomColor() {
                return EYE_COLORS[ (int) (EYE_COLOR_COUNT * Math.random()) ];
            }
        }

    }





}
