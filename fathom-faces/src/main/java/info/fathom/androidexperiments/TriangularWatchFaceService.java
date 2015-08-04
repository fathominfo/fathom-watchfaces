package info.fathom.androidexperiments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class TriangularWatchFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String  TAG = "TriangularWFService";

    private static final long    INTERACTIVE_UPDATE_RATE_MS = 33;

    private static final int     BACKGROUND_COLOR_AMBIENT = Color.BLACK;
    private final static int     BACKGROUND_COLORS_COUNT = 24;
    private final int[]          backgroundColors = new int[BACKGROUND_COLORS_COUNT];
    private final static int     COLOR_TRIANGLE_ALPHA = 100;
    private final static int     CURSOR_TIP_ALPHA = 200;
    private final static int     RANGE_HUE = 165;

    private static final String  RALEWAY_TYPEFACE_PATH = "fonts/raleway-regular-enhanced.ttf";
    private static final int     TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int     TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
    private static final float   TEXT_DIGITS_HEIGHT = 0.2f;  // as a factor of screen height
    private static final float   TEXT_DIGITS_BASELINE_HEIGHT = 0.43f;  // as a factor of screen height
    private static final float   TEXT_DIGITS_RIGHT_MARGIN = 0.08f;  // as a factor of screen width

    private static final int     RESET_HOUR = 4;  // at which hour will watch face reset [0...23], -1 to deactivate
    private static final long    INACTIVITY_RESET_TIME = TimeUnit.HOURS.toMillis(1);

    // DEBUG
    private static final boolean DEBUG_LOGS = false;
    private static final int     RESET_CRACK_THRESHOLD = 0;  // every nth glance, cracks will be reset (0 does no resetting)
    private static final boolean NEW_HOUR_PER_GLANCE = false;  // this will add an hour to the time at each glance






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

//        private int mFrameCount = 0;

        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mAmbient, mScreenOn;
