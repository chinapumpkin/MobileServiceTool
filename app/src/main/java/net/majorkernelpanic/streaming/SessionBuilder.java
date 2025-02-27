package net.majorkernelpanic.streaming;

/**
 * Created by dengcanrong on 15/7/5.
 */

import android.content.Context;
import android.hardware.Camera;
import android.nfc.Tag;
import android.preference.PreferenceManager;
import android.util.Log;

import com.serenegiant.usb.UVCCamera;

import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.io.IOException;

import fi.oulu.tol.vgs4msc.handlers.LEDHandler;

/**
 * Call {@link #getInstance()} to get access to the SessionBuilder.
 */
public class SessionBuilder {

    public final static String TAG = "SessionBuilder";

    /** Can be used with {@link #setVideoEncoder}. */
    public final static int VIDEO_NONE = 0;

    /** Can be used with {@link #setVideoEncoder}. */
    public final static int VIDEO_H264 = 1;

    /** Can be used with {@link #setVideoEncoder}. */
    public final static int VIDEO_H263 = 2;

    /** Can be used with {@link #setAudioEncoder}. */
    public final static int AUDIO_NONE = 0;

    /** Can be used with {@link #setAudioEncoder}. */
    public final static int AUDIO_AMRNB = 3;

    /** Can be used with {@link #setAudioEncoder}. */
    public final static int AUDIO_AAC = 5;

    // Default configuration
    private VideoQuality mVideoQuality = VideoQuality.DEFAULT_VIDEO_QUALITY;
    private AudioQuality mAudioQuality = AudioQuality.DEFAULT_AUDIO_QUALITY;
    private Context mContext;
    private int mVideoEncoder = VIDEO_H263;
    private int mAudioEncoder = AUDIO_AMRNB;
  //  private int mCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
    private UVCCamera mCamera=null;
    private int mTimeToLive = 64;
   // private int mOrientation = 0;
    //private boolean mFlash = false;
    private SurfaceView mSurfaceView = null;
    private String mOrigin = null;
    private String mDestination = null;
    private Session.Callback mCallback = null;

    // Removes the default public constructor
    private SessionBuilder() {}

    // The SessionManager implements the singleton pattern
    private static volatile SessionBuilder sInstance = null;

    /**
     * Returns a reference to the {@link SessionBuilder}.
     * @return The reference to the {@link SessionBuilder}
     */
    public final static SessionBuilder getInstance() {
        if (sInstance == null) {
            synchronized (SessionBuilder.class) {
                if (sInstance == null) {
                    SessionBuilder.sInstance = new SessionBuilder();
                }
            }
        }
        return sInstance;
    }

    /**
     * Creates a new {@link Session}.
     * @return The new Session
     * @throws IOException
     */
    public Session build() {
        Session session;

        session = new Session();
        session.setOrigin(mOrigin);
        session.setDestination(mDestination);
        session.setTimeToLive(mTimeToLive);
        session.setCallback(mCallback);

        switch (mAudioEncoder) {
            case AUDIO_AAC:
                UVCAACStream stream = new UVCAACStream();
                session.addAudioTrack(stream);
                if (mContext!=null)
                    stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(mContext));
                break;
            case AUDIO_AMRNB:
                session.addAudioTrack(new UVCAMRNBStream());
                break;
        }

