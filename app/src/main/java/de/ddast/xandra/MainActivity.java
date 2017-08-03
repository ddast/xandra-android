/*
 * Copyright (C) 2016  Dennis Dast
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.ddast.xandra;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements TcpClientObserver {

    private static final String TAG              = "xandra";
    private static final boolean DEBUG           = false;

    private static final long HEARTBEAT_INTERVAL = 1000L;

    private static final byte HEARTBEAT          = (byte)0x00;

    private static final byte LEFTCLICK          = (byte)0x00;
    private static final byte MIDDLECLICK        = (byte)0x01;
    private static final byte RIGHTCLICK         = (byte)0x02;
    private static final byte WHEELUP            = (byte)0x03;
    private static final byte WHEELDOWN          = (byte)0x04;
    private static final byte CTRL               = (byte)0x05;
    private static final byte SUP                = (byte)0x06;
    private static final byte ALT                = (byte)0x07;
    private static final byte BACKSPACE          = (byte)0x08;
    private static final byte ESCAPE             = (byte)0x09;
    private static final byte TAB                = (byte)0x0a;
    private static final byte LEFT               = (byte)0x0b;
    private static final byte DOWN               = (byte)0x0c;
    private static final byte UP                 = (byte)0x0d;
    private static final byte RIGHT              = (byte)0x0e;
    private static final byte VOLDN              = (byte)0x0f;
    private static final byte VOLUP              = (byte)0x10;
    private static final byte VOLTOG             = (byte)0x11;
    private static final byte INS                = (byte)0x12;
    private static final byte DEL                = (byte)0x13;
    private static final byte HOME               = (byte)0x14;
    private static final byte END                = (byte)0x15;
    private static final byte PGUP               = (byte)0x16;
    private static final byte PGDN               = (byte)0x17;
    private static final byte F1                 = (byte)0x18;
    private static final byte F2                 = (byte)0x19;
    private static final byte F3                 = (byte)0x1a;
    private static final byte F4                 = (byte)0x1b;
    private static final byte F5                 = (byte)0x1c;
    private static final byte F6                 = (byte)0x1d;
    private static final byte F7                 = (byte)0x1e;
    private static final byte F8                 = (byte)0x1f;
    private static final byte F9                 = (byte)0x20;
    private static final byte F10                = (byte)0x21;
    private static final byte F11                = (byte)0x22;
    private static final byte F12                = (byte)0x23;
    private static final byte LEFTMOUSEDOWN      = (byte)0x24;
    private static final byte LEFTMOUSEUP        = (byte)0x25;

    private int mPort;
    private long mTapdelay;
    private float mTaptol, mSensitivity, mAcceleration, mScrollThreshold;
    private int mSpecialKeysVisibility = View.GONE;
    private NoCursorEditText mBufferEdit;
    private HorizontalScrollView mLayoutKeys;
    private Button mToggleButton;
    private String mServerAddr;
    private Socket mSocket;
    private OutputStream mOutput;
    private Handler mHandler;
    private Runnable mSendHeartbeat;
    private MouseGestureDetector mMouseGestureDetector;
    private PowerManager mPowerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = sharedPreferences.getString(this.getString(R.string.pref_theme), "");
        if (theme.equals("dark")) {
            setTheme(R.style.DarkThemeNoActionbar);
        } else if (theme.equals("light")) {
            setTheme(R.style.LightThemeNoActionbar);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (sharedPreferences.getBoolean(this.getString(R.string.pref_lock), false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        mPort            = Integer.valueOf(sharedPreferences.getString(
                                           this.getString(R.string.pref_port), ""));
        mTapdelay        = Long.valueOf(sharedPreferences.getString(
                                        this.getString(R.string.pref_tapdelay), ""));
        mTaptol          = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_taptol), ""));
        mSensitivity     = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_sensitivity), ""));
        mAcceleration    = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_acceleration), ""));
        mScrollThreshold = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_scrollthreshold), ""));

        mServerAddr    = getIntent().getStringExtra(ConnectActivity.SERVERADDR);
        mBufferEdit    = (NoCursorEditText)findViewById(R.id.buffer_edit);
        mHandler       = new Handler();
        mSendHeartbeat = new SendHeartbeat();

        mBufferEdit.setHorizontallyScrolling(true);  // does not work in xml for some reason
        mBufferEdit.setText(" ");
        mBufferEdit.addTextChangedListener(new AddedTextWatcher());

        mMouseGestureDetector = new MouseGestureDetector();

        initKeyboardButtons();
    }

    private void initKeyboardButtons() {
        mLayoutKeys = (HorizontalScrollView)findViewById(R.id.layout_keys);
        mToggleButton = (Button)findViewById(R.id.button_togglekeys);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpecialKeysVisibility = mLayoutKeys.getVisibility() == View.VISIBLE ? View.GONE :
                                                                                       View.VISIBLE;
                mLayoutKeys.setVisibility(mSpecialKeysVisibility);
            }
        });

        Button escButton = (Button)findViewById(R.id.button_esc);
        escButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(ESCAPE);
            }
        });

        Button tabButton = (Button)findViewById(R.id.button_tab);
        tabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(TAB);
            }
        });

        Button ctrlButton = (Button)findViewById(R.id.button_ctrl);
        ctrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(CTRL);
            }
        });

        Button supButton = (Button)findViewById(R.id.button_sup);
        supButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(SUP);
            }
        });

        Button altButton = (Button)findViewById(R.id.button_alt);
        altButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(ALT);
            }
        });

        Button leftButton = (Button)findViewById(R.id.button_left);
        leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(LEFT);
            }
        });

        Button downButton = (Button)findViewById(R.id.button_down);
        downButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(DOWN);
            }
        });

        Button upButton = (Button)findViewById(R.id.button_up);
        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(UP);
            }
        });

        Button rightButton = (Button)findViewById(R.id.button_right);
        rightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(RIGHT);
            }
        });

        Button voldnButton = (Button)findViewById(R.id.button_voldn);
        voldnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(VOLDN);
            }
        });

        Button volupButton = (Button)findViewById(R.id.button_volup);
        volupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(VOLUP);
            }
        });

        Button voltogButton = (Button)findViewById(R.id.button_voltog);
        voltogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(VOLTOG);
            }
        });

        Button midButton = (Button)findViewById(R.id.button_mid);
        midButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(MIDDLECLICK);
            }
        });

        Button insButton = (Button)findViewById(R.id.button_ins);
        insButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(INS);
            }
        });

        Button delButton = (Button)findViewById(R.id.button_del);
        delButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(DEL);
            }
        });

        Button homeButton = (Button)findViewById(R.id.button_home);
        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(HOME);
            }
        });

        Button endButton = (Button)findViewById(R.id.button_end);
        endButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(END);
            }
        });

        Button pgupButton = (Button)findViewById(R.id.button_pgup);
        pgupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(PGUP);
            }
        });

        Button pgdnButton = (Button)findViewById(R.id.button_pgdn);
        pgdnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(PGDN);
            }
        });

        Button f1tButton = (Button)findViewById(R.id.button_f1);
        f1tButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F1);
            }
        });

        Button f2Button = (Button)findViewById(R.id.button_f2);
        f2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F2);
            }
        });

        Button f3Button = (Button)findViewById(R.id.button_f3);
        f3Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F3);
            }
        });

        Button f4Button = (Button)findViewById(R.id.button_f4);
        f4Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F4);
            }
        });

        Button f5Button = (Button)findViewById(R.id.button_f5);
        f5Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F5);
            }
        });

        Button f6Button = (Button)findViewById(R.id.button_f6);
        f6Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F6);
            }
        });

        Button f7Button = (Button)findViewById(R.id.button_f7);
        f7Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F7);
            }
        });

        Button f8Button = (Button)findViewById(R.id.button_f8);
        f8Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F8);
            }
        });

        Button f9Button = (Button)findViewById(R.id.button_f9);
        f9Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F9);
            }
        });

        Button f10Button = (Button)findViewById(R.id.button_f10);
        f10Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F10);
            }
        });

        Button f11Button = (Button)findViewById(R.id.button_f11);
        f11Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F11);
            }
        });

        Button f12Button = (Button)findViewById(R.id.button_f12);
        f12Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSpecialKey(F12);
            }
        });
    }

    public void connectionEstablished() {
        // react on successful connection
    }

    public void connectionLost() {
        // react on lost connection
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (mBufferEdit.isEnabled()) {
            return mMouseGestureDetector.processTouchEvent(event) || super.onTouchEvent(event);
        } else {
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onPause() {
        if (DEBUG) {
            Log.d(TAG, "Disconnecting due to onPause()");
        }
        super.onPause();
        mHandler.removeCallbacks(mSendHeartbeat);
        disconnect();
    }

    @Override
    protected void onResume() {
        if (DEBUG) {
            Log.d(TAG, "Connect due to onResume()");
        }
        super.onResume();
        mBufferEdit.setText(" ");
        new ConnectToServer().execute();
    }

    private void sendMouse(int distanceX, int distanceY) {
        if (DEBUG) {
            Log.d(TAG, "Sending mouse event: distanceX " + String.valueOf(distanceX)
                  + ", distanceY " + String.valueOf(distanceY));
        }
        boolean isNegX = distanceX < 0;
        boolean isNegY = distanceY < 0;
        distanceX = Math.abs(distanceX) & 0xfff;
        distanceY = Math.abs(distanceY) & 0xfff;
        byte[] bA = ByteBuffer.allocate(5)
                              .put((byte)(0xf8 | (isNegX ? 0x02 : 0x00) | distanceX>>>11))
                              .put((byte)(0x80 | distanceX>>>5 & 0x3f))
                              .put((byte)(0x80 | (distanceX & 0x1f)<<1 | (isNegY ? 0x01 : 0x00)))
                              .put((byte)(0x80 | distanceY>>>6))
                              .put((byte)(0x80 | distanceY & 0x3f))
                              .array();
        new SendBytes().execute(bA);
    }

    private void disconnect() {
        if (DEBUG) {
            Log.d(TAG, "Disconnecting");
        }
        setUiToDisconnected();
        if (mSocket == null) {
            Log.i(TAG, "mSocket is null while trying to disconnect");
            return;
        }
        try {
            Log.i(TAG, "Closing connection");
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "IO error while closing");
        }
        mSocket = null;
    }

    private void sendUTF8(String s) {
        if (DEBUG) {
            Log.d(TAG, "Sending UTF8 character" + s);
        }
        try {
            byte[] utf8Repr = s.getBytes("UTF8");
            new SendBytes().execute(utf8Repr);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Encoding failure");
        }
    }

    private void sendSpecialKey(byte b) {
        if (DEBUG) {
            Log.d(TAG, "Sending special key" + String.valueOf(b));
        }
        new SendBytes().execute(new byte[] {(byte)0xfc, (byte)0x80, (byte)0x80, (byte)0x80,
                                (byte)0x80, (byte)(0x80 | b)});
    }

    private void setUiToDisconnected() {
        mBufferEdit.setEnabled(false);
        mBufferEdit.setText(R.string.notconnected);
        mLayoutKeys.setVisibility(View.GONE);
        mToggleButton.setEnabled(false);
    }

    private void setUiToConnected() {
        mBufferEdit.setEnabled(true);
        mBufferEdit.setText(" ");
        showSoftKeyboard(mBufferEdit);
        mToggleButton.setEnabled(true);
    }

    private boolean isConnected() {
        return mBufferEdit.isEnabled();
    }

    private void showSoftKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
        if (mSpecialKeysVisibility == View.VISIBLE) {
            mLayoutKeys = (HorizontalScrollView) findViewById(R.id.layout_keys);
            mLayoutKeys.setVisibility(View.VISIBLE);
        }
    }

    private class SendBytes extends AsyncTask<byte[], Void, Boolean> {
        @Override
        protected Boolean doInBackground(byte[]... bA) {
            if (mOutput == null) {
                Log.e(TAG, "Tried to send, but not yet connected");
                return true;
            }

            try {
                mOutput.write(bA[0]);
            } catch (IOException e) {
                Log.e(TAG, "IO error while sending");
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                if (DEBUG) {
                    Log.d(TAG, "Disconnecting due to error while sending");
                }
                disconnect();
            }
        }
    }

    private class ConnectToServer extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            if (DEBUG) {
                Log.d(TAG, "Disconnecting before connecting");
            }
            disconnect();
            Log.i(TAG, "Connecting to " + mServerAddr);
        }

        @Override
        protected Boolean doInBackground(Void...  params) {
            try {
                mSocket = new Socket(mServerAddr, mPort);
                mSocket.setTcpNoDelay(true);
                mOutput = mSocket.getOutputStream();
                Log.i(TAG, "Connected to " + mServerAddr);
                return true;
            } catch(UnknownHostException e) {
                Log.e(TAG, "Unknown host: " + mServerAddr);
                return false;
            } catch(IOException e) {
                Log.e(TAG, "IO error while connecting");
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                setUiToConnected();
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Disconnecting due to error while connecting");
                }
                disconnect();
            }
            mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
        }
    }

    private class SendHeartbeat implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "Send heartbeat");
            if (mPowerManager.isScreenOn()) {
                new SendBytes().execute(new byte[]{HEARTBEAT});
            }
            if (!isConnected()) {
                Log.e(TAG, "Heartbeat error, try to reconnect");
                new ConnectToServer().execute();
            } else {
                mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
            }
        }
    }

    private class AddedTextWatcher implements TextWatcher {
        private boolean ignore = false;

        @Override
        public void afterTextChanged(Editable s) {
            // s must not be empty.  It always contains one space to detect further backspace input.
            if (s.toString().contains("\n") || s.length() == 0) {
                ignore = true;
                s.replace(0, s.length(), " ", 0, 1);
            }
        }

        @Override
        public void beforeTextChanged (CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged (CharSequence s, int start, int before, int count) {
            if (ignore) {
                ignore = false;
                return;
            }
            if (before == 0) {
                sendUTF8(s.subSequence(start, start + count).toString());
            } else if (count == 0) {
                for (int i = 0; i < before; ++i) {
                    sendSpecialKey(BACKSPACE);
                }
            }
        }
    }

    private class MouseGestureDetector {
        private int mPointerID1 = MotionEvent.INVALID_POINTER_ID;
        private int mPointerID2 = MotionEvent.INVALID_POINTER_ID;
        private float initX, initY, mOldX, mOldY, mOldY2;
        private double accumulatedDiffY;
        private long mDownEventTime, oldTime;
        boolean isMultiTouchGesture, isDragAndDrop;

        private double acceleratedMouseMovement(float len, long time) {
            double velocity = (len < 0.0f ? -1.0 : 1.0)
                    * Math.pow(Math.abs(10.0*len/time), mAcceleration);
            return velocity*time/10.0;
        }

        private int calcMouseMovement(float len, long time) {
            return (int) Math.round(mSensitivity*acceleratedMouseMovement(len, time));
        }

        private void initFirstPointer(MotionEvent event) {
            final int pointerIndex = event.getActionIndex();
            mPointerID1 = event.getPointerId(pointerIndex);
            initX = mOldX = event.getX(pointerIndex);
            initY = mOldY = event.getY(pointerIndex);
            mDownEventTime = oldTime = event.getEventTime();
        }

        private void initSecondPointer(MotionEvent event) {
            final int pointerIndex = event.getActionIndex();
            mPointerID2 = event.getPointerId(pointerIndex);
            mOldY2 = event.getY(pointerIndex);
            accumulatedDiffY = 0.0;
        }

        private void sendMouseOrScrollEvent(MotionEvent event) {
            final int pointerIndex = event.findPointerIndex(mPointerID1);
            float diffX = event.getX(pointerIndex) - mOldX;
            float diffY = event.getY(pointerIndex) - mOldY;
            mOldX = event.getX(pointerIndex);
            mOldY = event.getY(pointerIndex);
            long diffT = event.getEventTime() - oldTime;
            oldTime = event.getEventTime();
            if (event.getPointerCount() == 1) {
                sendMouse(calcMouseMovement(diffX, diffT),
                          calcMouseMovement(diffY, diffT));
            } else if (event.getPointerCount() == 2) {
                final int pointerIndex2 = event.findPointerIndex(mPointerID2);
                float diffY2 = event.getY(pointerIndex2) - mOldY2;
                mOldY2 = event.getY(pointerIndex2);
                float maxDiffY = Math.abs(diffY) > Math.abs(diffY2) ? diffY : diffY2;
                accumulatedDiffY += acceleratedMouseMovement(maxDiffY, diffT);
                while (accumulatedDiffY < -mScrollThreshold) {
                    sendSpecialKey(WHEELUP);
                    accumulatedDiffY += mScrollThreshold;
                }
                while (accumulatedDiffY > mScrollThreshold) {
                    sendSpecialKey(WHEELDOWN);
                    accumulatedDiffY -= mScrollThreshold;
                }
            }
        }

        private void rearrangePointerIDs(MotionEvent event) {
            final int pointerIndex = event.getActionIndex();
            final int pointerId = event.getPointerId(pointerIndex);
            if (pointerId != mPointerID1 && pointerId != mPointerID2) {
                return;
            }
            if (pointerId == mPointerID1) {
                mPointerID1 = mPointerID2;
                final int pointerIndex1 = event.findPointerIndex(mPointerID1);
                mOldX = event.getX(pointerIndex1);
                mOldY = event.getY(pointerIndex1);
                mPointerID2 = MotionEvent.INVALID_POINTER_ID;
            }
            if (event.getPointerCount() > 2) {
                final int pointerIndex1 = event.findPointerIndex(mPointerID1);
                int newPointerIndex = MotionEvent.INVALID_POINTER_ID;
                for (int i = 0; i < event.getPointerCount(); ++i) {
                    if (i != pointerIndex && i != pointerIndex1) {
                        newPointerIndex = i;
                        break;
                    }
                }
                mPointerID2 = event.getPointerId(newPointerIndex);
                mOldY2 = event.getY(newPointerIndex);
            }
        }

        private boolean isSingleTouchTap(MotionEvent event) {
            final int pointerIndex = event.findPointerIndex(mPointerID1);
            mOldX = event.getX(pointerIndex);
            mOldY = event.getY(pointerIndex);
            return singleTouchHasNotMoved() && (event.getEventTime() - mDownEventTime < mTapdelay);
        }

        private boolean singleTouchHasNotMoved() {
            return (!isMultiTouchGesture &&
                    (Math.abs(mOldX - initX) < mTaptol) &&
                    (Math.abs(mOldY - initY) < mTaptol));
        }


        private CountDownTimer leftClickCountDown = new CountDownTimer(mTapdelay, 2*mTapdelay) {
            @Override
            public void onTick(long millisUntilFinished) {}
            @Override
            public void onFinish() {
                isDragAndDrop = false;
                sendSpecialKey(LEFTMOUSEUP);
            }
        };

        private CountDownTimer rightClickCountDown = new CountDownTimer(2*mTapdelay, 3*mTapdelay) {
            @Override
            public void onTick(long millisUntilFinished) {}
            @Override
            public void onFinish() {
                if (singleTouchHasNotMoved()) {
                    isDragAndDrop = false;
                    sendSpecialKey(RIGHTCLICK);
                }
            }
        };

        private boolean processTouchEvent(MotionEvent event) {
            int action = MotionEventCompat.getActionMasked(event);

            switch(action) {
                case (MotionEvent.ACTION_DOWN): {
                    isMultiTouchGesture = false;
                    initFirstPointer(event);
                    rightClickCountDown.start();
                    return true;
                }
                case (MotionEvent.ACTION_POINTER_DOWN): {
                    isMultiTouchGesture = true;
                    if (event.getPointerCount() == 2) {
                        initSecondPointer(event);
                    }
                    return true;
                }
                case (MotionEvent.ACTION_MOVE): {
                    if (isDragAndDrop) {
                        leftClickCountDown.cancel();
                    }
                    sendMouseOrScrollEvent(event);
                    return true;
                }
                case (MotionEvent.ACTION_POINTER_UP): {
                    rearrangePointerIDs(event);
                    return true;
                }
                case (MotionEvent.ACTION_UP): {
                    rightClickCountDown.cancel();
                    if (isDragAndDrop) {
                        leftClickCountDown.cancel();
                        isDragAndDrop = false;
                        sendSpecialKey(LEFTMOUSEUP);
                    }
                    if (isSingleTouchTap(event)) {
                        sendSpecialKey(LEFTMOUSEDOWN);
                        isDragAndDrop = true;
                        leftClickCountDown.start();
                    }
                    mPointerID1 = MotionEvent.INVALID_POINTER_ID;
                    return true;
                }
                case (MotionEvent.ACTION_CANCEL): {
                    leftClickCountDown.cancel();
                    rightClickCountDown.cancel();
                    mPointerID1 = MotionEvent.INVALID_POINTER_ID;
                    mPointerID2 = MotionEvent.INVALID_POINTER_ID;
                    return true;
                }
                default:
                    return false;
            }
        }
    }
}
