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

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class TheTwinkieFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "TheTwinkieFaceService";

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

    private static final int   BACKGROUND_COLOR_AMBIENT = Color.BLACK;
    private final static int   BACKGROUND_COLORS_COUNT = 7;
    private final int[]        backgroundColors = new int[BACKGROUND_COLORS_COUNT];
    private final static int   COLOR_TRIANGLE_ALPHA = 100;
    private final static int   CURSOR_TRIANGLE_ALPHA = 100;

    private static final String RALEWAY_TYPEFACE_PATH = "fonts/raleway-regular-enhanced.ttf";
    private static final int   TEXT_DIGITS_COLOR_INTERACTIVE = Color.WHITE;
    private static final int   TEXT_DIGITS_COLOR_AMBIENT = Color.WHITE;
    private static final float TEXT_DIGITS_HEIGHT = 0.2f;  // as a factor of screen height
    private static final float TEXT_DIGITS_BASELINE_HEIGHT = 0.43f;  // as a factor of screen height
    private static final float TEXT_DIGITS_RIGHT_MARGIN = 0.08f;  // as a factor of screen width

    private static final int   RESET_HOUR = 4;  // at which hour will watch face reset [0...23], -1 to deactivate
    private static final long  INACTIVITY_RESET_TIME = TimeUnit.HOURS.toMillis(1);

    // DEBUG
    private static final int     RESET_CRACK_THRESHOLD = 0;  // every nth glance, cracks will be reset (0 does no resetting)
    private static final boolean NEW_HOUR_PER_GLANCE = false;  // this will add an hour to the time at each glance
    private static final boolean DRAW_BALL = false;
    private static final boolean USE_TRIANGLE_CURSOR = true;
    private static final boolean TRIANGLES_ANIMATE_VERTEX_ON_CREATION = true;
    private static final boolean TRIANGLES_ANIMATE_COLOR_ON_CREATION = true;
    private static final boolean DISPLAY_BOUNCES = false;













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
        private boolean mAmbient;
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

        private int randomColor;
        private int triangleColorNew = randomHSVColor();




        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TheTwinkieFaceService.this)
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

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = new SensorWrapper("Accelerometer", Sensor.TYPE_ACCELEROMETER, 3,
                    TheTwinkieFaceService.this, mSensorManager);
            mSensorAccelerometer.register();

            board = new Board();

            mTime  = new Time();
            mCurrentGlance = new Time();
            mCurrentGlance.setToNow();
            mPrevGlance = mCurrentGlance.toMillis(false);

            // Initialize hardcoded day colors
            backgroundColors[0] = Color.rgb(255, 102, 51);
            backgroundColors[1] = Color.rgb(0, 153, 255);
            backgroundColors[2] = Color.rgb(125, 114, 163);
            backgroundColors[3] = Color.rgb(215, 223, 35);
            backgroundColors[4] = Color.rgb(238, 42, 123);
            backgroundColors[5] = Color.rgb(0, 167, 157);
            backgroundColors[6] = Color.rgb(141, 198, 63);

        }

        @Override
        public void onDestroy() {
            mMainHandler.removeMessages(MSG_UPDATE_TIMER);
            mSensorManager.unregisterListener(TheTwinkieFaceService.this);
            unregisterTimeZoneReceiver();
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
            super.onAmbientModeChanged(inAmbientMode);

            // choose random color for the background
            randomColor = (int) (Math.random() * 7);

            if (inAmbientMode) {
                if (timelyReset()) {
                    Log.v(TAG, "Resetting watchface");
                    board.reset();
                }
            }

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            if (mAmbient) {
                unregisterTimeZoneReceiver();
                mSensorAccelerometer.unregister();
            } else {
                registerTimeZoneReceiver();
                mSensorAccelerometer.register();

                glances++;

                if (shouldReset()) board.reset();
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
            super.onApplyWindowInsets(insets);
            mIsRound = insets.isRound();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

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
                canvas.drawColor(backgroundColors[randomColor]);
//                board.update();  // moved to class
                board.render(canvas, false);
                canvas.drawText(mTimeStr, mWidth - mTextDigitsRightMargin,
                        mTextDigitsBaselineHeight, mTextDigitsPaintInteractive);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerTimeZoneReceiver();
                mSensorAccelerometer.register();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterTimeZoneReceiver();
                mSensorAccelerometer.unregister();
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

        // Checks if watchface should reset, like overnight
        boolean timelyReset() {
            boolean reset = false;
            if (mHourInt == RESET_HOUR && mLastAmbientHour == RESET_HOUR - 1) {
                reset = true;
            }
            mLastAmbientHour = mHourInt;
            return reset;
        }










        class Ball {

            private static final int COLOR = Color.WHITE;
            private static final float FRICTION = 0.999f;
            private static final float ACCEL_FACTOR = 0.5f;
//            private static final float FRICTION = 0.97f;
//            private static final float ACCEL_FACTOR = 0.25f;
            private static final float RADIUS_FACTOR = 0.03f;  // as a ratio to screen width

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

                if (mIsRound) {
                    double drx = x - mCenterX;
                    double dry = y - mCenterY;
                    double r = Math.sqrt(drx * drx + dry * dry);

                    if (r > mRadius) {
//                        Log.v(TAG, "Checking circ-int for [" + x + ", " + y + "]");

                        double dcx = drx - velX;
                        double dcy = dry - velY;
                        double a = velX * velX + velY * velY;
                        double b = 2 * (velX * dcx + velY * dcy);
                        double c = (dcx * dcx + dcy * dcy) - mRadius * mRadius;
                        double disc = b * b - 4 * a * c;

                        if (disc < 0) {
                            Log.v(TAG, "No intersection, disc: " + disc);
                        } else {
                            double sq = Math.sqrt(disc);
//                            double t0 = (-b - sq) / (2 * a);
                            double t1 = (-b + sq) / (2 * a);

                            //double xt0 = x - velX + t0 * velX;
                           // double yt0 = y - velY + t0 * velY;
                            double xt1 = x - velX + t1 * velX;
                            double yt1 = y - velY + t1 * velY;

                            //Log.v(TAG, "Bounce 0: [" + xt0 + ", " + yt0 + "]");
//                            Log.v(TAG, "Bounce 1: [" + xt1 + ", " + yt1 + "]");

                            bounce = true;
                            bounceX = Math.round((float) xt1);
                            bounceY = Math.round((float) yt1);

                            // http://www.3dkingdoms.com/weekly/weekly.php?a=2
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
                }


                if (bounce) {
                    parent.addBounce(bounceX, bounceY);
//                    triangleColorNew = randomHSVColor();  // moved to successful bounce
                }

            }

            void render(Canvas canvas) {
                canvas.drawCircle(x, y, r, paint);
            }
        }



        class Board {

            static final boolean AVOID_DUPLICATE_SIDES = true;
            static final int     MAX_TRIANGLE_COUNT = 20;

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
//                trianglePaint.setColor(Color.rgb(0,0,0));
                trianglePaint.setStyle(Paint.Style.FILL);
                trianglePaint.setAntiAlias(true);

                // Initialize two bounces for an initial triangle cursor
                addBounce(1, 0);
                addBounce(mWidth, 1);  // the 1's are a small trick to avoid closed outline
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
                    for (int i = 0; i < triangleCount; i++) {
                        triangles[i].renderOutline(canvas, linePaint);
                    }

                } else {
                    update();

                    for (int i = 0; i < triangleCount; i++) {
                        int pos = (triangleIterator + i) % triangleCount;
                        triangles[pos].render(canvas, trianglePaint);
                    }

                    if (DRAW_BALL) ball.render(canvas);
                    if (USE_TRIANGLE_CURSOR) renderTriangleCursor(canvas);
                    if (DISPLAY_BOUNCES) {
                        for (int i = bounceCount - 1; i >= 0; i--) {
                            bounces[i].render(canvas);
                        }
                    }
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


//                trianglePaint.setColor(Color.argb(CURSOR_TRIANGLE_ALPHA, 255,255,255) );
//                trianglePaint.setColor(backgroundColorsAlpha[hour]);
//                trianglePaint.setColor(Color.HSVToColor(CURSOR_TRIANGLE_ALPHA, new float[]{ (float) (360 * Math.random()), 1.0f, 1.0f } ));


//                float midX = Math.min(a.x, b.x) + 0.5f * Math.abs(a.x - b.x);
//                float midY = Math.min(a.y, b.y) + 0.5f * Math.abs(a.y - b.y);
//                trianglePaint.setShader(new LinearGradient(ball.x, ball.y, midX, midY,
//                        Color.argb(255, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.MIRROR));
//                canvas.drawPath(path, trianglePaint);
//                trianglePaint.setShader(null);  // revert back  @TODO: improve this with a dedicated cursor paint

                trianglePaint.setColor(triangleColorNew);
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

                // New current color
                triangleColorNew = randomHSVColor();

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

            void reset() {
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

                    mTime.setToNow();
                    int hour = mTime.hour;

//                    currentColor = Color.argb(COLOR_TRIANGLE_ALPHA, 255,255,255);
                    currentColor = triangleColorNew;
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

            final static double TAU_MINUS_3_8 = -0.75 * Math.PI;
            final static double TAU_MINUS_1_8 = -0.25 * Math.PI;
            final static double TAU_PLUS_1_8 = 0.25 * Math.PI;
            final static double TAU_PLUS_3_8 = 0.75 * Math.PI;

            int x, y;
            int side;  // 0 for top... 3 for left (clockwise)
            int color;
//            int colorHSV;

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

//                newColor();
                color = triangleColorNew;
            }

            void newColor() {

//                color = Color.HSVToColor( COLOR_TRIANGLE_ALPHA, new float[]{ (float) (360 * Math.random()), 1.0f, 1.0f } );
                color = triangleColorNew;
//                color = Color.argb(COLOR_TRIANGLE_ALPHA,
//                        (int) (255 * Math.random()),
//                        (int) (255 * Math.random()),
//                        (int) (255 * Math.random())
//                        );
            }

            void render(Canvas canvas) {
                canvas.drawCircle(x, y, 5, mTextDigitsPaintInteractive);
            }

        }

    }

    int randomHSVColor() {
        return Color.HSVToColor( COLOR_TRIANGLE_ALPHA, new float[]{ (float) (360 * Math.random()), 1.0f, 1.0f } );
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
        final static boolean DEBUG_TO_CONSOLE = false;

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
                Log.v(TAG, "Sensor " + name + " not available in this device");
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
            if (DEBUG_TO_CONSOLE) Log.i(TAG, "Registered " + name + ": " + isRegistered);
            return isRegistered;
        }

        boolean unregister() {
            if (!isActive) return false;
            if (!isRegistered) return false;
            manager.unregisterListener(listener);
            isRegistered = false;
            if (DEBUG_TO_CONSOLE) Log.i(TAG, "Unregistered " + name);
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