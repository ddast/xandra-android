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
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG              = "MainActivity";
    private static final int PORT                = 64296;
    private static final byte HEARTBEAT          = (byte)0;
    private static final long HEARTBEAT_INTERVAL = 3000L;
    private static final byte BACKSPACE          = (byte)1;
    private static final byte LEFTCLICK          = (byte)2;
    private static final byte RIGHTCLICK         = (byte)3;
    private static final int MOUSEVENT           = 127;
    private static final int MOUSEVENTLEN        = 9;

    private NoCursorEditText mBufferEdit;
    private String mServerAddr;
    private Socket mSocket;
    private OutputStream mOutput;
    private Handler mHandler;
    private Runnable mSendHeartbeat;
    private GestureDetectorCompat mDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mServerAddr    = getIntent().getStringExtra(ConnectActivity.SERVERADDR);
        mBufferEdit    = (NoCursorEditText)findViewById(R.id.buffer_edit);
        mHandler       = new Handler();
        mSendHeartbeat = new SendHeartbeat();

        mBufferEdit.setHorizontallyScrolling(true);  // does not work in xml for some reason
        mBufferEdit.addTextChangedListener(new AddedTextWatcher());

        mDetector = new GestureDetectorCompat(this, new ScrollAndClickListener());
    }

    @Override
    protected void onPause() {
        super.onPause();

        mBufferEdit.setEnabled(false);
        mBufferEdit.setText(R.string.notconnected);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.mDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private void sendMouse(int distanceX, int distanceY) {
        byte[] bA = ByteBuffer.allocate(MOUSEVENTLEN).put((byte)MOUSEVENT)
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
            mBufferEdit.setEnabled(false);
            mBufferEdit.setText(R.string.notconnected);
            return false;
        }

        try {
            mOutput.write(bA);
        } catch (IOException e) {
            Log.e(TAG, "IO error while sending");
            mBufferEdit.setEnabled(false);
            mBufferEdit.setText(R.string.notconnected);
            return false;
        }
        return true;
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
            mBufferEdit.setEnabled(false);
            mBufferEdit.setText(R.string.connecting);
        }

        @Override
        protected Boolean doInBackground(Void...  params) {
            try {
                mSocket = new Socket(mServerAddr, PORT);
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
                mBufferEdit.setEnabled(true);
                mBufferEdit.setText(" ");
                showSoftKeyboard(mBufferEdit);
            } else {
                mBufferEdit.setEnabled(false);
                mBufferEdit.setText(R.string.notconnected);
            }
            mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
        }
    }

    private class SendHeartbeat implements Runnable {
        @Override
        public void run() {
            byte[] bA = new byte[1];
            bA[0] = HEARTBEAT;
            if (!sendBytes(bA)) {
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
        public void beforeTextChanged (CharSequence s, int start, int count, int after) {
        }

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

    private class ScrollAndClickListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            byte[] bA = new byte[1];
            bA[0] = RIGHTCLICK;
            sendBytes(bA);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                                float distanceY) {
            sendMouse(Math.round(distanceX), Math.round(distanceY));
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            byte[] bA = new byte[1];
            bA[0] = LEFTCLICK;
            sendBytes(bA);
            return true;
        }
    }
}