        switch (mVideoEncoder) {
            case VIDEO_H263:
                session.addVideoTrack(new UVCH263Stream(mCamera));
                break;
            case VIDEO_H264:
                UVCH264Stream stream = new UVCH264Stream(mCamera);
                if (mContext!=null)
                    stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(mContext));
                session.addVideoTrack(stream);
                break;
        }

        if (session.getVideoTrack()!=null) {
            UVCVideoStream video = session.getVideoTrack();
          //  video.setFlashState(mFlash);
            video.setVideoQuality(mVideoQuality);
            video.setSurfaceView(mSurfaceView);
           // video.setPreviewOrientation(mOrientation);
            video.setDestinationPorts(5006);
        }

        if (session.getAudioTrack()!=null) {
            UVCAudioStream audio = session.getAudioTrack();
            //audio.setAudioQuality(mAudioQuality);
            audio.setDestinationPorts(5004);
        }

        return session;

    }

    /**
     * Access to the context is needed for the H264Stream class to store some stuff in the SharedPreferences.
     * Note that you should pass the Application context, not the context of an Activity.
     **/
    public SessionBuilder setContext(Context context) {
        mContext = context;
        return this;
    }

    /** Sets the destination of the session. */
    public SessionBuilder setDestination(String destination) {
        mDestination = destination;
        return this;
    }

    /** Sets the origin of the session. It appears in the SDP of the session. */
    public SessionBuilder setOrigin(String origin) {
        mOrigin = origin;
        return this;
    }

    /** Sets the video stream quality. */
    public SessionBuilder setVideoQuality(VideoQuality quality) {
        mVideoQuality = quality.clone();// in order to use default quality . if use quality.clone, the videoQuality ==0
        return this;
    }

    /** Sets the audio encoder. */
    public SessionBuilder setAudioEncoder(int encoder) {
        mAudioEncoder = encoder;
        return this;
    }

    /** Sets the audio quality. */
    public SessionBuilder setAudioQuality(AudioQuality quality) {
        mAudioQuality = quality.clone();
        return this;
    }

    /** Sets the default video encoder. */
    public SessionBuilder setVideoEncoder(int encoder) {
        mVideoEncoder = encoder;
        return this;
    }

  /**  public SessionBuilder setFlashEnabled(boolean enabled) {
        mFlash = enabled;
        return this;
    }**/

    public SessionBuilder setCamera(UVCCamera camera) {
        mCamera = camera;
        return this;
    }

    public SessionBuilder setTimeToLive(int ttl) {
        mTimeToLive = ttl;
        return this;
    }

    /**
     * Sets the SurfaceView required to preview the video stream.
     **/
    public SessionBuilder setSurfaceView(SurfaceView surfaceView) {
        mSurfaceView = surfaceView;
        return this;
    }

    /**
     * Sets the orientation of the preview.
     * @param orientation The orientation of the preview
     */
    /**public SessionBuilder setPreviewOrientation(int orientation) {
        mOrientation = orientation;
        return this;
    }**/

    public SessionBuilder setCallback(Session.Callback callback) {
        mCallback = callback;
        return this;
    }

    /** Returns the context set with {@link #setContext(Context)}*/
    public Context getContext() {
        return mContext;
    }

    /** Returns the destination ip address set with {@link #setDestination(String)}. */
    public String getDestination() {
        return mDestination;
    }

    /** Returns the origin ip address set with {@link #setOrigin(String)}. */
    public String getOrigin() {
        return mOrigin;
    }

    /** Returns the audio encoder set with {@link #setAudioEncoder(int)}. */
    public int getAudioEncoder() {
        return mAudioEncoder;
    }

    /** Returns the id of the {@link android.hardware.Camera} set with {@link #setCamera(UVCCamera)}. */
    public UVCCamera getCamera() {
        return mCamera;
    }

    /** Returns the video encoder set with {@link #setVideoEncoder(int)}. */
    public int getVideoEncoder() {
        return mVideoEncoder;
    }

    /** Returns the VideoQuality set with {@link #setVideoQuality(VideoQuality)}. */
    public VideoQuality getVideoQuality() {
        return mVideoQuality;
    }

    /** Returns the AudioQuality set with {@link #setAudioQuality(AudioQuality)}. */
    public AudioQuality getAudioQuality() {
        return mAudioQuality;
    }

  /** Returns the flash state set with {@link #setFlashEnabled(boolean)}.
    public boolean getFlashState() {
        return mFlash;
    }
**/
    /** Returns the SurfaceView set with {@link #setSurfaceView(SurfaceView)}. */
    public SurfaceView getSurfaceView() {
        return mSurfaceView;
    }


    /** Returns the time to live set with {@link #setTimeToLive(int)}. */
    public int getTimeToLive() {
        return mTimeToLive;
    }

    /** Returns a new {@link SessionBuilder} with the same configuration. */
    public SessionBuilder clone() {
        return new SessionBuilder()
                .setDestination(mDestination)
                .setOrigin(mOrigin)
                .setSurfaceView(mSurfaceView)
                .setVideoQuality(mVideoQuality)
                .setVideoEncoder(mVideoEncoder)
                .setCamera(mCamera)
                .setTimeToLive(mTimeToLive)
                .setAudioEncoder(mAudioEncoder)
                .setAudioQuality(mAudioQuality)
                .setContext(mContext)
                .setCallback(mCallback);
    }
}
