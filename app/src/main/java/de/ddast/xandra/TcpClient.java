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

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

class TcpClient {

    private static final String TAG  = "Xandra/TcpClient";
    private static final boolean DEBUG = true;

    private final String mServerAddr;
    private final int mPort;
    private Socket mSocket;
    private OutputStream mOutput;
    private boolean mRunning = false;

    TcpClient(String serverAddr, int port) {
        mServerAddr = serverAddr;
        mPort = port;
    }

    void connect() {
        mRunning = true;
        new ConnectAsync().execute();
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
            if (!result) {
                disconnect();
            }
            mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
        }
    }

    void disconnect() {
        if (DEBUG) {
            Log.d(TAG, "Disconnecting");
        }
        mRunning = false;

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

    boolean isConnected() {
        return mRunning;
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
            new MainActivity.SendBytes().execute(utf8Repr);
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






    private class SendHeartbeat implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "Send heartbeat");
            if (mPowerManager.isScreenOn()) {
                new MainActivity.SendBytes().execute(new byte[]{HEARTBEAT});
            }
            if (!isConnected()) {
                Log.e(TAG, "Heartbeat error, try to reconnect");
                new MainActivity.ConnectToServer().execute();
            } else {
                mHandler.postDelayed(mSendHeartbeat, HEARTBEAT_INTERVAL);
            }
        }
    }
}
