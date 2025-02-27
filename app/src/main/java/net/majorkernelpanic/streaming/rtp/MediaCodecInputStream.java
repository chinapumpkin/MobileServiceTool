/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.majorkernelpanic.streaming.rtp;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !
 */
@SuppressLint("NewApi")
public class MediaCodecInputStream extends InputStream {

    public final String TAG = "MediaCodecInputStream";
    public MediaFormat mMediaFormat;
    private MediaCodec mMediaCodec = null;
    private BufferInfo mBufferInfo = new BufferInfo();
    private ByteBuffer[] mBuffers = null;
    private ByteBuffer mBuffer = null;
    private int mIndex = -1;
    private boolean mClosed = false;

    public MediaCodecInputStream(MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;
        mBuffers = mMediaCodec.getOutputBuffers();
    }

    @Override
    public void close() {
        mClosed = true;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int min = 0;
        try {
            if (mBuffer == null) {
                while (!Thread.interrupted() && !mClosed) {
                    //the original is 500000
                    mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, -1);
                   // Log.d(TAG, "" + mIndex);
                    if (mIndex >= 0) {
                     //   Log.d(TAG, "Index: " + mIndex + " Time: " + mBufferInfo.presentationTimeUs + " size: " + mBufferInfo.size);
                        mBuffer = mBuffers[mIndex];
                        mBuffer.position(0);
                        break;
                    } else if (mIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        // this shoud not come when encoding
                        mBuffers = mMediaCodec.getOutputBuffers();
                    } else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // this status indicate the output format of codec is changed
                        // this should come only once before actual encoded data
                        // but this status never come on Android4.3 or less
                        // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
                        mMediaFormat = mMediaCodec.getOutputFormat(); //API>=16
                        Log.i(TAG, "format changed! new format:" + mMediaFormat.toString());
                        // get output format from codec and pass them to muxer
                        // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                      
                       //mTrackIndex used in  	muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                        //which eaual to    mBuffer.get(buffer, offset, min);
                      //  mTrackIndex = muxer.addTrack(mMediaFormat);


                    } else if (mIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.v(TAG, "No buffer available...");
                        //return 0;
                    } else {
                        Log.e(TAG, "Message: " + mIndex);
                        //return 0;
                    }
                }
            }

            if (mClosed) throw new IOException("This InputStream was closed");

            min = length < mBufferInfo.size - mBuffer.position() ? length : mBufferInfo.size - mBuffer.position();

            mBuffer.get(buffer, offset, min);

            if (mBuffer.position() >= mBufferInfo.size) {
                mMediaCodec.releaseOutputBuffer(mIndex, false);
                mBuffer = null;
            }

        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        return min;
    }

    public int available() {
        if (mBuffer != null)
            return mBufferInfo.size - mBuffer.position();
        else
            return 0;
    }

    public BufferInfo getLastBufferInfo() {
        return mBufferInfo;
    }

}