//        private boolean mLowBitAmbient;
//        private boolean mBurnInProtection;

        private Time mTime;
        private String mTimeStr;
        private int mHourInt, mMinuteInt;
        private int mLastAmbientHour;
        private Time mCurrentGlance;
        private long mPrevGlance;

        private Paint mTextDigitsPaintInteractive, mTextDigitsPaintAmbient;
        private float mTextDigitsHeight, mTextDigitsBaselineHeight, mTextDigitsRightMargin;
        private final Rect textBounds = new Rect();
        private Typeface RALEWAY_REGULAR_TYPEFACE;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private boolean mIsRound;
        private float mRadius;

        private Board board;
        private int glances = 0;  // how many times did the watch go from ambient to interactive?

        private int currentR, currentG, currentB;
        private int triangleColorNew = generateTriangleColor();





        @Override
        public void onCreate(SurfaceHolder holder) {
            if (DEBUG_LOGS) Log.v(TAG, "onCreate(): " + holder.toString());
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TriangularWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            RALEWAY_REGULAR_TYPEFACE = Typeface.createFromAsset(getApplicationContext().getAssets(),
                    RALEWAY_TYPEFACE_PATH);

            mTextDigitsPaintInteractive = new Paint();
            mTextDigitsPaintInteractive.setColor(TEXT_DIGITS_COLOR_INTERACTIVE);
            mTextDigitsPaintInteractive.setTypeface(RALEWAY_REGULAR_TYPEFACE);
            mTextDigitsPaintInteractive.setTextAlign(Paint.Align.RIGHT);
            mTextDigitsPaintInteractive.setAntiAlias(true);

            mTextDigitsPaintAmbient = new Paint();
            mTextDigitsPaintAmbient.setColor(TEXT_DIGITS_COLOR_AMBIENT);
            mTextDigitsPaintAmbient.setTypeface(RALEWAY_REGULAR_TYPEFACE);
            mTextDigitsPaintAmbient.setTextAlign(Paint.Align.RIGHT);
            mTextDigitsPaintAmbient.setAntiAlias(false);

            board = new Board();

            mTime  = new Time();
            mCurrentGlance = new Time();
            mCurrentGlance.setToNow();
            mPrevGlance = mCurrentGlance.toMillis(false);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = new SensorWrapper("Accelerometer", Sensor.TYPE_ACCELEROMETER, 3,
                    TriangularWatchFaceService.this, mSensorManager);
            mSensorAccelerometer.register();

            registerScreenReceiver();

            backgroundColors[0] =  Color.HSVToColor(new float[]{ 130.0f, 1.0f, 1.0f});
            backgroundColors[1] =  Color.HSVToColor(new float[]{ 115.0f, 1.0f, 1.0f});
            backgroundColors[2] =  Color.HSVToColor(new float[]{ 100.0f, 1.0f, 1.0f});
            backgroundColors[3] =  Color.HSVToColor(new float[]{  85.0f, 1.0f, 1.0f});
            backgroundColors[4] =  Color.HSVToColor(new float[]{  70.0f, 1.0f, 1.0f});
            backgroundColors[5] =  Color.HSVToColor(new float[]{  55.0f, 1.0f, 1.0f});
            backgroundColors[6] =  Color.HSVToColor(new float[]{  40.0f, 1.0f, 1.0f});
            backgroundColors[7] =  Color.HSVToColor(new float[]{  25.0f, 1.0f, 1.0f});
            backgroundColors[8] =  Color.HSVToColor(new float[]{  10.0f, 1.0f, 1.0f});
            backgroundColors[9] =  Color.HSVToColor(new float[]{ 355.0f, 1.0f, 1.0f});
            backgroundColors[10] = Color.HSVToColor(new float[]{ 340.0f, 1.0f, 1.0f});
            backgroundColors[11] = Color.HSVToColor(new float[]{ 325.0f, 1.0f, 1.0f});
            backgroundColors[12] = Color.HSVToColor(new float[]{ 310.0f, 1.0f, 1.0f});
            backgroundColors[13] = Color.HSVToColor(new float[]{ 295.0f, 1.0f, 1.0f});
            backgroundColors[14] = Color.HSVToColor(new float[]{ 280.0f, 1.0f, 1.0f});
            backgroundColors[15] = Color.HSVToColor(new float[]{ 265.0f, 1.0f, 1.0f});
            backgroundColors[16] = Color.HSVToColor(new float[]{ 250.0f, 1.0f, 1.0f});
            backgroundColors[17] = Color.HSVToColor(new float[]{ 235.0f, 1.0f, 1.0f});
            backgroundColors[18] = Color.HSVToColor(new float[]{ 220.0f, 1.0f, 1.0f});
            backgroundColors[19] = Color.HSVToColor(new float[]{ 205.0f, 1.0f, 1.0f});
            backgroundColors[20] = Color.HSVToColor(new float[]{ 190.0f, 1.0f, 1.0f});
            backgroundColors[21] = Color.HSVToColor(new float[]{ 175.0f, 1.0f, 1.0f});
            backgroundColors[22] = Color.HSVToColor(new float[]{ 160.0f, 1.0f, 1.0f});
            backgroundColors[23] = Color.HSVToColor(new float[]{ 145.0f, 1.0f, 1.0f});

        }

        @Override
        public void onDestroy() {
            if (DEBUG_LOGS) Log.v(TAG, "onDestroy()");
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            unregisterTimeZoneReceiver();
            unregisterScreenReceiver();
            mSensorAccelerometer.unregister();
            mSensorManager.unregisterListener(TriangularWatchFaceService.this);
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

            if (visible)
                mSensorAccelerometer.register();
            else
                mSensorAccelerometer.unregister();

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
            TriangularWatchFaceService.this.registerReceiver(mScreenReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_ON));
            TriangularWatchFaceService.this.registerReceiver(mScreenReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }

        private void unregisterScreenReceiver() {
            TriangularWatchFaceService.this.unregisterReceiver(mScreenReceiver);
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
                mSensorAccelerometer.register();

                glances++;
                if (shouldReset()) board.reset();

            } else {
                if (timelyReset()) {
                    if (DEBUG_LOGS) Log.v(TAG, "Resetting watchface");
                    board.reset();
                }

                unregisterTimeZoneReceiver();
                mSensorAccelerometer.unregister();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (DEBUG_LOGS) Log.v(TAG, "onSurfaceChanged: " + format + " " + width + " " + height);
            super.onSurfaceChanged(holder, format, width, height);

            mWidth   = width;
            mHeight  = height;
            mCenterX = 0.50f * mWidth;
            mCenterY = 0.50f * mHeight;
            mRadius  = 0.50f * mWidth;

            board.initialize(mWidth, mHeight);

            mTextDigitsHeight = TEXT_DIGITS_HEIGHT * mHeight;
            mTextDigitsBaselineHeight = TEXT_DIGITS_BASELINE_HEIGHT * mHeight;
            mTextDigitsRightMargin = TEXT_DIGITS_RIGHT_MARGIN * mWidth;
            mTextDigitsPaintInteractive.setTextSize(mTextDigitsHeight);
            mTextDigitsPaintAmbient.setTextSize(mTextDigitsHeight);
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
//            if (DEBUG_LOGS) Log.v(TAG, "Drawing canvas " + mFrameCount++);

            mTime.setToNow();
            mHourInt = mTime.hour;
            if (NEW_HOUR_PER_GLANCE) {
                mHourInt = (mHourInt + glances) % 24;
            }
            mMinuteInt = mTime.minute;
            mTimeStr = (mHourInt % 12 == 0 ? 12 : mHourInt % 12) + ":" + String.format("%02d", mMinuteInt);

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT); // background
                board.render(canvas, true);
                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintAmbient);

            } else {
//                canvas.drawColor(backgroundColors[randomColor]);
//                int tempBackHue = (mHourInt * 15) + 200;
//                if (tempBackHue > 360) {
//                    tempBackHue -= 360;
//                }
//                int tempBackColor = Color.HSVToColor(new float[]{ (float) tempBackHue, 1.0f, 1.0f });
//                canvas.drawColor(tempBackColor);
                canvas.drawColor(backgroundColors[mHourInt]);

                board.render(canvas, false);
                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintInteractive);
            }
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
            TriangularWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TriangularWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
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

        private boolean shouldReset() {
            if (RESET_CRACK_THRESHOLD > 0 && glances % RESET_CRACK_THRESHOLD == 0) return true;

            mPrevGlance = mCurrentGlance.toMillis(false);
            mCurrentGlance.setToNow();

            if (mCurrentGlance.toMillis(false) - mPrevGlance > INACTIVITY_RESET_TIME) return true;

            return false;
        }

        // Checks if watch face should reset, like overnight
        boolean timelyReset() {
            boolean reset = false;
            if (mHourInt == RESET_HOUR && mLastAmbientHour == RESET_HOUR - 1) {
                reset = true;
            }
            mLastAmbientHour = mHourInt;
            return reset;
        }







        class Cursor {
            private static final int COLOR = Color.WHITE;

            private static final float FRICTION = 0.995f;
            private static final float ACCEL_FACTOR = 0.45f;
//            private static final float FRICTION = 0.80f;
//            private static final float ACCEL_FACTOR = 0.45f;

            Board parent;
            float x, y;
            float velX, velY;
            Paint paint;

            Cursor(Board parent_) {
                parent = parent_;

                x = 0.50f * parent.width;
                y = 0.01f * parent.height;
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

                // @TODO fix case where cursor is out of two bounds simultaneously (bounce coords override)
                // @TODO in general, make this check more programmatic
                boolean bounce = false;
                int bounceX = 0, bounceY = 0;

                if (mIsRound) {
                    double drx = x - mCenterX;
                    double dry = y - mCenterY;
                    double r = Math.sqrt(drx * drx + dry * dry);

                    if (r > mRadius) {
                        double dcx = drx - velX;
                        double dcy = dry - velY;
                        double a = velX * velX + velY * velY;
                        double b = 2 * (velX * dcx + velY * dcy);
                        double c = (dcx * dcx + dcy * dcy) - mRadius * mRadius;
                        double disc = b * b - 4 * a * c;

                        if (disc < 0) {
                            if (DEBUG_LOGS) Log.v(TAG, "No intersection, disc: " + disc);

                        } else {
                            double sq = Math.sqrt(disc);
                            double t1 = (-b + sq) / (2 * a);

                            double xt1 = x - velX + t1 * velX;
                            double yt1 = y - velY + t1 * velY;

                            bounce = true;
                            bounceX = Math.round((float) xt1);
                            bounceY = Math.round((float) yt1);

                            double tangentAngle = Math.atan2(yt1 - mCenterY, xt1 - mCenterX) + 0.50f * Math.PI;
                            double tx = Math.cos(tangentAngle);
                            double ty = Math.sin(tangentAngle);
                            double dVelX = x - xt1;
                            double dVelY = y - yt1;
                            double dVelDotT = dVelX * tx + dVelY * ty;
                            double refDVelX = 2 * dVelDotT * tx - dVelX;
                            double refDVelY = 2 * dVelDotT * ty - dVelY;

                            x = (float) (xt1 + refDVelX);
                            y = (float) (yt1 + refDVelY);

                            double velDotT = velX * tx + velY * ty;
                            velX = (float) (2 * velDotT * tx - velX);
                            velY = (float) (2 * velDotT * ty - velY);
                        }
                    }

                } else {
                    if (x > parent.width) {
                        bounce = true;
                        bounceX = parent.width;
                        bounceY = Math.round(y - velY + velY * x / parent.width);  // proportional height at bounce
                        x = parent.width - (x - parent.width);
                        velX = -velX;

                    } else if (x < 0) {
                        bounce = true;
                        bounceX = 0;
                        bounceY = Math.round(y - velY + velY * x / parent.width);  // proportional height at bounce
                        x = -x;
                        velX = -velX;
                    }

                    if (y > mHeight) {
                        bounce = true;
                        bounceX = Math.round(x - velX + velX * y / parent.height);  // proportional height at bounce
                        bounceY = parent.height;
                        y = mHeight - (y - mHeight);
                        velY = -velY;

                    } else if (y < 0) {
                        bounce = true;
                        bounceX = Math.round(x - velX + velX * y / parent.height);  // proportional height at bounce
                        bounceY = 0;
                        y = -y;
                        velY = -velY;
                    }
                }

                if (bounce) parent.addBounce(bounceX, bounceY);

            }

        }



        class Board {

            static final int     MAX_TRIANGLE_COUNT = 15;

            int width, height;
            Cursor cursor;
            float cursorProjectionX, cursorProjectionY;  // for gradient fills

            List<Bounce> bounces = new ArrayList<>();  // last three bounces
            List<Triangle> triangles = new ArrayList<>();
            List<Triangle> triangleUpdateBuffer = new ArrayList<>();
            List<Triangle> triangleStopUpdatingBuffer = new ArrayList<>();

            Paint linePaint;
            Paint dottedPaint;  // WIP

            Path cursorPath;
            Paint cursorPaint;

            Board() {}

            void initialize(int screenW, int  screenH) {
                width = screenW;
                height = screenH;
                cursor = new Cursor(this);

                linePaint = new Paint();
                linePaint.setColor(Color.GRAY);
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setStrokeWidth(1.0f);
                linePaint.setAntiAlias(false);

                // WIP
                dottedPaint = new Paint();
                dottedPaint.setColor(Color.WHITE);
                dottedPaint.setStyle(Paint.Style.STROKE);
                dottedPaint.setStrokeWidth(0);  // for pixel perfect dots

                cursorPath = new Path();

                cursorPaint = new Paint();
                cursorPaint.setColor(Color.WHITE);
                cursorPaint.setStyle(Paint.Style.FILL);
                cursorPaint.setAntiAlias(true);

                reset();
            }

            void reset() {

                cursor.x = 0.50f * mWidth;
                cursor.y = 0.01f * mHeight;

                triangles.clear();
                triangleUpdateBuffer.clear();

                // Initialize three bounces for an initial triangle cursor
                bounces.clear();
                addBounce(0, 0);  // bogus initialization bounce
                addBounce(1, 0);
                addBounce(mWidth, 1);  // the 1's are a small trick to avoid closed outline
            }

            void update() {
                cursor.update();

                for (Triangle t : triangleUpdateBuffer) {
                    t.update();
                }

                for (Triangle t : triangleStopUpdatingBuffer) {
                    triangleUpdateBuffer.remove(t);
                }
                triangleStopUpdatingBuffer.clear();
            }

            void render(Canvas canvas, boolean ambientMode) {
                // @TODO background is drawn before this call, change this at some point

                if (ambientMode) {
                    for (Triangle t : triangles) {
                        t.renderOutline(canvas, linePaint);
                    }

                } else {
                    update();

                    for (Triangle t : triangles) {
                        t.render(canvas);
                    }

                    renderTriangleCursor(canvas);
                }

            }

            void renderTriangleCursor(Canvas canvas) {

                Bounce a = bounces.get(1);
                Bounce b = bounces.get(2);

                cursorPath.rewind();
                cursorPath.moveTo(a.x, a.y);
                cursorPath.lineTo(cursor.x, cursor.y);
                cursorPath.lineTo(b.x, b.y);

                // gradient fill on projection
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double dpx = cursor.x - a.x;
                double dpy = cursor.y - a.y;
                double xylen = Math.sqrt(dx * dx + dy * dy);
                double pl = (dx * dpx + dy * dpy) / xylen;
                cursorProjectionX = (float) (a.x + pl * dx / xylen);
                cursorProjectionY = (float) (a.y + pl * dy / xylen);
                cursorPaint.setShader(new LinearGradient(cursor.x, cursor.y,
                        cursorProjectionX, cursorProjectionY,
                        Color.argb(CURSOR_TIP_ALPHA, currentR, currentG, currentB),
                        Color.argb(COLOR_TRIANGLE_ALPHA, currentR, currentG, currentB),
                        Shader.TileMode.CLAMP));
                canvas.drawPath(cursorPath, cursorPaint);
                cursorPaint.setShader(null);
            }

            void addBounce(int xpos, int ypos) {
                Bounce bounce = new Bounce(xpos, ypos);

                int bounceCount = bounces.size();

                if (bounceCount > 2) {
                    if (bounce.side == bounces.get(2).side) return;
                }

                // Otherwise, add it to the array
                bounces.add(bounce);
                bounceCount++;
                if (bounceCount > 3) {
                    bounces.remove(0);  // keep it down to three elements
                    bounceCount--;
                }

                if (bounceCount > 2) {
                    Triangle t = new Triangle(this, bounces.get(0), bounces.get(1), bounces.get(2),
                            cursorProjectionX, cursorProjectionY);
                    triangles.add(t);
//                    triangleUpdateBuffer.add(t);  // added to Triangle.constructor

                    if (triangles.size() > MAX_TRIANGLE_COUNT) {
                        // Remove however many triangles above the limit
                        int criminals = triangles.size() - MAX_TRIANGLE_COUNT;
                        for (int i = 0; i < criminals; i++) {
                            Triangle condemned = triangles.get(i);
                            if (!condemned.mustDie) {
                                condemned.kill();  // if it wasn't flagged before
                                triangleUpdateBuffer.add(condemned);
                            }
                        }
                    }

                    // After triangle was created with current color, generate a new one
                    triangleColorNew = generateTriangleColor();
                }
            }
        }




        private int triangleCounter = 0;

        class Triangle {

            private final static float VERTICES_ANIM_SPEED = 0.25f;
            private final static int   VERTICES_ANIM_END_THRESHOLD = 5;
            private final static float COLOR_ANIM_SPEED = 0.10f;

            int id;
            Board parent;

            Bounce start, middle, end, corner;
            Path pathFull, pathOutline;
            boolean animateVertices, animateColor;
            boolean needsUpdate;
            boolean containsCornerBounce = false;
            float cornerX, cornerY;

            int baseColor, currentColor, targetColor;
            int currA, currR, currG, currB;
            int currentTipAlpha;
            Paint paint;

            float gradEndX, gradEndY;  // gradient target passed by Cursor
            boolean animateGradient;

            boolean mustDie;

            Triangle(Board parent_, Bounce start_, Bounce middle_, Bounce end_,
                     float gradEndX_, float gradEndY_) {

                id = triangleCounter++;
                parent = parent_;

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

                if (containsCornerBounce) {
                    animateVertices = true;
                    cornerX = Math.min(start.x, middle.x) + 0.5f * Math.abs(start.x - middle.x);
                    cornerY = Math.min(start.y, middle.y) + 0.5f * Math.abs(start.y - middle.y);
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

                baseColor = triangleColorNew;
                currentColor = targetColor = baseColor;
                animateColor = false;
                interpolateColor(currentColor, targetColor, 1);  // initialize currentARGBs

                gradEndX = gradEndX_;
                gradEndY = gradEndY_;
                currentTipAlpha = CURSOR_TIP_ALPHA;
                animateGradient = true;  // kick off transition from the beginning

                paint = new Paint();
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                paint.setAntiAlias(true);
                // start off a gradient fill
                paint.setShader(new LinearGradient(end.x, end.y,
                        gradEndX, gradEndY,
                        Color.argb(currentTipAlpha, currR, currG, currB),
                        Color.argb(COLOR_TRIANGLE_ALPHA, currR, currG, currB),
                        Shader.TileMode.CLAMP));

                needsUpdate = true;
                parent.triangleUpdateBuffer.add(this);
            }

            public boolean update() {

                if (animateVertices) {
                    float diffX = corner.x - cornerX,
                            diffY = corner.y - cornerY;

                    if (Math.abs(diffX) < VERTICES_ANIM_END_THRESHOLD && Math.abs(diffY) < VERTICES_ANIM_END_THRESHOLD) {
                        cornerX = corner.x;
                        cornerY = corner.y;
                        animateVertices = false;

                    } else {
                        cornerX += VERTICES_ANIM_SPEED * diffX;
                        cornerY += VERTICES_ANIM_SPEED * diffY;
                    }

                    pathFull.rewind();
                    pathFull.moveTo(start.x, start.y);
                    if (containsCornerBounce) pathFull.lineTo(cornerX, cornerY);
                    pathFull.lineTo(middle.x, middle.y);
                    pathFull.lineTo(end.x, end.y);
                }

                if (animateColor) {
                    int prevColor = currentColor;
                    currentColor = interpolateColor(currentColor, targetColor, COLOR_ANIM_SPEED);

                    if (prevColor == currentColor) {
                        animateColor = false;
                        if (mustDie) {
                            parent.triangles.remove(this);
                        }
                    }

                    paint.setColor(currentColor);
                }

                if (animateGradient) {
                    paint.setShader(null);  // reset gradient fill
                    int prevAlpha = currentTipAlpha;
                    currentTipAlpha += COLOR_ANIM_SPEED * (COLOR_TRIANGLE_ALPHA - currentTipAlpha);

                    if (prevAlpha == currentTipAlpha) {
                        animateGradient = false;
                        paint.setColor(currentColor);

                    } else {
                        paint.setShader(new LinearGradient(end.x, end.y,
                                gradEndX, gradEndY,
                                Color.argb(currentTipAlpha, currR, currG, currB),
                                Color.argb(COLOR_TRIANGLE_ALPHA, currR, currG, currB),
                                Shader.TileMode.CLAMP));
                    }
                }

                needsUpdate = animateVertices || animateColor || animateGradient;
                if (DEBUG_LOGS) Log.v(TAG, "  needsUpdate: " + needsUpdate);
                if (!needsUpdate) parent.triangleStopUpdatingBuffer.add(this);
                return needsUpdate;
            }

            public void render(Canvas canvas) {
                canvas.drawPath(pathFull, paint);

            }

            public void renderOutline(Canvas canvas, Paint paint) {
                canvas.drawPath(pathOutline, paint);
            }

            public void kill() {
                mustDie = true;
                targetColor = Color.argb(0, currR, currG, currB);
                animateColor = true;
                needsUpdate = true;
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

                currA = sA + (int) (parameter * (tA - sA));
                currR = sR + (int) (parameter * (tR - sR));
                currG = sG + (int) (parameter * (tG - sG));
                currB = sB + (int) (parameter * (tB - sB));

                return Color.argb(currA, currR, currG, currB);
            }

            private Bounce generateCornerBounce() {
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

            final static double TAU_MINUS_3_8 = -0.75 * Math.PI;
            final static double TAU_MINUS_1_8 = -0.25 * Math.PI;
            final static double TAU_PLUS_1_8 = 0.25 * Math.PI;
            final static double TAU_PLUS_3_8 = 0.75 * Math.PI;

            int x, y;
            int side;  // 0 for top... 3 for left (clockwise)
            int color;

            Bounce(int x_, int y_) {
                x = x_;
                y = y_;

                if (mIsRound) {
                    double angle = Math.atan2(y - mCenterY, x - mCenterX);
                    if (angle > TAU_MINUS_3_8 && angle <= TAU_MINUS_1_8) side = 0;
                    else if (angle > TAU_MINUS_1_8 && angle <= TAU_PLUS_1_8) side = 1;
                    else if (angle > TAU_PLUS_1_8 && angle <= TAU_PLUS_3_8) side = 2;
                    else side = 3;

                } else {
                    if (x == 0)            side = 3;
                    else if (x == mWidth)  side = 1;
                    else if (y == 0)       side = 0;
                    else if (y == mHeight) side = 2;
                }

                color = triangleColorNew;
            }

        }

        int generateTriangleColor() {
            // start range at the minute of the hour mapped to the total hue, minus half the range
            int totalHue = 360;
            int startHue = (mMinuteInt * 6) - (RANGE_HUE/2);
            int endHue   = startHue + RANGE_HUE;

            // find random number between the range
            int randomHue = randomRange(startHue, endHue);

            // adjust the random number
            if (randomHue < 0) {
                randomHue += totalHue;
            } else if (randomHue > totalHue) {
                randomHue -= totalHue;
            }

//            int currentTriangleColor = Color.HSVToColor(randomRange(COLOR_TRIANGLE_ALPHA - 25, COLOR_TRIANGLE_ALPHA + 25), new float[]{ (float) randomHue, 1.0f, 1.0f } );
            int currentTriangleColor = Color.HSVToColor(COLOR_TRIANGLE_ALPHA, new float[]{ (float) randomHue, 1.0f, 1.0f } );
            currentR = Color.red(currentTriangleColor);
            currentG = Color.green(currentTriangleColor);
            currentB = Color.blue(currentTriangleColor);

            return currentTriangleColor;
        }

        int randomRange(int min, int max) {
            int range = (max - min) + 1;
            return (int)(Math.random() * range) + min;
        }

    }





    // Sensors
    private SensorManager mSensorManager;
    private SensorWrapper mSensorAccelerometer;
    private float[] gravity = new float[3];

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                mSensorAccelerometer.update(event);
                updateGravity(event);
                break;
        }
    }

    void updateGravity(SensorEvent event) {
        // In this example, alpha is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the event delivery rate.
        final float alpha = 0.8f;

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        // gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
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