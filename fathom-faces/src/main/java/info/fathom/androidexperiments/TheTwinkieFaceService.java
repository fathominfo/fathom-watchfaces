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

import java.util.TimeZone;
import java.util.Arrays;


public class TheTwinkieFaceService extends CanvasWatchFaceService implements SensorEventListener {
    private static final String TAG = "TheTwinkieFaceService";

    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We updateSize once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;


    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private boolean mSensorAccelerometerIsRegistered;
    private float[] gravity = new float[3];

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // In this example, alpha is calculated as t / (t + dT),
            // where t is the low-pass filter's time-constant and
            // dT is the event delivery rate.
            final float alpha = 0.8f;

            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

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

//        private static final int BACKGROUND_COLOR_INTERACTIVE = Color.BLACK;
        private final int BACKGROUND_COLOR_INTERACTIVE = Color.rgb(240, 78, 35);  // orange
        private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

        private static final int   TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
        private static final int   TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_DIGITS_HEIGHT = 0.2f;  // as a factor of screen height

        private static final int RESET_CRACK_THRESHOLD = 5;  // very nth glance, cracks will be reset (0 makes does no resetting)


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

//        private Ball ball;
        private Board board;

        private int glances = 0;  // how many times did the watch go from ambient to interactive?






        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheTwinkieFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(NORMAL_TYPEFACE);
            mTextPaintInteractive.setAntiAlias(true);

            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(NORMAL_TYPEFACE);
            mTextPaintAmbient.setAntiAlias(false);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(TheTwinkieFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

//            ball = new Ball();
            board = new Board();

            mTime  = new Time();
        }

        @Override
        public void onDestroy() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            mSensorManager.unregisterListener(TheTwinkieFaceService.this);
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

                if (RESET_CRACK_THRESHOLD > 0) {
                    if (glances % RESET_CRACK_THRESHOLD == 0) board.resetBoard();
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
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

//            ball.initialize();
            board.initialize(mWidth, mHeight);

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

                board.render(canvas, true);

                mTextPaintAmbient.setTextAlign(Paint.Align.RIGHT);
                drawTextVerticallyCentered(canvas, mTextPaintAmbient, hours, mCenterX - 20, mCenterY);  // @TODO: be screen programmatic here
                mTextPaintAmbient.setTextAlign(Paint.Align.LEFT);
                drawTextVerticallyCentered(canvas, mTextPaintAmbient, minutes, mCenterX + 20, mCenterY);

            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);

