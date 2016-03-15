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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG              = "MainActivity";

    private static final long HEARTBEAT_INTERVAL = 3000L;

    private static final byte MOUSEEVENT         = (byte)0xff;
    private static final int MOUSEEVENTLEN       = 9;

    private static final byte HEARTBEAT          = (byte)0x00;
    private static final byte LEFTCLICK          = (byte)0x01;
    private static final byte RIGHTCLICK         = (byte)0x02;
    private static final byte WHEELUP            = (byte)0x03;
    private static final byte WHEELDOWN          = (byte)0x04;
    private static final byte BACKSPACE          = (byte)0x05;
    private static final byte ESCAPE             = (byte)0x06;
    private static final byte TAB                = (byte)0x07;
    private static final byte LEFT               = (byte)0x08;
    private static final byte DOWN               = (byte)0x09;
    private static final byte UP                 = (byte)0x0b;
    private static final byte RIGHT              = (byte)0x0c;
    private static final byte CTRL               = (byte)0x0d;
    private static final byte SUP                = (byte)0x0e;
    private static final byte ALT                = (byte)0x0f;

    private int mPort;
    private long mTapdelay;
    private float mTaptol;
    private float mSensitivity;
    private float mScrollThreshold;
    private NoCursorEditText mBufferEdit;
    private LinearLayout mLayoutKeys;
    private Button mToggleButton;
    private String mServerAddr;
    private Socket mSocket;
    private OutputStream mOutput;
    private Handler mHandler;
    private Runnable mSendHeartbeat;
    private MouseGestureDetector mMouseGestureDetector;

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

        mPort            = Integer.valueOf(sharedPreferences.getString(
                                           this.getString(R.string.pref_port), ""));
        mTapdelay        = Long.valueOf(sharedPreferences.getString(
                                        this.getString(R.string.pref_tapdelay), ""));
        mTaptol          = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_taptol), ""));
        mSensitivity     = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_sensitivity), ""));
        mScrollThreshold = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_scrollthreshold), ""));

        mServerAddr    = getIntent().getStringExtra(ConnectActivity.SERVERADDR);
        mBufferEdit    = (NoCursorEditText)findViewById(R.id.buffer_edit);
        mHandler       = new Handler();
        mSendHeartbeat = new SendHeartbeat();

        mBufferEdit.setHorizontallyScrolling(true);  // does not work in xml for some reason
        mBufferEdit.addTextChangedListener(new AddedTextWatcher());

        mMouseGestureDetector = new MouseGestureDetector();

        initKeyboardButtons();
    }

    private void initKeyboardButtons() {
        mLayoutKeys = (LinearLayout)findViewById(R.id.layout_keys);
        mToggleButton = (Button)findViewById(R.id.button_togglekeys);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newVisibility = mLayoutKeys.getVisibility() == View.VISIBLE ? View.GONE :
                        View.VISIBLE;
                mLayoutKeys.setVisibility(newVisibility);
            }
        });

        Button escButton = (Button)findViewById(R.id.button_esc);
        escButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {ESCAPE});
            }
        });

        Button tabButton = (Button)findViewById(R.id.button_tab);
        tabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {TAB});
            }
        });

        Button ctrlButton = (Button)findViewById(R.id.button_ctrl);
        ctrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {CTRL});
            }
        });

        Button supButton = (Button)findViewById(R.id.button_sup);
        supButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {SUP});
            }
        });

        Button altButton = (Button)findViewById(R.id.button_alt);
        altButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {ALT});
            }
        });

        Button leftButton = (Button)findViewById(R.id.button_left);
        leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {LEFT});
            }
        });

        Button downButton = (Button)findViewById(R.id.button_down);
        downButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {DOWN});
            }
        });

        Button upButton = (Button)findViewById(R.id.button_up);
        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {UP});
            }
        });

        Button rightButton = (Button)findViewById(R.id.button_right);
        rightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBytes(new byte[] {RIGHT});
            }
        });
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
        super.onPause();

        setUiToDisconnected();
        mHandler.removeCallbacks(mSendHeartbeat);

        if (mSocket == null) {
            return;
        }
        try {
            Log.i(TAG, "Closing connection");
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "IO error while closing");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        new ConnectToServer().execute();
    }

    private void sendMouse(int distanceX, int distanceY) {
        byte[] bA = ByteBuffer.allocate(MOUSEEVENTLEN).put(MOUSEEVENT)
                                                      .putInt(distanceX)
                                                      .putInt(distanceY).array();
        sendBytes(bA);
    }

    private void sendUTF8(String s) {
        try {
            byte[] utf8Repr = s.getBytes("UTF8");
            sendBytes(utf8Repr);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Encoding failure");
        }
    }

    private boolean sendBytes(byte[] bA) {
        if (mOutput == null) {
            Log.e(TAG, "Tried to send, but not yet connected");
            setUiToDisconnected();
            return false;
        }

        try {
            mOutput.write(bA);
        } catch (IOException e) {
            Log.e(TAG, "IO error while sending");
            setUiToDisconnected();
            return false;
        }
        return true;
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

    private void showSoftKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private class ConnectToServer extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            Log.i(TAG, "Connecting to " + mServerAddr);
            setUiToDisconnected();
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
                setUiToDisconnected();
            }
            mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
        }
    }

    private class SendHeartbeat implements Runnable {
        @Override
        public void run() {
            if (!sendBytes(new byte[] {HEARTBEAT})) {
                Log.e(TAG, "Hearbeat error, try to reconnect");
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
                byte[] bA = new byte[before];
                java.util.Arrays.fill(bA, BACKSPACE);
                sendBytes(bA);
            }
        }
    }

    private class MouseGestureDetector {
        private int mPointerID1 = MotionEvent.INVALID_POINTER_ID;
        private int mPointerID2 = MotionEvent.INVALID_POINTER_ID;
        private float initX;
        private float initY;
        private float mOldX;
        private float mOldY;
        private float mOldY2;
        private float accumulatedDiffY;
        private long mDownEventTime;

        private boolean processTouchEvent(MotionEvent event) {
            int action = MotionEventCompat.getActionMasked(event);

            switch(action) {
                case (MotionEvent.ACTION_DOWN): {
                    final int pointerIndex = event.getActionIndex();
                    mPointerID1 = event.getPointerId(pointerIndex);
                    initX = mOldX = event.getX(pointerIndex);
                    initY = mOldY = event.getY(pointerIndex);
                    mDownEventTime = event.getEventTime();
                    return true;
                }
                case (MotionEvent.ACTION_POINTER_DOWN): {
                    if (event.getPointerCount() == 2) {
                        final int pointerIndex = event.getActionIndex();
                        mPointerID2 = event.getPointerId(pointerIndex);
                        mOldY2 = event.getY(pointerIndex);
                        accumulatedDiffY = 0.0f;
                    }
                    return true;
                }
                case (MotionEvent.ACTION_MOVE): {
                    final int pointerIndex = event.findPointerIndex(mPointerID1);
                    float diffX = event.getX(pointerIndex) - mOldX;
                    float diffY = event.getY(pointerIndex) - mOldY;
                    mOldX = event.getX(pointerIndex);
                    mOldY = event.getY(pointerIndex);
                    if (event.getPointerCount() == 1) {
                        sendMouse(Math.round(mSensitivity * diffX),
                                  Math.round(mSensitivity * diffY));
                    } else {
                        final int pointerIndex2 = event.findPointerIndex(mPointerID2);
                        float diffY2 = event.getY(pointerIndex2) - mOldY2;
                        mOldY2 = event.getY(pointerIndex2);
                        accumulatedDiffY += 0.5f * (diffY + diffY2);
                        if (accumulatedDiffY < -mScrollThreshold) {
                            sendBytes(new byte[]{WHEELUP});
                            accumulatedDiffY = 0.f;
                        } else if (accumulatedDiffY > mScrollThreshold) {
                            sendBytes(new byte[]{WHEELDOWN});
                            accumulatedDiffY = 0.f;
                        }
                    }
                    return true;
                }
                case (MotionEvent.ACTION_POINTER_UP): {
                    final int pointerIndex = event.getActionIndex();
                    final int pointerId = event.getPointerId(pointerIndex);
                    if (pointerId == mPointerID1) {
                        mPointerID1 = mPointerID2;
                        final int pointerIndex1 = event.findPointerIndex(mPointerID1);
                        mOldX = event.getX(pointerIndex1);
                        mOldY = event.getY(pointerIndex1);
                        mPointerID2 = MotionEvent.INVALID_POINTER_ID;
                    } else if (pointerId != mPointerID2) {
                        return true;
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
                    return true;
                }
                case (MotionEvent.ACTION_UP): {
                    final int pointerIndex = event.findPointerIndex(mPointerID1);
                    if ((Math.abs(event.getX(pointerIndex) - initX) < mTaptol) &&
                            (Math.abs(event.getY(pointerIndex) - initY) < mTaptol)) {
                        if (event.getEventTime() - mDownEventTime < mTapdelay) {
                            sendBytes(new byte[]{LEFTCLICK});
                        } else {
                            sendBytes(new byte[]{RIGHTCLICK});
                        }
                    }
                    mPointerID1 = MotionEvent.INVALID_POINTER_ID;
                    return true;
                }
                case (MotionEvent.ACTION_CANCEL): {
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


