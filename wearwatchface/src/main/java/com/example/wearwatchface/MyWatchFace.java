/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.wearwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;

import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.text.DateFormat;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */




    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener
    {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        static final String COLON_STRING = ":";

        //Paint mBackgroundPaint;
        Paint mHandPaint;
        //boolean mAmbient;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDateTextPaint;
        Paint mAmPmPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;

        boolean mAmbient;
        final String LOG_TAG = CanvasWatchFaceService.Engine.class.getSimpleName();
        final String requestDataPath = "/wearable-request-path";

        DateFormat mMediumDateFormat;
        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayofWeekFormat;

        float mXOffset;
        float mYOffset;
        float mLineHeight;
        float m_xMargin;
        float mRectDimen;
        float mDateMargin;
        float mTempMargin;

        //asset and temperatures sent from the mobile app
        Asset mIconAsset;
        Bitmap mIconBitmap;
        String mHighTemp;
        String mLowTemp;

        String mAmString;
        String mPmString;

        String mBestNodeId;
        String capabilityName;

        Time mTime;



        //Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();

                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();

                invalidate();
            }
        };

        int mTapCount;


        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        //Capability Listener
        CapabilityApi.CapabilityListener requestCapabilityListener = new CapabilityApi.CapabilityListener() {
            @Override
            public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
                Log.v(LOG_TAG, "Capability Changed!");
                updateCapability(capabilityInfo);
            }
        };



        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            //mHandPaint = new Paint();
            //mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            //mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            //mHandPaint.setAntiAlias(true);
            //mHandPaint.setStrokeCap(Paint.Cap.ROUND);


            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.digital_subText));
            mAmPmPaint = new Paint();
            mAmPmPaint = createTextPaint(resources.getColor(R.color.digital_subText));
            mHighTempPaint = new Paint();
            mHighTempPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(resources.getColor(R.color.digital_subText));

            mCalendar = Calendar.getInstance();

            mAmString = resources.getString(R.string.am_string);
            mPmString = resources.getString(R.string.pm_string);

            mHighTemp = "00";
            mLowTemp = "00";

            mDate = new Date();
            capabilityName = "mobile_request"; //same name as the capability defined on the handheld
            mBestNodeId = null;
            initFormats();




            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }


        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            m_xMargin = resources.getDimension(isRound
                    ? R.dimen.digital_x_margin_round : R.dimen.digital_x_margin);
            mLineHeight = resources.getDimension(isRound
                    ? R.dimen.digital_lineHeight_round : R.dimen.digital_lineHeight);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_dateText_size_round : R.dimen.digital_dateText_size);
            float am_pm_textSize = resources.getDimension(isRound
                    ? R.dimen.digital_ampm_size_round : R.dimen.digital_ampm_size);
            float highTempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_highTemp_size_round : R.dimen.digital_highTemp_size);
            float lowTempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_lowTemp_size_round : R.dimen.digital_lowTemp_size);
            mRectDimen = resources.getDimension(R.dimen.digital_rect_dimen);
            mDateMargin = resources.getDimension(isRound
                    ? R.dimen.digital_x_date_margin_round : R.dimen.digital_x_date_margin);
            mTempMargin = resources.getDimension(isRound
                    ? R.dimen.digital_temp_gap_margin_round : R.dimen.digital_temp_gap_margin);

            mTextPaint.setTextSize(textSize);
            mDateTextPaint.setTextSize(dateTextSize);
            mAmPmPaint.setTextSize(am_pm_textSize);
            mHighTempPaint.setTextSize(highTempTextSize);
            mLowTempPaint.setTextSize(lowTempTextSize);


        }


        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
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
                if (mLowBitAmbient) {
                    //mHandPaint.setAntiAlias(!inAmbientMode);
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                    mAmPmPaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //mTime.setToNow();

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = android.text.format.DateFormat.is24HourFormat(MyWatchFace.this);
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float Time_X = mXOffset + m_xMargin;
            float Time_Y = mYOffset;
            String hourString;

            if(is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if(hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }


            canvas.drawText(hourString, Time_X, Time_Y, mTextPaint);
            Time_X += mTextPaint.measureText(hourString);
            canvas.drawText(COLON_STRING, Time_X, Time_Y, mTextPaint);
            Time_X += mTextPaint.measureText(COLON_STRING);

            String minutes = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            String amPmString = getAmPmString(mCalendar.get(Calendar.AM_PM));
            canvas.drawText(minutes, Time_X, Time_Y, mTextPaint);
            Time_X += mTextPaint.measureText(minutes) + mTextPaint.measureText(COLON_STRING);
            canvas.drawText(amPmString, Time_X, Time_Y, mAmPmPaint);

            float Date_X = mXOffset + mDateMargin;
            float Date_Y = mYOffset + mLineHeight;
            String dayOfWeek = mDayofWeekFormat.format(mDate) + ", " ;

            canvas.drawText(dayOfWeek, Date_X, Date_Y, mDateTextPaint);
            Date_X += mDateTextPaint.measureText(dayOfWeek);
            canvas.drawText(mMediumDateFormat.format(mDate), Date_X, Date_Y, mDateTextPaint);


            float weather_X = mXOffset;
            float weather_Y = mYOffset + mDateTextPaint.measureText(amPmString);

            if(mIconBitmap != null) {
                Rect rect = new Rect(
                        (int) weather_X, (int) weather_Y,
                        (int) weather_X + (int) mRectDimen, (int) weather_Y + (int) mRectDimen);
                canvas.drawBitmap(mIconBitmap, null, rect, null);
                weather_X += (rect.width() + mTempMargin);
                weather_Y += (rect.width() / 2);

                canvas.drawText(mHighTemp, weather_X, weather_Y, mHighTempPaint);
                weather_X += mHighTempPaint.measureText(mHighTemp) + mTempMargin;
                canvas.drawText(mLowTemp, weather_X, weather_Y, mLowTempPaint);
            }


            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            /*float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint);*/
        }


        public String formatTwoDigitNumber(int num) {
            return String.format("%02d", num);
        }

        public String getAmPmString(int AmPm) {
            return AmPm == Calendar.AM ? mAmString : mPmString;
        }


        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
/*        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }*/

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        /*private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }*/


        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.v(LOG_TAG, "Data Changed called.");
            for (DataEvent data : dataEvents) {
                String path = data.getDataItem().getUri().getPath();
                String weather_path = MyWatchFace.this.getString(R.string.data_path);
                if(weather_path.equals(path)) {
                    Log.v(LOG_TAG, "Received Data Item, extracting contents now");
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(data.getDataItem());

                    mHighTemp = dataMapItem.getDataMap().getString(MyWatchFace.this.getString(R.string.high_key));
                    mLowTemp = dataMapItem.getDataMap().getString(MyWatchFace.this.getString(R.string.low_key));
                    mIconAsset = dataMapItem.getDataMap().getAsset(MyWatchFace.this.getString(R.string.asset_key));
                    if(mIconAsset != null) {
                        new loadBitmapAsyncTask().execute(mIconAsset);
                    }

                    Log.v(LOG_TAG, "Received Data Item extraction complete?");
                } else {
                    Log.v(LOG_TAG, "Unrecognized path: " + path);
                }
            }
        }

        private class loadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {


            @Override
            protected Bitmap doInBackground(Asset...params) {
                if(params.length > 0) {
                    Asset asset = params[0];

                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                            mGoogleApiClient, asset).await().getInputStream();

                    if (assetInputStream == null) {
                        Log.w(LOG_TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    // decode the stream into a bitmap
                    return BitmapFactory.decodeStream(assetInputStream);
                } else {
                    Log.e(LOG_TAG, "No valid Asset to decode/Asset must be non-null.");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if(bitmap != null) {
                    //set the bitmap to the global variable to draw
                    mIconBitmap = bitmap;
                }
            }

        }


        //helper method for initializing formats
            public void initFormats() {
                mDayofWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                mDayofWeekFormat.setCalendar(mCalendar);
                mMediumDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
                mMediumDateFormat.setCalendar(mCalendar);
            }


            //GoogleApiClient.ConnectionCallbacks required calls

            public void onConnected(Bundle connectionHint) {
                //called when connection succeeded
                Log.v(LOG_TAG + " GoogleApiClient", "Connection succeed.");

                //connect a listener for the DataLayerApi
                Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

                //Capability Listener
                setupNodes();
                Wearable.CapabilityApi.addCapabilityListener(mGoogleApiClient, requestCapabilityListener, capabilityName);
            }


        @Override
        public void onConnectionSuspended(int i) {
            //called when connection somehow disconnected
            Log.v(LOG_TAG + " GoogleApiClient", "Connection somehow disconnected. Cause : " + i );

        }


        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            //called when connection failed
            Log.v(LOG_TAG + " GoogleApiClient", "Connection failed. Reason : " + connectionResult.toString());

        }


        public void setupNodes() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Wearable.CapabilityApi.getCapability(
                            mGoogleApiClient, capabilityName,
                            CapabilityApi.FILTER_REACHABLE)
                            .setResultCallback(new ResultCallback<CapabilityApi.GetCapabilityResult>() {
                                @Override
                                public void onResult(@NonNull CapabilityApi.GetCapabilityResult getCapabilityResult) {
                                    if(getCapabilityResult.getStatus().isSuccess()) {
                                        Log.v(LOG_TAG, "Capability detected");
                                        updateCapability(getCapabilityResult.getCapability());
                                    } else {
                                        Log.v(LOG_TAG, "Unable to detect a Capability");
                                    }
                                }
                            });
                }
            }).start();
        }



        public void updateCapability(CapabilityInfo capabilityInfo) {
            Set<Node> capableNodes = capabilityInfo.getNodes();

            mBestNodeId = findBestNodeId(capableNodes);

            //send message here
            requestWeatherData();
        }

        public String findBestNodeId(Set<Node> nodes) {
            String bestNodeId = null;
            for(Node node : nodes) {
                if(node.isNearby()) {
                    return node.getId();
                }
                bestNodeId = node.getId();
            }

            return bestNodeId;
        }


        public void requestWeatherData() {
            if(mBestNodeId != null) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mBestNodeId, requestDataPath, null)
                        .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                                if (sendMessageResult.getStatus().isSuccess()) {
                                    Log.v(LOG_TAG, "Send Message success! " + sendMessageResult.getStatus().getStatusCode());
                                } else {
                                    Log.v(LOG_TAG, "Uh-oh, messaged sending was unsuccessful. "
                                            + sendMessageResult.getStatus().getStatusCode());
                                }
                            }
                        });
            } else {
                Log.v(LOG_TAG, "Apparently, there are no capable nodes nearby or available to connect to.");
            }
        }












        /**
         * Handle updating the time periodically in interactive mode.
         */
        /*private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }*/



        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            /*if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }*/

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();

                //update Calendar TimeZone
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    //unregister the listener when not active
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }




            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
