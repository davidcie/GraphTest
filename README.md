Hardware Accelerated Canvas Drawing
----------
This test app was built to demonstrate and help debug slow line rendering in Android 5.0+. Three circular buffers of 50 values get a new value every 100ms, fed by a background thread. Two graphs animate the arrival of new values by progressively shifting the entire graph to the left over the 100ms they have before a new value arrives. Re-drawing happens every 16ms for a ~60FPS view.

In total each graph draws 150 lines using hardware-accelerated `canvas.drawLine` in a custom view's `onDraw`. A separate background thread updates the list of line coordinates to draw so that `drawLine` has no extra work to do.

While this test case app performs acceptably well on a Snapdragon 805, as soon as more complex layouts are involved and graphs are placed eg. in a `CardView`, it falls on its face. Granted, there are more efficient ways to draw (eg. use a bitmap, scroll it across the canvas and only add the one new line at the end) but 300 straight lines should not bring a modern GPU to its knees.

Discussion
----------
Reasons for the apparent inefficiency and slow drawing performance are discussed [in this StackOverflow question]().

Pre-requisites
----------
- Android SDK 27
- Android Build Tools v27.0.3

Building
----------
This app uses the Gradle build system. To build, issue `gradlew build` or open this project in Android Studio.