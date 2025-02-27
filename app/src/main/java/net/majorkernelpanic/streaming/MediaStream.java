package net.majorkernelpanic.streaming;

import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Random;

/**
 * Created by dengcanrong on 15/7/4.
 */
public  class MediaStream implements IMediaStream {
    protected static final String TAG = "MediaStream";

    /** Raw audio/video will be encoded using the MediaRecorder API. */
    public static final byte MODE_MEDIARECORDER_API = 0x01;

    /** Raw audio/video will be encoded using the MediaCodec API with buffers. */
    public static final byte MODE_MEDIACODEC_API = 0x02;

    /** Raw audio/video will be encoded using the MediaCode API with a surface. */
    public static final byte MODE_MEDIACODEC_API_2 = 0x05;

    /** A LocalSocket will be used to feed the MediaRecorder object */
    public static final byte PIPE_API_LS = 0x01;

    /** A ParcelFileDescriptor will be used to feed the MediaRecorder object */
    public static final byte PIPE_API_PFD = 0x02;

    /** Prefix that will be used for all shared preferences saved by libstreaming */
    protected static final String PREF_PREFIX = "libstreaming-";

    /** The packetizer that will read the output of the camera and send RTP packets over the networked. */
    protected AbstractPacketizer mPacketizer = null;

    protected static byte sSuggestedMode = MODE_MEDIARECORDER_API;
    protected byte mMode, mRequestedMode;

    /**
     * Starting lollipop the LocalSocket API cannot be used to feed a MediaRecorder object.
     * You can force what API to use to create the pipe that feeds it with this constant
     * by using  {@link #PIPE_API_LS} and {@link #PIPE_API_PFD}.
     */
    protected final static byte sPipeApi;

    protected boolean mStreaming = false, mConfigured = false;
    protected int mRtpPort = 0, mRtcpPort = 0;
    protected byte mChannelIdentifier = 0;
    protected OutputStream mOutputStream = null;
    protected InetAddress mDestination;

    protected ParcelFileDescriptor[] mParcelFileDescriptors;
    protected ParcelFileDescriptor mParcelRead;
    protected ParcelFileDescriptor mParcelWrite;

    protected LocalSocket mReceiver, mSender = null;
    private LocalServerSocket mLss = null;
    private int mSocketId;

    private int mTTL = 64;

    protected MediaRecorder mMediaRecorder;
    protected MediaCodec mMediaCodec;