                board.update();
                board.render(canvas, false);

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
            TheTwinkieFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TheTwinkieFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private boolean registerAccelerometerSensor() {
            if (mSensorAccelerometerIsRegistered) return true;
            mSensorAccelerometerIsRegistered = mSensorManager.registerListener(
                    TheTwinkieFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            return mSensorAccelerometerIsRegistered;
        }

        private boolean unregisterAccelerometerSensor() {
            if (!mSensorAccelerometerIsRegistered) return false;
            mSensorManager.unregisterListener(TheTwinkieFaceService.this, mSensorAccelerometer);
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



        class Ball {

            private static final float FRICTION = 0.97f;
            private static final float ACCEL_FACTOR = 0.25f;
            private static final float RADIUS_FACTOR = 0.03f;     // as a ratio to screen width

            private static final int COLOR = Color.WHITE;

            Board parent;
            float x, y, r;
            float velX, velY;
            Paint paint;

            Ball(Board parent_) {
                parent = parent_;

                x = 0.5f * parent.width;
                y = 0.5f * parent.height;
                r = RADIUS_FACTOR * parent.width;
                velX = velY = 0;

                paint = new Paint();
                paint.setColor(COLOR);
                paint.setAntiAlias(true);
            }

            void update() {
                velX += ACCEL_FACTOR * -gravity[0];
                velY += ACCEL_FACTOR * gravity[1];

                velX *= FRICTION;
                velY *= FRICTION;

                x += velX;
                y += velY;


                // @TODO fix case where ball is out of two bounds simultaneously (bounce coords override)
                // @TODO in general, make this check more programmatic
                boolean bounce = false;
                int bounceX = 0, bounceY = 0;
                if (x > parent.width) {
                    bounce = true;
                    bounceX = parent.width;
                    bounceY = Math.round( y - velY + velY * x / parent.width );  // proportional height at bounce
                    x = parent.width - (x - parent.width);
                    velX = -velX;

                } else if (x < 0) {
                    bounce = true;
                    bounceX = 0;
                    bounceY = Math.round( y - velY + velY * x / parent.width );  // proportional height at bounce
                    x = -x;
                    velX = -velX;
                }

                if (y > mHeight) {
                    bounce = true;
                    bounceX = Math.round( x - velX + velX * y / parent.height );  // proportional height at bounce
                    bounceY = parent.height;
                    y = mHeight - (y - mHeight);
                    velY = -velY;

                } else if (y < 0) {
                    bounce = true;
                    bounceX = Math.round( x - velX + velX * y / parent.height );  // proportional height at bounce
                    bounceY = 0;
                    y = -y;
                    velY = -velY;
                }

                if (bounce) {
                    parent.addBounce(bounceX, bounceY);
                }



            }

            void render(Canvas canvas) {
                canvas.drawCircle(x, y, r, paint);
            }
        }



        class Board {

            static final boolean AVOID_DUPLICATE_SIDES = true;

            int width, height;
            Ball ball;

            Bounce[] bounces;
            Triangle[] triangles;
            int bounceCount, triangleCount;

            Paint linePaint;
            Paint trianglePaint;

            Board() {}

            void initialize(int screenW, int  screenH) {
                width = screenW;
                height = screenH;
                ball = new Ball(this);

                bounces = new Bounce[4];
                bounceCount = 0;
                triangles = new Triangle[4];
                triangleCount = 0;

                linePaint = new Paint();
                linePaint.setColor(Color.GRAY);
                linePaint.setStrokeWidth(1.0f);
                linePaint.setAntiAlias(false);
                linePaint.setStrokeCap(Paint.Cap.ROUND);

                trianglePaint = new Paint();
                trianglePaint.setColor(Color.WHITE);
                trianglePaint.setStyle(Paint.Style.FILL);
                trianglePaint.setAntiAlias(true);
//                trianglePaint.setStrokeWidth(1.0f);
//                trianglePaint.setStrokeCap(Paint.Cap.ROUND);

            }

            void update() {
                ball.update();
            }

            void render(Canvas canvas, boolean ambientMode) {
                // @TODO background is drawn before this call, change this at some point

                if (ambientMode) {
                    for (int i = 0; i < bounceCount - 1; i++) {
                        canvas.drawLine(bounces[i].x, bounces[i].y,
                                bounces[i + 1].x, bounces[i + 1].y, linePaint);
                    }

                } else {
                    for (int i = 0; i < triangleCount; i++) {
                        triangles[i].render(canvas, trianglePaint);
                    }
                    ball.render(canvas);
                }
            }

            void addBounce(int xpos, int ypos) {
                Bounce bounce = new Bounce(xpos, ypos);

                // If repeating bouncing side, do not add it to the list
                if (AVOID_DUPLICATE_SIDES
                        && bounceCount > 0
                        && bounce.side == bounces[bounceCount - 1].side) {
                    return;
                }

                // Otherwise, add it to the array
                bounces[bounceCount++] = bounce;

                if (bounceCount > 2) {
                    triangles[triangleCount++] = new Triangle(bounces[bounceCount - 3],
                            bounces[bounceCount - 2], bounces[bounceCount - 1]);
                }

                // Double the side of the arrays if necessary
                if (bounceCount >= bounces.length) {
                    bounces = Arrays.copyOf(bounces, 2 * bounces.length);
                }
                if (triangleCount >= triangles.length) {
                    triangles = Arrays.copyOf(triangles, 2 * triangles.length);
                }
            }

            void resetBoard() {
                bounces = new Bounce[4];
                bounceCount = 0;
            }

        }







        class Triangle {
            Bounce start, middle, end;
            Path path;
            int color;

            Triangle(Bounce start_, Bounce middle_, Bounce end_) {
                start = start_;
                middle = middle_;
                end = end_;

                color = end.color;

                path = new Path();
                path.moveTo(start.x, start.y);
                path.lineTo(middle.x, middle.y);
                path.lineTo(end.x, end.y);

            }

            void render(Canvas canvas, Paint paint) {
                paint.setColor(color);
                canvas.drawPath(path, paint);
            }

        }



        class Bounce {
            int x, y;
            int side;  // 0 for top... 3 for left (clockwise)
            int color;

            Bounce(int x_, int y_) {
                x = x_;
                y = y_;

                if (x == 0)            side = 3;
                else if (x == mWidth)  side = 1;
                else if (y == 0)       side = 0;
                else if (y == mHeight) side = 2;

                newColor();
            }

            void newColor() {
                color = Color.argb(63,
                        (int) (255 * Math.random()),
                        (int) (255 * Math.random()),
                        (int) (255 * Math.random())
                        );
            }

        }

    }



}