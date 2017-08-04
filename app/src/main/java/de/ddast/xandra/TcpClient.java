/*
 * Copyright (C) 2017  Dennis Dast
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
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

class TcpClient {
    static final byte LEFTCLICK          = (byte)0x00;
    static final byte MIDDLECLICK        = (byte)0x01;
    static final byte RIGHTCLICK         = (byte)0x02;
    static final byte WHEELUP            = (byte)0x03;
    static final byte WHEELDOWN          = (byte)0x04;
    static final byte CTRL               = (byte)0x05;
    static final byte SUP                = (byte)0x06;
    static final byte ALT                = (byte)0x07;
    static final byte BACKSPACE          = (byte)0x08;
    static final byte ESCAPE             = (byte)0x09;
    static final byte TAB                = (byte)0x0a;
    static final byte LEFT               = (byte)0x0b;
    static final byte DOWN               = (byte)0x0c;
    static final byte UP                 = (byte)0x0d;
    static final byte RIGHT              = (byte)0x0e;
    static final byte VOLDN              = (byte)0x0f;
    static final byte VOLUP              = (byte)0x10;
    static final byte VOLTOG             = (byte)0x11;
    static final byte INS                = (byte)0x12;
    static final byte DEL                = (byte)0x13;
    static final byte HOME               = (byte)0x14;
    static final byte END                = (byte)0x15;
    static final byte PGUP               = (byte)0x16;
    static final byte PGDN               = (byte)0x17;
    static final byte F1                 = (byte)0x18;
    static final byte F2                 = (byte)0x19;
    static final byte F3                 = (byte)0x1a;
    static final byte F4                 = (byte)0x1b;
    static final byte F5                 = (byte)0x1c;
    static final byte F6                 = (byte)0x1d;
    static final byte F7                 = (byte)0x1e;
    static final byte F8                 = (byte)0x1f;
    static final byte F9                 = (byte)0x20;
    static final byte F10                = (byte)0x21;
    static final byte F11                = (byte)0x22;
    static final byte F12                = (byte)0x23;
    static final byte LEFTMOUSEDOWN      = (byte)0x24;
    static final byte LEFTMOUSEUP        = (byte)0x25;

    private static final String TAG  = "TcpClient";
    private static final boolean DEBUG = true;
    private static final long HEARTBEAT_INTERVAL = 1000L;
    private static final byte HEARTBEAT          = (byte)0x00;

    private final String mServerAddr;
    private final int mPort;
    private final TcpClientObserver mTcpClientObserver;
    private Socket mSocket;
    private OutputStream mOutput;
    private boolean mRunning = false;
    private Handler mHandler;

    TcpClient(String serverAddr, int port, TcpClientObserver tcpClientObserver) {
        mServerAddr = serverAddr;
        mPort = port;
        mTcpClientObserver = tcpClientObserver;
        mHandler = new Handler();
    }

    void connect() {
        mRunning = true;
        mHandler.removeCallbacks(mSendHeartbeat);
        new ConnectAsync().execute();
    }

    void shutdown() {
        mHandler.removeCallbacks(mSendHeartbeat);
        disconnect();
    }

    boolean isConnected() {
        return mRunning;
    }

    void sendMouse(int distanceX, int distanceY) {
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

    void sendUTF8(String s) {
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

    void sendSpecialKey(byte b) {
        if (DEBUG) {
            Log.d(TAG, "Sending special key" + String.valueOf(b));
        }
        new SendBytes().execute(new byte[] {(byte)0xfc, (byte)0x80, (byte)0x80, (byte)0x80,
                (byte)0x80, (byte)(0x80 | b)});
    }

    private class ConnectAsync extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected void onPreExecute() {
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
                mTcpClientObserver.connectionEstablished();
            }
            mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
        }
    }

    private void disconnect() {
        if (DEBUG) {
            Log.d(TAG, "Disconnecting");
        }
        mRunning = false;
        mTcpClientObserver.connectionLost();

        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "IO error while closing");
            }
        } else if (DEBUG) {
            Log.d(TAG, "Socket was null on disconnect");
        }

        mOutput = null;
        mSocket = null;
    }

    private Runnable mSendHeartbeat = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "Sending heartbeat");
            }
            new SendBytes().execute(new byte[]{HEARTBEAT});
            if (!isConnected()) {
                Log.e(TAG, "Connection error on heartbeat, try to reconnect");
                connect();
            } else {
                mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
            }
        }
    };

    private class SendBytes extends AsyncTask<byte[], Void, Boolean> {
        @Override
        protected Boolean doInBackground(byte[]... bA) {
            if (!isConnected() || mOutput == null) {
                Log.e(TAG, "Tried to send, but not yet connected");
                return false;
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
}