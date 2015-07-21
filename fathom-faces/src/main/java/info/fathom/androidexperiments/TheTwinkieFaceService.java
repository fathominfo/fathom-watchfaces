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
import java.util.concurrent.TimeUnit;


public class TheTwinkieFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "TheTwinkieFaceService";
    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final long INACTIVITY_RESET_TIME = TimeUnit.HOURS.toMillis(5);

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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
            // gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

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
//        private final int BACKGROUND_COLOR_INTERACTIVE = Color.rgb(240, 78, 35);  // orange
        private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;
        private final int[] backgroundColors = new int[24];
        private final static int COLOR_TRIANGLE_ALPHA = 63;
        private final static int CURSOR_TRIANGLE_ALPHA = 127;

        private static final int   TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
        private static final int   TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_DIGITS_HEIGHT = 0.2f;  // as a factor of screen height
        private static final float TEXT_DIGITS_RIGHT_MARGIN = 0.1f;  // as a factor of screen width

        // DEBUG
        private static final int     RESET_CRACK_THRESHOLD = 5;  // every nth glance, cracks will be reset (0 does no resetting)
        private static final boolean NEW_HOUR_PER_GLANCE = true;  // this will add an hour to the time at each glance
        private static final boolean DRAW_BALL = false;
        private static final boolean USE_TRIANGLE_CURSOR = true;
        private static final boolean TRIANGLES_ANIMATE_VERTEX_ON_CREATION = true;
        private static final boolean TRIANGLES_ANIMATE_COLOR_ON_CREATION = true;




        private boolean mRegisteredTimeZoneReceiver = false;
