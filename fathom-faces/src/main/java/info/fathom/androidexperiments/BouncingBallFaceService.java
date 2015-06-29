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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;



/**
 * Created by Jose Luis on 29/06/2015.
 */
public class BouncingBallFaceService extends CanvasWatchFaceService implements SensorEventListener {

    private static final String TAG = "BouncingBallFaceService";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
//    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final int BACKGROUND_COLOR = Color.BLACK;
        private static final int TEXT_COLOR = Color.WHITE;

        private static final float FRICTION = 0.97f;
        private static final float ACCEL_REDUCTION_FACTOR = 0.25f;

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };



        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mAmbient;
        private Time mTime;

        private Paint mTextPaint;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

        private float mBallRadius;
        private float mBallX, mBallY;
        private float mBallVelX, mBallVelY;
        private Paint mBallPaint;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(BouncingBallFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(BouncingBallFaceService.this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

            mTextPaint = new Paint();
            mTextPaint.setColor(TEXT_COLOR);
            mTextPaint.setTypeface(NORMAL_TYPEFACE);
            mTextPaint.setAntiAlias(false);

            mBallPaint = new Paint();
            mBallPaint.setColor(Color.WHITE);
            mBallPaint.setAntiAlias(true);

            mTime  = new Time();
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
            mSensorManager.unregisterListener(BouncingBallFaceService.this);  // registering should prob go into onVisibilityChanged()
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

            mBallRadius = 0.10f * width;
            mBallVelX = 0;
            mBallVelY = 0;
            mBallX = mCenterX;
            mBallY = mCenterY;

            mTextPaint.setTextSize(0.10f * mHeight);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR);

                canvas.drawText("Ambient mode", 50, 50, mTextPaint);

            } else {
                // update ball position
                mBallVelX += ACCEL_REDUCTION_FACTOR * -gravity[0];
                mBallVelY += ACCEL_REDUCTION_FACTOR * gravity[1];

                mBallVelX *= FRICTION;
                mBallVelY *= FRICTION;

                mBallX += mBallVelX;
                mBallY += mBallVelY;

                if (mBallX > mWidth) {
                    mBallX = mWidth - (mBallX - mWidth);
                    mBallVelX = -mBallVelX;
                } else if (mBallX < 0) {
                    mBallX = -mBallX;
                    mBallVelX = -mBallVelX;
                }

                if (mBallY > mHeight) {
                    mBallY = mHeight - (mBallY - mHeight);
                    mBallVelY = -mBallVelY;
                } else if (mBallY < 0) {
                    mBallY = -mBallY;
                    mBallVelY = -mBallVelY;
                }

                canvas.drawColor(BACKGROUND_COLOR); // background
                canvas.drawCircle(mBallX, mBallY, mBallRadius, mBallPaint);

            }

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
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

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            BouncingBallFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            BouncingBallFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

    }




    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;

    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];

    // using the one in the engine
//    public final void onCreate(Bundle savedInstanceState) {
//
//    }




    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
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

            // Remove the gravity contribution with the high-pass filter.
            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];
        }




    }


}
