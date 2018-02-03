package net.davidcie.graphtest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.os.TraceCompat;
import android.util.AttributeSet;
import android.view.View;

public class GraphView extends View {

    // Path generation
    private volatile Handler mPathUpdateHandler;
    private HandlerThread mPathUpdateThread = new HandlerThread("PathUpdater", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Runnable mPathUpdateTask = new PathUpdateTask();

    // View properties
    private volatile int mWidth = 0;
    private volatile int mHeight = 0;
    private volatile Rect mScreenRect;

    // Collections
    private volatile Float[] mPoints1;
    private volatile Float[] mPoints2;
    private volatile Float[] mPoints3;
    private volatile Float[] mPoints1Local;
    private volatile Float[] mPoints2Local;
    private volatile Float[] mPoints3Local;

    // Collection properties
    private volatile int mNumberOfPoints;
    private volatile float mScaleX;
    private volatile float mScaleY;
    private volatile float mFrameDeltaX;

    // Pre-computed lines for Canvas.drawLines
    private final Object mCanvasLinesLock = new Object();
    private volatile float[] mCanvasLines1;
    private volatile float[] mCanvasLines2;
    private volatile float[] mCanvasLines3;

    // Paints
    private final Paint mPaintRed = getPaint(Color.argb(128, 255, 0, 0));
    private final Paint mPaintGreen = getPaint(Color.argb(128, 0, 255, 0));
    private final Paint mPaintBlue = getPaint(Color.argb(128, 0, 0, 255));
    private Paint mBackground;

    // State variables
    private volatile int mFramesPerValue;
    private volatile int mCurrentAnimationFrame = 0;
    private volatile boolean mUpdateView = false;
    private volatile boolean mInitialized = false;

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(
            Float[] points1,
            Float[] points2,
            Float[] points3,
            int numberOfPoints,
            int valueEveryMs) {
        mNumberOfPoints = numberOfPoints;
        // Calculate how many animation frames we can squeeze between a new
        // value arrives in the collection being graphed; UI runs at 60FPS ~ 16ms per frame
        mFramesPerValue = (int) ((double) valueEveryMs / 16);

        // Store pointers to collections being graphed
        mPoints1 = points1;
        mPoints2 = points2;
        mPoints3 = points3;
        // Initialize local copies of collections being graphed; this is just
        // in case they are changed while they are being drawn
        mPoints1Local = new Float[mNumberOfPoints];
        mPoints2Local = new Float[mNumberOfPoints];
        mPoints3Local = new Float[mNumberOfPoints];
        // Every line fed into Canvas.drawLines is defined by four coordinates
        mCanvasLines1 = new float[mNumberOfPoints * 4];
        mCanvasLines2 = new float[mNumberOfPoints * 4];
        mCanvasLines3 = new float[mNumberOfPoints * 4];

        mBackground = new Paint();
        mBackground.setStyle(Paint.Style.FILL);
        mBackground.setColor(Color.WHITE);

        mInitialized = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        mScaleX = (float)mWidth / (mNumberOfPoints - 1);
        mScaleY = (float)mHeight / 1; // Math.random generates values 0.0..1.0
        mFrameDeltaX = mScaleX / mFramesPerValue;
        mScreenRect = new Rect(0, 0, mWidth, mHeight);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mUpdateView = true;
        mPathUpdateThread.start();
        mPathUpdateHandler = new Handler(mPathUpdateThread.getLooper());
        mPathUpdateHandler.post(mPathUpdateTask);
    }

    @Override
    protected void onDetachedFromWindow() {
        mUpdateView = false;
        if (mPathUpdateThread != null) mPathUpdateThread.quit();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        TraceCompat.beginSection("GraphView.onDraw");
        // Clear canvas
        canvas.drawRect(mScreenRect, mBackground);
        // Draw all three graphs
        synchronized (mCanvasLinesLock) {
            canvas.drawLines(mCanvasLines1, mPaintRed);
            canvas.drawLines(mCanvasLines2, mPaintGreen);
            canvas.drawLines(mCanvasLines3, mPaintBlue);
        }
        TraceCompat.endSection();
    }

    private Paint getPaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(6);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        return paint;
    }

    /**
     * Generates a flat array of line coordinates for use in Canvas.drawLines.
     */
    private void generateLines(Float[] collection, float[] linesArray, float xOffset) {
        int basePtr;
        for (int v = 0; v < mNumberOfPoints - 1; v++) {
            basePtr = v * 4;
            linesArray[basePtr] = translateX(v) - xOffset;        //x0
            linesArray[basePtr + 1] = translateY(collection[v]);  //y0
            linesArray[basePtr + 2] = translateX(v+1) - xOffset;  //x1
            linesArray[basePtr + 3] = translateY(collection[v+1]);//y1
        }
    }

    /**
     * Translate from 0.0..1.0 range to screen coordinates.
     */
    private float translateY(float from) {
        return from * mScaleY;
    }

    /**
     * Translate from 0..mNumberOfPoints range to screen coordinates.
     */
    private float translateX(float from) {
        return from * mScaleX;
    }

    private class PathUpdateTask implements Runnable {
        public void run() {

            if (mUpdateView) mPathUpdateHandler.postDelayed(this, 16);
            if (!mInitialized || mScreenRect == null) return;

            TraceCompat.beginSection("Path Generation");
            boolean repaint = false;

            // Check if one of the collections being graphed has a new value; if so we need
            // to switch to that immediately and reset animation, even if it did not finish.
            // This comparison intentionally compares pointers to Float rather than numerical values.
            //noinspection NumberEquality
            if (mPoints1Local[0] != mPoints1[0]) {
                // mPoints{1,2,3} should but is intentionally not locked to give
                // a clearer picture of what is causing slow rendering
                System.arraycopy(mPoints1, 0, mPoints1Local, 0, mNumberOfPoints);
                System.arraycopy(mPoints2, 0, mPoints2Local, 0, mNumberOfPoints);
                System.arraycopy(mPoints3, 0, mPoints3Local, 0, mNumberOfPoints);
                mCurrentAnimationFrame = 0;
                repaint = true;
            } else if (mCurrentAnimationFrame < mFramesPerValue) {
                // No new values but we still have part of the animation to run
                mCurrentAnimationFrame++;
                repaint = true;
            } else {
                // Animation finished but a new value is yet to arrive - do nothing!
            }

            if (repaint) {
                float frameDeltaX = mCurrentAnimationFrame * mFrameDeltaX;
                synchronized (mCanvasLinesLock) {
                    generateLines(mPoints1Local, mCanvasLines1, frameDeltaX);
                    generateLines(mPoints2Local, mCanvasLines2, frameDeltaX);
                    generateLines(mPoints3Local, mCanvasLines3, frameDeltaX);
                }
                postInvalidate(0, 0, mWidth, mHeight);
            }

            TraceCompat.endSection();
        }
    }
}