//        private boolean mLowBitAmbient;
//        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Time mTime;
        private Time mCurrentGlance, mPrevGlance;

        private Paint mTextPaintInteractive, mTextPaintAmbient;
        private float mTextHeight;
        private float mTextRightMargin;
        private final Rect textBounds = new Rect();
        private Typeface RALEWAY_REGULAR_TYPEFACE;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

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

            RALEWAY_REGULAR_TYPEFACE = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    "fonts/raleway-regular.ttf");

            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(RALEWAY_REGULAR_TYPEFACE);
            mTextPaintInteractive.setTextAlign(Paint.Align.RIGHT);
            mTextPaintInteractive.setAntiAlias(true);

            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(RALEWAY_REGULAR_TYPEFACE);
            mTextPaintAmbient.setTextAlign(Paint.Align.RIGHT);
            mTextPaintAmbient.setAntiAlias(false);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(TheTwinkieFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

            board = new Board();

            mTime  = new Time();
            mCurrentGlance = new Time();
            mCurrentGlance.setToNow();
            mPrevGlance = new Time();
            mPrevGlance.setToNow();

            // Initialize hardcoded day colors
            backgroundColors[0] = Color.rgb(0, 85, 255);
            backgroundColors[1] = Color.rgb(0, 21, 255);
            backgroundColors[2] = Color.rgb(42, 0, 255);
            backgroundColors[3] = Color.rgb(106, 0, 255);
            backgroundColors[4] = Color.rgb(170, 0, 255);
            backgroundColors[5] = Color.rgb(234, 0, 255);
            backgroundColors[6] = Color.rgb(255, 0, 212);
            backgroundColors[7] = Color.rgb(255, 0, 149);
            backgroundColors[8] = Color.rgb(255, 0, 85);
            backgroundColors[9] = Color.rgb(255, 0, 21);
            backgroundColors[10] = Color.rgb(255, 43, 0);
            backgroundColors[11] = Color.rgb(255, 106, 0);
            backgroundColors[12] = Color.rgb(255, 170, 0);
            backgroundColors[13] = Color.rgb(255, 234, 0);
            backgroundColors[14] = Color.rgb(212, 255, 0);
            backgroundColors[15] = Color.rgb(149, 255, 0);
            backgroundColors[16] = Color.rgb(85, 255, 0);
            backgroundColors[17] = Color.rgb(21, 255, 0);
            backgroundColors[18] = Color.rgb(0, 255, 43);
            backgroundColors[19] = Color.rgb(0, 255, 106);
            backgroundColors[20] = Color.rgb(0, 255, 170);
            backgroundColors[21] = Color.rgb(0, 255, 234);
            backgroundColors[22] = Color.rgb(0, 212, 255);
            backgroundColors[23] = Color.rgb(0, 149, 255);
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


                if (shouldReset()) board.resetBoard();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        private boolean shouldReset() {
            if (RESET_CRACK_THRESHOLD > 0 && glances % RESET_CRACK_THRESHOLD == 0) return true;

            mPrevGlance = mCurrentGlance;
            mCurrentGlance.setToNow();

            if (mCurrentGlance.toMillis(false) - mPrevGlance.toMillis(false) > INACTIVITY_RESET_TIME) return true;


            return false;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

            board.initialize(mWidth, mHeight);

            mTextHeight = TEXT_DIGITS_HEIGHT * mHeight;
            mTextRightMargin = TEXT_DIGITS_RIGHT_MARGIN * mWidth;
            mTextPaintInteractive.setTextSize(mTextHeight);
            mTextPaintAmbient.setTextSize(mTextHeight);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

            int hour = mTime.hour;
            if (NEW_HOUR_PER_GLANCE) {
                hour = (hour + glances) % 24;
            }

//            String hourStr = String.format("%02d", hour % 12 == 0 ? 12 : hour % 12);
            String hourStr = Integer.toString(hour % 12 == 0 ? 12 : hour % 12);
            String minuteStr = String.format("%02d", mTime.minute);

            if (mAmbient) {

                // if on debug, show real time on ambient
                if (NEW_HOUR_PER_GLANCE) {
                    hour = mTime.hour;
                    hourStr = Integer.toString(hour % 12 == 0 ? 12 : hour % 12);
                }
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT); // background

                board.render(canvas, true);
//                drawTestGrays(canvas);

                drawTextVerticallyCentered(canvas, mTextPaintAmbient, hourStr + ":" + minuteStr,
                        mWidth - mTextRightMargin, mCenterY);

            } else {
//                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);
                canvas.drawColor(backgroundColors[hour]);

                board.update();
                board.render(canvas, false);

                drawTextVerticallyCentered(canvas, mTextPaintInteractive, hourStr + ":" + minuteStr,
                        mWidth - mTextRightMargin, mCenterY);
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

            private static final int COLOR = Color.WHITE;
            private static final float FRICTION = 0.97f;
            private static final float ACCEL_FACTOR = 0.25f;
            private static final float RADIUS_FACTOR = 0.03f;     // as a ratio to screen width

            Board parent;
            float x, y, r;
            float velX, velY;
            Paint paint;

            Ball(Board parent_) {
                parent = parent_;

                x = 0.50f * parent.width;
                y = 0.01f * parent.height;
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
            static final int     MAX_TRIANGLE_COUNT = 15;

            int width, height;
            Ball ball;

            Bounce[] bounces;
            int bounceCount, bounceIterator;

            Triangle[] triangles;
            int triangleCount, triangleIterator;
            List<Triangle> triangleUpdateBuffer = new ArrayList<>();

            Paint linePaint;
            Paint trianglePaint;

            Board() {}

            void initialize(int screenW, int  screenH) {
                width = screenW;
                height = screenH;
                ball = new Ball(this);

                bounces = new Bounce[MAX_TRIANGLE_COUNT + 2];
                bounceCount = 0;
                bounceIterator = 0;

                triangles = new Triangle[MAX_TRIANGLE_COUNT];
                triangleCount = 0;
                triangleIterator = 0;

                linePaint = new Paint();
                linePaint.setColor(Color.GRAY);
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setStrokeWidth(1.0f);
                linePaint.setAntiAlias(false);
//                linePaint.setStrokeCap(Paint.Cap.ROUND);

                trianglePaint = new Paint();
                trianglePaint.setColor(Color.WHITE);
                trianglePaint.setStyle(Paint.Style.FILL);
                trianglePaint.setAntiAlias(true);

                // Initialize two bounces for an initial triangle cursor
                addBounce(0, 0);
                addBounce(mWidth, 0);
            }

            void update() {
                ball.update();

                for (Triangle tt : triangleUpdateBuffer) {
                    tt.update();
                }

                for (int i = triangleUpdateBuffer.size() - 1; i >= 0; i--) {
                    if (!triangleUpdateBuffer.get(i).needsUpdate) {
                        triangleUpdateBuffer.remove(i);
                    }
                }
            }

            void render(Canvas canvas, boolean ambientMode) {
                // @TODO background is drawn before this call, change this at some point

                if (ambientMode) {
//                    for (int i = 0; i < bounceCount - 1; i++) {
//                        canvas.drawLine(bounces[i].x, bounces[i].y,
//                                bounces[i + 1].x, bounces[i + 1].y, linePaint);
////                        Log.v(TAG, "bounceCount: " + bounceCount + " bounceIterator: "
////                                + bounceIterator + " i: " + i);
//                    }
//
//                    // Close the polyline
//                    if (bounceCount > 2) {
//                        canvas.drawLine(bounces[bounceCount - 1].x, bounces[bounceCount - 1].y,
//                                bounces[0].x, bounces[0].y, linePaint);
//                    }

//                    Log.v(TAG, "Paint: " + linePaint.toString());
//                    Log.v(TAG, "Canvas: " + canvas.toString());

//                    for (Triangle t : triangles) {
//                        Log.v(TAG, "Triangle: " + t.toString());
//                        t.renderOutline(canvas, linePaint);
//                    }

                    for (int i = 0; i < triangleCount; i++) {
//                        Log.v(TAG, "Triangle: " + triangles[i].toString());
                        triangles[i].renderOutline(canvas, linePaint);
                    }

                } else {
//                    for (int i = 0; i < triangleCount; i++) {
//                        triangles[i].render(canvas, trianglePaint);
//                    }

                    for (int i = 0; i < triangleCount; i++) {
                        int pos = (triangleIterator + i) % triangleCount;
//                        Log.v(TAG, "triangleCount: " + triangleCount + " triangleIterator: "
//                                + triangleIterator + " i: " + i + " pos: " + pos);
                        triangles[pos].render(canvas, trianglePaint);
                    }
                    if (DRAW_BALL) ball.render(canvas);
                    if (USE_TRIANGLE_CURSOR) renderTriangleCursor(canvas);
                }
            }

            void renderTriangleCursor(Canvas canvas) {
                Bounce a, b;
                if (bounceCount < 2) {
                    a = new Bounce((int) mCenterX, 0);
                    b = new Bounce((int) mCenterX, mHeight);
                } else {
                    int posA = (bounceIterator - 2) % bounceCount;
                    if (posA < 0) posA += bounceCount;
                    int posB = (bounceIterator - 1) % bounceCount;
                    if (posB < 0) posB += bounceCount;
//                    Log.v(TAG, "posA: " + posA + " posB: " + posB);

                    a = bounces[posA];
                    b = bounces[posB];
                }

                Path path = new Path();
                path.moveTo(a.x, a.y);
                path.lineTo(b.x, b.y);
                path.lineTo(ball.x, ball.y);


                // CURSOR TRIANGLE FOLLOWS NEW TRIANGULATION ALGORITHM
//                Bounce a, b;
//                Bounce start, corner, middle;
//                boolean threeV = false;
//                if (bounceCount < 2) {
//                    start = new Bounce((int) mCenterX, 0);
//                    middle = new Bounce((int) mCenterX, mHeight);
//                    corner = middle;  // fake initialization for compile
//                } else {
//                    int posA = (bounceIterator - 2) % bounceCount;
//                    if (posA < 0) posA += bounceCount;
//                    int posB = (bounceIterator - 1) % bounceCount;
//                    if (posB < 0) posB += bounceCount;
//                                        a = bounces[posA];
//                    b = bounces[posB];
//
//                    if (a.side < b.side) {
//                        start = a;
//                        middle = b;
//                    } else if (a.side == 3 && b.side == 0) {
//                        start = a;
//                        middle = b;
//                    } else {
//                        start = b;
//                        middle = a;
//                    }
//                    if (middle.side - start.side != 2) {
//                        corner = generateCornerBounce(start);
//                        threeV = true;
//                    } else {
//                        corner = middle;  // fake initialization for compile
//                    }
//                }
//
//                Path path = new Path();
//                path.moveTo(start.x, start.y);
//                if (threeV) {
//                    path.lineTo(corner.x, corner.y);
//                }
//                path.lineTo(middle.x, middle.y);
//                path.lineTo(ball.x, ball.y);

                trianglePaint.setColor(Color.argb(CURSOR_TRIANGLE_ALPHA, 255, 255, 255));
                canvas.drawPath(path, trianglePaint);
            }

            void addBounce(int xpos, int ypos) {
                Bounce bounce = new Bounce(xpos, ypos);

                if (AVOID_DUPLICATE_SIDES
                        && bounceCount > 0) {
                    // If repeating bouncing side, do not add it to the list
                    int lastBounce = (bounceIterator - 1) % bounceCount;
                    if (lastBounce < 0) lastBounce += bounceCount;
                    if (bounce.side == bounces[lastBounce].side) return;
                }

                // Otherwise, add it to the array
                bounces[bounceIterator++] = bounce;

                if (bounceIterator >= MAX_TRIANGLE_COUNT + 2) {
                    bounceIterator = 0;
                }
                if (bounceCount < MAX_TRIANGLE_COUNT + 2) {
                    bounceCount++;
                }



                if (bounceCount > 2) {
                    int posA = (bounceIterator - 3) % bounceCount;
                    if (posA < 0) posA += bounceCount;
                    int posB = (bounceIterator - 2) % bounceCount;
                    if (posB < 0) posB += bounceCount;
                    int posC = (bounceIterator - 1) % bounceCount;
                    if (posC < 0) posC += bounceCount;

//                    Triangle t = new Triangle(bounces[bounceCount - 3],
//                            bounces[bounceCount - 2], bounces[bounceCount - 1]);
                    Triangle t = new Triangle(bounces[posA], bounces[posB], bounces[posC]);

//                    triangles[triangleCount++] = t;
                    triangles[triangleIterator++] = t;

                    if (triangleIterator >= MAX_TRIANGLE_COUNT) {
                        triangleIterator = 0;
                    }
                    if (triangleCount < MAX_TRIANGLE_COUNT) {
                        triangleCount++;
                    }

//                    triangleCount++;
//                    if (triangleCount > MAX_TRIANGLE_COUNT) triangleCount = MAX_TRIANGLE_COUNT;

                    triangleUpdateBuffer.add(t);
                }

                // Double the side of the arrays if necessary
//                if (bounceCount >= bounces.length) {
//                    bounces = Arrays.copyOf(bounces, 2 * bounces.length);
//                }
//                if (triangleCount >= triangles.length) {
//                    triangles = Arrays.copyOf(triangles, 2 * triangles.length);
//                }
            }

            void resetBoard() {
                bounces = new Bounce[MAX_TRIANGLE_COUNT + 2];
                bounceCount = 0;
                bounceIterator = 0;
                addBounce(0, 0);
                addBounce(mWidth, 0);

                ball.x = 0.50f * mWidth;
                ball.y = 0.01f * mHeight;

                triangles = new Triangle[MAX_TRIANGLE_COUNT];
                triangleCount = 0;
                triangleIterator = 0;
            }

//            Bounce generateCornerBounce(Bounce startBounce) {
//                switch (startBounce.side) {
//                    case 0:
//                        return new Bounce(mWidth, 0);
//                    case 1:
//                        return new Bounce(mWidth, mHeight);
//                    case 2:
//                        return new Bounce(0, mHeight);
//                    case 3:
//                    default:
//                        return new Bounce(0, 0);
//                }
//            }

        }







        class Triangle {
            private final static float VERTICES_ANIM_SPEED = 0.25f;
            private final static int   VERTICES_ANIM_END_THRESHOLD = 5;

            private final static float COLOR_ANIM_SPEED = 0.10f;
            private final static int   COLOR_ANIM_END_THRESHOLD = 100;

            Bounce start, middle, end, corner;
            Path pathFull, pathOutline;
            int baseColor, currentColor;
            boolean animateVertices, animateColor;
            boolean needsUpdate;
            boolean containsCornerBounce = false;
//            float endX, endY;
            float cornerX, cornerY;

            Triangle(Bounce start_, Bounce middle_, Bounce end_) {
                if (start_.side < middle_.side) {
                    start = start_;
                    middle = middle_;
                } else if (start_.side == 3 && middle_.side == 0) {
                    start = start_;
                    middle = middle_;
                } else {
                    start = middle_;
                    middle = start_;
                }
                end = end_;

                if (middle.side - start.side != 2) {
                    containsCornerBounce = true;
                    corner = generateCornerBounce();
                }

//                Log.v(TAG, "Created triangle start: " + start.x + "," + start.y +
//                        " corner: " + (corner == null ? "null" : (corner.x + "," + corner.y)) +
//                        " middle: " + middle.x + "," + middle.y +
//                        " end: " + end.x + "," + end.y);

                baseColor = end.color;

                if (TRIANGLES_ANIMATE_VERTEX_ON_CREATION) {
                    animateVertices = true;
//                    endX = middle.x;
//                    endY = middle.y;
                    cornerX = Math.min(start.x, middle.x) + 0.5f * Math.abs(start.x - middle.x);
                    cornerY = Math.min(start.y, middle.y) + 0.5f * Math.abs(start.y - middle.y);
                } else {
                    animateVertices = false;
//                    endX = end.x;
//                    endY = end.y;
                    cornerX = corner.x;
                    cornerY = corner.y;
                }

                pathFull = new Path();
                pathFull.moveTo(start.x, start.y);
                if (containsCornerBounce) pathFull.lineTo(cornerX, cornerY);
                pathFull.lineTo(middle.x, middle.y);
                pathFull.lineTo(end.x, end.y);

                pathOutline = new Path();
                pathOutline.moveTo(start.x, start.y);
                pathOutline.lineTo(end.x, end.y);
                pathOutline.lineTo(middle.x, middle.y);
                if (!containsCornerBounce) pathOutline.close();

                if (TRIANGLES_ANIMATE_COLOR_ON_CREATION) {
                    animateColor = true;
                    currentColor = Color.argb(CURSOR_TRIANGLE_ALPHA, 255, 255, 255);
                } else {
                    animateColor = false;
                    currentColor = baseColor;
                }

                needsUpdate = animateColor || animateVertices;
            }

            boolean update() {
//                Log.v(TAG, "Updating T:" + this.toString());

                if (TRIANGLES_ANIMATE_VERTEX_ON_CREATION && animateVertices && containsCornerBounce) {
//                    float diffX = end.x - endX,
//                            diffY = end.y - endY;
                    float diffX = corner.x - cornerX,
                            diffY = corner.y - cornerY;

                    if (Math.abs(diffX) < VERTICES_ANIM_END_THRESHOLD && Math.abs(diffY) < VERTICES_ANIM_END_THRESHOLD) {
//                        endX = end.x;
//                        endY = end.y;
                        cornerX = corner.x;
                        cornerY = corner.y;
                        animateVertices = false;
                    } else {
//                        endX += VERTICES_ANIM_SPEED * diffX;
//                        endY += VERTICES_ANIM_SPEED * diffY;
                        cornerX += VERTICES_ANIM_SPEED * diffX;
                        cornerY += VERTICES_ANIM_SPEED * diffY;
                    }

                    pathFull.rewind();
                    pathFull.moveTo(start.x, start.y);
//                    if (containsCornerBounce) path.lineTo(corner.x, corner.y);
                    if (containsCornerBounce) pathFull.lineTo(cornerX, cornerY);
                    pathFull.lineTo(middle.x, middle.y);
//                    path.lineTo(endX, endY);
                    pathFull.lineTo(end.x, end.y);
                }

                if (TRIANGLES_ANIMATE_COLOR_ON_CREATION && animateColor) {
                    int prevColor = currentColor;
                    currentColor = interpolateColor(currentColor, baseColor, COLOR_ANIM_SPEED);

                    if (prevColor == currentColor) {  // @TODO improve this check to accept some threshold
                        animateColor = false;
                    }
                }

                needsUpdate = animateColor || animateVertices;

                return needsUpdate;
            }

            void render(Canvas canvas, Paint paint) {
                paint.setColor(currentColor);
                canvas.drawPath(pathFull, paint);
            }

            void renderOutline(Canvas canvas, Paint paint) {
                canvas.drawPath(pathOutline, paint);
            }


            /**
             * This can be optimized with bitwise operators: http://stackoverflow.com/a/18037185/1934487
             * @param sourceColor
             * @param targetColor
             * @param parameter
             * @return
             */
            int interpolateColor(int sourceColor, int targetColor, float parameter) {
                int r = Color.red(sourceColor)   + (int) (parameter * (Color.red(targetColor)   - Color.red(sourceColor)));
                int g = Color.green(sourceColor) + (int) (parameter * (Color.green(targetColor) - Color.green(sourceColor)));
                int b = Color.blue(sourceColor)  + (int) (parameter * (Color.blue(targetColor)  - Color.blue(sourceColor)));
                int a = Color.alpha(sourceColor) + (int) (parameter * (Color.alpha(targetColor) - Color.alpha(sourceColor)));

                return Color.argb(a, r, g, b);
            }

            Bounce generateCornerBounce() {
                switch (start.side) {
                    case 0:
                        return new Bounce(mWidth, 0);
                    case 1:
                        return new Bounce(mWidth, mHeight);
                    case 2:
                        return new Bounce(0, mHeight);
                    case 3:
                    default:
                        return new Bounce(0, 0);
                }
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
                color = Color.argb(COLOR_TRIANGLE_ALPHA,
                        (int) (255 * Math.random()),
                        (int) (255 * Math.random()),
                        (int) (255 * Math.random())
                        );
            }

        }



//        void drawTestGrays(Canvas canvas) {
//
//            Paint p = new Paint();
//            p.setStyle(Paint.Style.FILL);
//
//            p.setColor(Color.rgb(50, 50, 50));
//            canvas.drawRect(10, 10, 50, 100, p);
//
//            p.setColor(Color.rgb(100, 100, 100));
//            canvas.drawRect(50, 10, 100, 100, p);
//
//            p.setColor(Color.rgb(150, 150, 150));
//            canvas.drawRect(100, 10, 150, 100, p);
//
//            p.setColor(Color.rgb(200, 200, 200));
//            canvas.drawRect(150, 10, 200, 100, p);
//
//            p.setColor(Color.rgb(255, 255, 255));
//            canvas.drawRect(200, 10, 250, 100, p);
//
//        }

    }

}