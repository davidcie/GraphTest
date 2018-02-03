package net.davidcie.graphtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.os.TraceCompat;

public class MainActivity extends Activity {

    // Settings
    private final static int VALUE_EVERY_MS = 100;
    private final static int NUMBER_OF_POINTS = 50;

    // Collections
    public final Object mPointsLock = new Object();
    private Float[] mPoints1 = new Float[NUMBER_OF_POINTS];
    private Float[] mPoints2 = new Float[NUMBER_OF_POINTS];
    private Float[] mPoints3 = new Float[NUMBER_OF_POINTS];

    // Value generation
    private Handler mValueUpdateHandler = new Handler();
    private HandlerThread mValueUpdateThread = new HandlerThread("ValueUpdater", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Runnable mValueUpdateTask = new Runnable() {
        @Override
        public void run() {
            TraceCompat.beginSection("Value Generation");
            mValueUpdateHandler.postDelayed(this, VALUE_EVERY_MS);
            // Generate new values
            synchronized (mPointsLock) {
                addValue(mPoints1, (float) Math.random());
                addValue(mPoints2, (float) Math.random());
                addValue(mPoints3, (float) Math.random());
            }
            TraceCompat.endSection();
        }
    };

    private void addValue(Float[] array, float newValue) {
        System.arraycopy(array, 1, array, 0, NUMBER_OF_POINTS - 1);
        array[NUMBER_OF_POINTS - 1] = newValue;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int i = 0; i < NUMBER_OF_POINTS; i++) {
            addValue(mPoints1, 0f);
            addValue(mPoints2, 0f);
            addValue(mPoints3, 0f);
        }

        GraphView graph1 = findViewById(R.id.graph1);
        graph1.initialize(mPoints1, mPoints2, mPoints3, NUMBER_OF_POINTS, VALUE_EVERY_MS);

        GraphView graph2 = findViewById(R.id.graph2);
        graph2.initialize(mPoints1, mPoints2, mPoints3, NUMBER_OF_POINTS, VALUE_EVERY_MS);

        mValueUpdateThread.start();
        mValueUpdateHandler = new Handler(mValueUpdateThread.getLooper());
    }

    @Override
    public void onResume() {
        super.onResume();
        mValueUpdateHandler.post(mValueUpdateTask);
    }
    @Override
    public void onPause() {
        mValueUpdateHandler.removeCallbacks(mValueUpdateTask);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mValueUpdateThread != null) mValueUpdateThread.quit();
        super.onDestroy();
    }
}