    static {
        // We determine whether or not the MediaCodec API should be used
        try {
            Class.forName("android.media.MediaCodec");
            // Will be set to MODE_MEDIACODEC_API at some point...
            sSuggestedMode = MODE_MEDIACODEC_API;
            Log.i(TAG, "Phone supports the MediaCodec API");
        } catch (ClassNotFoundException e) {
            sSuggestedMode = MODE_MEDIARECORDER_API;
            Log.i(TAG,"Phone does not support the MediaCodec API");
        }

        // Starting lollipop, the LocalSocket API cannot be used anymore to feed
        // a MediaRecorder object for security reasons
        // KITKAT android 4.4
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            sPipeApi = PIPE_API_PFD;
        } else {
            sPipeApi = PIPE_API_LS;
        }
    }
    public MediaStream() {
        mRequestedMode = sSuggestedMode;
        mMode = sSuggestedMode;
    }

    @Override
    public byte getStreamingMethod() {
        return mMode;
    }

    @Override
    public void setStreamingMethod(byte mode) {
        mRequestedMode = mode;
    }

    @Override
    public AbstractPacketizer getPacketizer() {
        return mPacketizer;
    }

    @Override
    public void encodeWithMediaRecorder() throws IOException {
        Log.d(TAG,"encodewithMeidaRecorder");
    }

    @Override
    public void encodeWithMediaCodec() throws IOException {
        Log.d(TAG,"encodewithMediaCodec");
    }

    @Override
    public void createSockets() throws IOException {
        if (sPipeApi == PIPE_API_LS) {
            // MediaRecorder

            final String LOCAL_ADDR = "net.majorkernelpanic.streaming-";

            for (int i=0;i<10;i++) {
                try {
                    mSocketId = new Random().nextInt();
                    mLss = new LocalServerSocket(LOCAL_ADDR+mSocketId);
                    break;
                } catch (IOException e1) {}
            }

            mReceiver = new LocalSocket();
            mReceiver.connect( new LocalSocketAddress(LOCAL_ADDR+mSocketId));
            mReceiver.setReceiveBufferSize(500000);
            mReceiver.setSoTimeout(3000);
            mSender = mLss.accept();
            mSender.setSendBufferSize(500000);

        } else {//mediaCodec
            Log.e(TAG, "parcelFileDescriptors createPipe version = Lollipop");
            mParcelFileDescriptors = ParcelFileDescriptor.createPipe();
            mParcelRead = new ParcelFileDescriptor(mParcelFileDescriptors[0]);
            mParcelWrite = new ParcelFileDescriptor(mParcelFileDescriptors[1]);
        }
    }
    /**
     * Returns a description of the stream using SDP.
     * This method can only be called after {@link Stream#configure()}.
     * @throws IllegalStateException Thrown when {@link Stream#configure()} was not called.
     */
    public  String getSessionDescription(){
        return null;
    }

    @Override
    public void closeSockets() {
        if (sPipeApi == PIPE_API_LS) {
            try {
                mReceiver.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mSender.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mLss.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mLss = null;
            mSender = null;
            mReceiver = null;

        } else {
            try {
                if (mParcelRead != null) {
                    mParcelRead.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (mParcelWrite != null) {
                    mParcelWrite.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void configure() throws IllegalStateException, IOException {
        if (mStreaming) throw new IllegalStateException("Can't be called while streaming.");
        if (mPacketizer != null) {
            mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
            mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);
        }
        mMode = mRequestedMode;
        mConfigured = true;
    }

    @Override
    public void start() throws IllegalStateException, IOException {
        if (mDestination==null)
            throw new IllegalStateException("No destination ip address set for the stream !");

        if (mRtpPort<=0 || mRtcpPort<=0)
            throw new IllegalStateException("No destination ports set for the stream !");

        mPacketizer.setTimeToLive(mTTL);
        Log.d(TAG,"mmode:"+mMode+"suggestmode:"+sSuggestedMode+"requestmode"+mRequestedMode);
        // this one should be mmode
        if (mMode != MODE_MEDIARECORDER_API) {
            encodeWithMediaCodec();
        } else {
            encodeWithMediaRecorder();
        }
    }

    @Override
    public void stop() {
        if (mStreaming) {
            try {
                if (mMode==MODE_MEDIARECORDER_API) {
                    mMediaRecorder.stop();
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                    closeSockets();
                    mPacketizer.stop();
                } else {
                    mPacketizer.stop();
                    mMediaCodec.stop();
                    mMediaCodec.release();
                    mMediaCodec = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mStreaming = false;
        }

    }

    @Override
    public void setTimeToLive(int ttl) throws IOException {
        mTTL = ttl;
    }

    @Override
    public void setDestinationAddress(InetAddress dest) {
        mDestination = dest;
    }

    @Override
    public void setDestinationPorts(int dport) {
        if (dport % 2 == 1) {
            mRtpPort = dport-1;
            mRtcpPort = dport;
        } else {
            mRtpPort = dport;
            mRtcpPort = dport+1;
        }
    }

    @Override
    public void setDestinationPorts(int rtpPort, int rtcpPort) {
        mRtpPort = rtpPort;
        mRtcpPort = rtcpPort;
        mOutputStream = null;
    }

    @Override
    public void setOutputStream(OutputStream stream, byte channelIdentifier) {
        mOutputStream = stream;
        mChannelIdentifier = channelIdentifier;
    }

    @Override
    public int[] getLocalPorts() {
        return mPacketizer.getRtpSocket().getLocalPorts();
    }

    @Override
    public int[] getDestinationPorts() {
        return new int[] {
                mRtpPort,
                mRtcpPort
        };
    }

    @Override
    public int getSSRC() {
        return getPacketizer().getSSRC();
    }

    @Override
    public long getBitrate() {
        return !mStreaming ? 0 : mPacketizer.getRtpSocket().getBitrate();
    }




    @Override
    public boolean isStreaming() {
        return mStreaming;
    }
}
