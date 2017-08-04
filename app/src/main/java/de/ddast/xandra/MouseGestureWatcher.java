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

import android.os.CountDownTimer;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;

class MouseGestureWatcher {
    private final long mTapdelay;
    private final float mTaptol, mSensitivity, mAcceleration, mScrollThreshold;
    private final TcpClient mTcpClient;
    private final CountDownTimer mLeftClickCountDown;
    private final CountDownTimer mRightClickCountDown;
    private int mPointerID1 = MotionEvent.INVALID_POINTER_ID;
    private int mPointerID2 = MotionEvent.INVALID_POINTER_ID;
    private float initX, initY, mOldX, mOldY, mOldY2;
    private double accumulatedDiffY;
    private long mDownEventTime, oldTime;
    private boolean isMultiTouchGesture, isDragAndDrop;

    MouseGestureWatcher(TcpClient tcpClient, long tapdelay, float taptol, float sensitivity,
                        float acceleration, float scrollThreshold) {
        mTcpClient = tcpClient;
        mTapdelay = tapdelay;
        mTaptol = taptol;
        mSensitivity = sensitivity;
        mAcceleration = acceleration;
        mScrollThreshold = scrollThreshold;

        mLeftClickCountDown = new CountDownTimer(mTapdelay, 2*mTapdelay) {
            @Override
            public void onTick(long millisUntilFinished) {}
            @Override
            public void onFinish() {
                isDragAndDrop = false;
                mTcpClient.sendSpecialKey(TcpClient.LEFTMOUSEUP);
            }
        };

        mRightClickCountDown = new CountDownTimer(2*mTapdelay, 3*mTapdelay) {
            @Override
            public void onTick(long millisUntilFinished) {}
            @Override
            public void onFinish() {
                if (singleTouchHasNotMoved()) {
                    isDragAndDrop = false;
                    mTcpClient.sendSpecialKey(TcpClient.RIGHTCLICK);
                }
            }
        };
    }

    boolean processTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);

        switch(action) {
            case (MotionEvent.ACTION_DOWN): {
                isMultiTouchGesture = false;
                initFirstPointer(event);
                mRightClickCountDown.start();
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
                    mLeftClickCountDown.cancel();
                }
                sendMouseOrScrollEvent(event);
                return true;
            }
            case (MotionEvent.ACTION_POINTER_UP): {
                rearrangePointerIDs(event);
                return true;
            }
            case (MotionEvent.ACTION_UP): {
                mRightClickCountDown.cancel();
                if (isDragAndDrop) {
                    mLeftClickCountDown.cancel();
                    isDragAndDrop = false;
                    mTcpClient.sendSpecialKey(TcpClient.LEFTMOUSEUP);
                }
                if (isSingleTouchTap(event)) {
                    mTcpClient.sendSpecialKey(TcpClient.LEFTMOUSEDOWN);
                    isDragAndDrop = true;
                    mLeftClickCountDown.start();
                }
                mPointerID1 = MotionEvent.INVALID_POINTER_ID;
                return true;
            }
            case (MotionEvent.ACTION_CANCEL): {
                mLeftClickCountDown.cancel();
                mRightClickCountDown.cancel();
                mPointerID1 = MotionEvent.INVALID_POINTER_ID;
                mPointerID2 = MotionEvent.INVALID_POINTER_ID;
                return true;
            }
            default:
                return false;
        }
    }

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
            mTcpClient.sendMouse(calcMouseMovement(diffX, diffT),
                    calcMouseMovement(diffY, diffT));
        } else if (event.getPointerCount() == 2) {
            final int pointerIndex2 = event.findPointerIndex(mPointerID2);
            float diffY2 = event.getY(pointerIndex2) - mOldY2;
            mOldY2 = event.getY(pointerIndex2);
            float maxDiffY = Math.abs(diffY) > Math.abs(diffY2) ? diffY : diffY2;
            accumulatedDiffY += acceleratedMouseMovement(maxDiffY, diffT);
            while (accumulatedDiffY < -mScrollThreshold) {
                mTcpClient.sendSpecialKey(TcpClient.WHEELUP);
                accumulatedDiffY += mScrollThreshold;
            }
            while (accumulatedDiffY > mScrollThreshold) {
                mTcpClient.sendSpecialKey(TcpClient.WHEELDOWN);
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
}