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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PORT = 64296;
    private static final int HEARTBEAT = 0;
    private static final long HEARTBEAT_INTERVAL = 3000L;
    private static final char BACKSPACE = 1;

    private NoCursorEditText mBufferEdit;
    private String mServerAddr;
    private Socket mSocket;
    private OutputStream mOutput;
    private Handler mHandler;
    private Runnable mSendHeartbeat;

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

    private void sendUTF8(String s) {
        if (mOutput == null) {
            Log.e(TAG, "Tried to send, but not yet connected");
            mBufferEdit.setEnabled(false);
            mBufferEdit.setText(R.string.notconnected);
            return;
        }

        try {
            byte[] utf8Repr = s.getBytes("UTF8");
            mOutput.write(utf8Repr);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Encoding failure");
        } catch (IOException e) {
            Log.e(TAG, "IO error while sending");
            mBufferEdit.setEnabled(false);
            mBufferEdit.setText(R.string.notconnected);
        }
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
            if (mOutput == null) {
                Log.e(TAG, "Not yet connected on heartbeat, try to connect");
                new ConnectToServer().execute();
            } else {
                try {
                    mOutput.write(HEARTBEAT);
                    mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
                } catch (IOException e) {
                    Log.e(TAG, "IO error while heartbeating, try to reconnect");
                    new ConnectToServer().execute();
                }
            }
        }
    }

    private class AddedTextWatcher implements TextWatcher {
        private boolean ignore = false;

        @Override
        public void afterTextChanged(Editable s) {
            // s must not be empty, but always contains one space to detect further backspace input
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
                StringBuilder backspaces = new StringBuilder();
                for (int i = 0; i < before; ++i) {
                    backspaces.append(BACKSPACE);
                }
                sendUTF8(backspaces.toString());
            }
        }
    }
}


