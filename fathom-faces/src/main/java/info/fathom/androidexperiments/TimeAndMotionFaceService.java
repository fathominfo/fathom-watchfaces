/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class TimeAndMotionFaceService extends CanvasWatchFaceService {

    private static final String TAG = "TimeAndMotionFaceService";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final float SECONDS_RADIUS = 0.9f;  // expressed as a ratio of the screen 'radius'
        private static final float MINUTES_RADIUS = 0.86f;
        private static final float HOURS_RADIUS = 0.77f;
        private static final float INNER_MASK_RADIUS = 0.6f;  // the empty circle inside

        private static final int BACKGROUND_COLOR_INTERACTIVE = Color.WHITE;
        private static final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;

        private final int SECONDS_COLOR = Color.rgb(200, 200, 200);

        private final int MINUTES_COLOR_INTERACTIVE = Color.rgb(150, 150, 150);
        private final int MINUTES_COLOR_AMBIENT = Color.rgb(150, 150, 150);

        private final int HOURS_COLOR_INTERACTIVE = Color.rgb(100, 100, 100);
        private final int HOURS_COLOR_AMBIENT = Color.rgb(200, 200, 200);

        private final float[] CIRCLE_RADII = {0.62f, 0.40f, 0.15f, 0.05f};
        private final static float CIRCLE_STROKE_THICKNESS = 0.015f;  // expressed as a ratio of the screen 'radius'
        private final int CIRCLES_COLOR = Color.argb(64, 150, 150, 150);

        private final int TEXT_COLOR_INTERACTIVE = Color.argb(127, 175, 175, 175);
        private final int TEXT_COLOR_AMBIENT = Color.WHITE;
        private static final float TEXT_HEIGHT = 0.2f;  // as a factor of screen height

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
//        private boolean mLowBitAmbient;
//        private boolean mBurnInProtection;
        private boolean mAmbient;

        private Time mTime;

//        private Paint mBackgroundPaint;
        private Paint mSecondsPaint,
                mMinutesPaintInteractive, mMinutesPaintAmbient,
                mHoursPaintInteractive, mHoursPaintAmbient;
        private Paint mCircleMaskPaintInteractive, mCircleMaskPaintAmbient;
        private Paint mCirclesPaint;

        private Paint mTextPaintInteractive, mTextPaintAmbient;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

        private float mSecondsHandRadius, mMinutesHandRadius,
                mHoursHandRadius, mInnerMaskRadius;

        private int mCirclesStrokeWidth = 0;

        private final Rect textBounds = new Rect();
        private float mTextHeight;







        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TimeAndMotionFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

//            mBackgroundPaint = new Paint();
//            mBackgroundPaint.setColor(Color.WHITE);

            mSecondsPaint = new Paint();
            mSecondsPaint.setColor(SECONDS_COLOR);
            mSecondsPaint.setAntiAlias(true);
//            mSecondsPaint.setStyle(Paint.Style.FILL);  // FILL is default

            mMinutesPaintInteractive = new Paint();
            mMinutesPaintInteractive.setColor(MINUTES_COLOR_INTERACTIVE);
            mMinutesPaintInteractive.setAntiAlias(true);

            mMinutesPaintAmbient = new Paint();
            mMinutesPaintAmbient.setColor(MINUTES_COLOR_AMBIENT);
            mMinutesPaintAmbient.setAntiAlias(false);

            mHoursPaintInteractive = new Paint();
            mHoursPaintInteractive.setColor(HOURS_COLOR_INTERACTIVE);
            mHoursPaintInteractive.setAntiAlias(true);

            mHoursPaintAmbient = new Paint();
            mHoursPaintAmbient.setColor(HOURS_COLOR_AMBIENT);
            mHoursPaintAmbient.setAntiAlias(false);

            mCircleMaskPaintInteractive = new Paint();
            mCircleMaskPaintInteractive.setColor(BACKGROUND_COLOR_INTERACTIVE);
            mCircleMaskPaintInteractive.setAntiAlias(true);

            mCircleMaskPaintAmbient = new Paint();
            mCircleMaskPaintAmbient.setColor(BACKGROUND_COLOR_AMBIENT);
            mCircleMaskPaintAmbient.setAntiAlias(false);

            mCirclesPaint = new Paint();
            mCirclesPaint.setColor(CIRCLES_COLOR);
            mCirclesPaint.setAntiAlias(true);
            mCirclesPaint.setStyle(Paint.Style.STROKE);
//            mCirclesPaint.setStrokeWidth(mCirclesStrokeWidth);  // moved to onSurfaceChanged

            mTextPaintInteractive = new Paint();
            mTextPaintInteractive.setColor(TEXT_COLOR_INTERACTIVE);
            mTextPaintInteractive.setTypeface(NORMAL_TYPEFACE);
            mTextPaintInteractive.setAntiAlias(true);

            mTextPaintAmbient = new Paint();
            mTextPaintAmbient.setColor(TEXT_COLOR_AMBIENT);
            mTextPaintAmbient.setTypeface(NORMAL_TYPEFACE);
            mTextPaintAmbient.setAntiAlias(false);

            mTime  = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
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

            mSecondsHandRadius = SECONDS_RADIUS * 0.5f * mWidth;
            mMinutesHandRadius = MINUTES_RADIUS * 0.5f * mWidth;
            mHoursHandRadius = HOURS_RADIUS * 0.5f * mWidth;
            mInnerMaskRadius = INNER_MASK_RADIUS * 0.5f * width;

            mCirclesStrokeWidth = (int) (CIRCLE_STROKE_THICKNESS * 0.5f * width);
            mCirclesPaint.setStrokeWidth(mCirclesStrokeWidth);

            mTextHeight = TEXT_HEIGHT * mHeight;
            mTextPaintInteractive.setTextSize(mTextHeight);
            mTextPaintAmbient.setTextSize(mTextHeight);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mTime.setToNow();

            /*
             * These calculations reflect the rotation in degrees per unit of
             * time, e.g. 360 / 60 = 6 and 360 / 12 = 30
             */
            final float secondsRotation = mTime.second * 6f;
            final float minutesRotation = mTime.minute * 6f;
            // account for the offset of the hour hand due to minutes of the hour.
            final float hourHandOffset = mTime.minute / 2f;
            final int hour1to12 = mTime.hour % 12 == 0 ? 12 : mTime.hour % 12;
            final float hoursRotation = (hour1to12 * 30f) + hourHandOffset;

//            Log.v("hour1to12:", Float.toString(hour1to12));
//            Log.v("hourHandOffset:", Float.toString(hourHandOffset));
//            Log.v("hoursRotation:", Float.toString(hoursRotation));

            // Start drawing watch elements
            canvas.save();

            if (mAmbient) {
                canvas.drawColor(BACKGROUND_COLOR_AMBIENT); // background

                canvas.drawCircle(mCenterX, mCenterY, 0.01f * mWidth, mHoursPaintAmbient);  // central dot

                mTextPaintAmbient.setTextAlign(Paint.Align.RIGHT);
                drawTextVerticallyCentered(canvas, mTextPaintAmbient,
                        hour1to12 > 9 ? Integer.toString(hour1to12) : "0" + Integer.toString(hour1to12),
                        mCenterX - 20, mCenterY);
                mTextPaintAmbient.setTextAlign(Paint.Align.LEFT);
                drawTextVerticallyCentered(canvas, mTextPaintAmbient,
                        mTime.minute > 9 ? Integer.toString(mTime.minute) : "0" + Integer.toString(mTime.minute),
                        mCenterX + 20, mCenterY);

            } else {
                canvas.drawColor(BACKGROUND_COLOR_INTERACTIVE);
                canvas.drawArc(mCenterX - mSecondsHandRadius,
                        mCenterY - mSecondsHandRadius,
                        mCenterX + mSecondsHandRadius,
                        mCenterY + mSecondsHandRadius,
                        270, secondsRotation,
                        true, mSecondsPaint);
                canvas.drawCircle(mCenterX, mCenterY,
                        mMinutesHandRadius, mCircleMaskPaintInteractive);
                canvas.drawArc(mCenterX - mMinutesHandRadius,
                        mCenterY - mMinutesHandRadius,
                        mCenterX + mMinutesHandRadius,
                        mCenterY + mMinutesHandRadius,
                        270, minutesRotation,
                        true, mMinutesPaintInteractive);
                canvas.drawCircle(mCenterX, mCenterY,
                        mHoursHandRadius, mCircleMaskPaintInteractive);
                canvas.drawArc(mCenterX - mHoursHandRadius,
                        mCenterY - mHoursHandRadius,
                        mCenterX + mHoursHandRadius,
                        mCenterY + mHoursHandRadius,
                        270, hoursRotation,
                        true, mHoursPaintInteractive);
                canvas.drawCircle(mCenterX, mCenterY,
                        mInnerMaskRadius, mCircleMaskPaintInteractive);
                canvas.drawCircle(mCenterX, mCenterY, 0.01f * mWidth, mHoursPaintInteractive);  // central dot

                // floating circles
                for (int i = 0; i < CIRCLE_RADII.length; i++) {
                    canvas.drawCircle(mCenterX, mCenterY, CIRCLE_RADII[i] * 0.5f * mWidth, mCirclesPaint);
                }

                mTextPaintInteractive.setTextAlign(Paint.Align.RIGHT);
                drawTextVerticallyCentered(canvas, mTextPaintInteractive,
                        hour1to12 > 9 ? Integer.toString(hour1to12) : "0" + Integer.toString(hour1to12),
                        mCenterX - 20, mCenterY);
                mTextPaintInteractive.setTextAlign(Paint.Align.LEFT);
                drawTextVerticallyCentered(canvas, mTextPaintInteractive,
                        mTime.minute > 9 ? Integer.toString(mTime.minute) : "0" + Integer.toString(mTime.minute),
                        mCenterX + 20, mCenterY);

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
            TimeAndMotionFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TimeAndMotionFaceService.this.unregisterReceiver(mTimeZoneReceiver);
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

        private void drawHand(Canvas canvas, float handLength, float radius, Paint paint) {
            canvas.drawRoundRect(mCenterX - radius,
                    mCenterY - handLength - radius,
                    mCenterX + radius,
                    mCenterY + radius,
                    radius, radius, paint);
        }

        // http://stackoverflow.com/a/24969713/1934487
        private void drawTextVerticallyCentered(Canvas canvas, Paint paint, String text, float cx, float cy){
            paint.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, cx, cy - textBounds.exactCenterY(), paint);
        }


    }
}
