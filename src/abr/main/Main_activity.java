package abr.main;

/**
 * Rescue Robotics 2015 App
 * Controls wheeled robot through IOIO
 * Parts of code adapted from OpenCV blob follow
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import abr.rescuerobotics.R;

public class Main_activity extends Activity implements IOIOLooperProvider,
		CvCameraViewListener2 // implements IOIOLooperProvider: from IOIOActivity
{
	private final IOIOAndroidApplicationHelper helper_ = new IOIOAndroidApplicationHelper(this, this); // from IOIOActivity
	
	// ioio variables
	IOIO_thread m_ioio_thread;
	
	// blob detection variables
	private CameraBridgeViewBase mOpenCvCameraView;
	private boolean mIsColorSelected = false;
	private Mat mRgba;
	private Scalar mBlobColorRgba;
	private ColorBlobDetector mDetector;
	private Mat mSpectrum;
	private Scalar CONTOUR_COLOR;
	
	// app state variables
	private boolean autoMode;
	private boolean scanned;
	public int randDir = 0;
	public int backUpCounter = 0;
	public int turnCounter = 0;
	public int pauseCounter = 0;
	
	// location variables
	double curr_lat = 0;
	double curr_lon = 0;
	double dest_lat = 0;
	double dest_lon = 0;
	
	// ui variables
	TextView sonar2;
	
	// called to use OpenCV libraries contained within the app as opposed to a separate download
	static {
		if (!OpenCVLoader.initDebug()) {
			// Handle initialization error
		}
	}
	
	// called whenever the activity is created
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i("activity cycle","main activity being created");
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.main);
		
		helper_.create(); // from IOIOActivity

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
		mOpenCvCameraView.enableView();
		
		Intent myIntent = getIntent();
		if(myIntent.hasExtra("autoMode")){
			autoMode = myIntent.getBooleanExtra("autoMode", false);
		}
		else{
			autoMode = false;
		}
		backUpCounter = myIntent.getIntExtra("backUpCounter", 0);
		turnCounter = myIntent.getIntExtra("turnCounter", 0);
		randDir = (Math.random()<0.5)?0:1;
		//if(myIntent.hasExtra("pauseCounter"))
		//	pauseCounter = myIntent.getIntExtra("pauseCounter",100);
		//else{
		//	pauseCounter = 100;
		//}
		scanned = true;

		sonar2 = (TextView) findViewById(R.id.sonar2);
		
		//add functionality to autoMode button
		Button buttonAuto = (Button) findViewById(R.id.btnAuto);
		buttonAuto.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (!autoMode) {
					v.setBackgroundResource(R.drawable.button_auto_on);
					autoMode = true;
				} else {
					v.setBackgroundResource(R.drawable.button_auto_off);
					autoMode = false;
				}
			}
		});
		
		//Set starting autoMode button color
		if (autoMode) {
			buttonAuto.setBackgroundResource(R.drawable.button_auto_on);
		} else {
			buttonAuto.setBackgroundResource(R.drawable.button_auto_off);
		}

		// Set up location system
		// Acquire a reference to the system Location Manager
		LocationManager locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);
		// Define a listener that responds to location updates
		LocationListener locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				curr_lat = location.getLatitude();
				curr_lon = location.getLongitude();
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};

		// Register the listener with the Location Manager to receive location
		// updates
		locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
	}

	// Scan for QR code
	public void scan() {
		Log.i("activity cycle","scan() called");
		scanned = false;
		
		//Creates a new activity for QR code reader and starts it
		Intent intent = new Intent("com.google.zxing.client.android.SCAN");
		intent.putExtra("com.google.zxing.client.android.SCAN.SCAN_MODE", "QR_CODE_MODE");
		startActivityForResult(intent, 0);
		
		//Sets turn counter
		turnCounter = 10;
	}
	//Called whenever activity resumes from pause
	@Override
	public void onResume() {
	    super.onResume();  // Always call the superclass method first
	    Log.i("activity cycle","main activity resuming");
	}
	//Called when activity pauses
	@Override
	public void onPause() {
		super.onPause();
		Log.i("activity cycle","main activity pausing");
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}
	//Called when activity restarts. onCreate() will then be called
	@Override
	public void onRestart() {
		super.onRestart();
		Log.i("activity cycle","main activity restarting");
	}
	//Called when camera view starts. change bucket color here
	public void onCameraViewStarted(int width, int height) {
		mRgba = new Mat(height, width, CvType.CV_8UC4);
		mDetector = new ColorBlobDetector();
		mSpectrum = new Mat();
		mBlobColorRgba = new Scalar(255);
		CONTOUR_COLOR = new Scalar(255, 0, 0, 255);

		//To set color, find HSV values of desired color and convert each value to 1-255 scale
		mDetector.setHsvColor(new Scalar(7, 196, 144)); // red
		mDetector.setHsvColor(new Scalar(7.015625,255.0,239.3125));

	}
	//Called when camera view stops
	public void onCameraViewStopped() {
		mRgba.release();
	}
	//Called at every camera frame. Main controls of the robot movements are in this function
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgba = inputFrame.rgba();
		mDetector.process(mRgba);
		
		List<MatOfPoint> contours = mDetector.getContours();
		// Log.e(TAG, "Contours count: " + contours.size());
		Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);

		Mat colorLabel = mRgba.submat(4, 68, 4, 68);
		colorLabel.setTo(mBlobColorRgba);

		Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70,
				70 + mSpectrum.cols());
		mSpectrum.copyTo(spectrumLabel);
		
		//Set text on phone screen to sonar reading
		setSonar2Text("Sonar2: "+m_ioio_thread.get_sonar2_reading());

		//Tells robot to go left, right, or straight, depending on condition
		if (autoMode) { // only move if autoMode is on
			//Pauses the robot to avoid sudden jolts to pan/tilt unit during scanning
			if(pauseCounter > 0){
				m_ioio_thread.set_speed(1500); //stop
				m_ioio_thread.set_steering(1500); //stop
				pauseCounter--;
				if(pauseCounter == 0 && scanned == false){
					scanned = true;
					scan();
				}
			}
			//Backs up robot, and triggers pauseCounter when backUpCounter reaches 0
			else if(backUpCounter > 0){
				m_ioio_thread.set_speed(1500 - 75);//75
				m_ioio_thread.set_steering(1500);
				backUpCounter--;
				if(backUpCounter == 0){
					pauseCounter = 10;
					scanned = false;
				}
			}
			//TurnCounter will be >0 when main activity is restarted after a scan()
			//Currently causes robot to turn backwards gradually, not in place
			else if(turnCounter > 0){
				m_ioio_thread.set_speed(1500-200);
				if(randDir == 0) //will go backwards left or right depending on the random direction selected at the beginning of the activity
					m_ioio_thread.set_steering(1500+150);//100
				else
					m_ioio_thread.set_steering(1500-150);
				turnCounter--;
			}
			//If sonar detects obstacle, backUpCounter will get set, causing robot to back up
			else if (m_ioio_thread.get_sonar2_reading() < 20){ //change back to 14, 6 for indoor tests
				m_ioio_thread.set_speed(1500);
				m_ioio_thread.set_steering(1500);
				backUpCounter = 15;
			}
			else{ //if no counters are set and no obstacles detected, default to tracking orange objects
				//find the center of mass of the largest orange object
				double momentX = mDetector.getMomentX();
				// double momentY = mDetector.getMomentY(); //currently not used, but could possibly be used for distance
				double centerX = mDetector.getCenterX();
				double centerY = mDetector.getCenterY();
				
				//divide the screen into sides and middle using threshold lines
				int centerThreshold = (int) (.333 * centerX);

				// reasons for not moving: blob too big and therefore very close, blob too small, or no blob seen
				if (mDetector.getMaxArea() > (int) (.333 * 4 * centerX * centerY) //area of biggest orange blob should be larger than 1/3 of screen
						//|| mDetector.getMaxArea() < (int) (.001 * 4 * centerX * centerY)
						|| mDetector.getMaxArea() == 0) {
					m_ioio_thread.set_speed(1500); //stop
					m_ioio_thread.set_steering(1500); //stop
				} else if (momentX > centerThreshold) { //blob is to the right of screen
					m_ioio_thread.set_speed(1500 + 200);//75 for pavement, move forward
					if (m_ioio_thread.get_speed() > 1500)
						m_ioio_thread.set_steering(1500 + 150);//100 for pavement, turn right while moving
					else
						m_ioio_thread.set_steering(1500 - 150);//100 for pavement, turn left while moving because robot is going backwards. this is never used
				} else if (momentX < -centerThreshold) { //blob is to the left of screen
					m_ioio_thread.set_speed(1500 + 200);//75
					if (m_ioio_thread.get_speed() > 1500)
						m_ioio_thread.set_steering(1500 - 150);//100
					else
						m_ioio_thread.set_steering(1500 + 150);//100 for pavement, turn left while moving because robot is going backwards. this is never used
				} else { //in any other case, just stop
					m_ioio_thread.set_speed(1500 + 100);//75
					m_ioio_thread.set_steering(1500);
				}
			}
		} else {
			m_ioio_thread.set_speed(1500);
			m_ioio_thread.set_steering(1500);
		}

		return mRgba;
	}

	//Called when scan activity is finished
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if(requestCode == 0){
			autoMode = true;
			Button buttonAuto = (Button) findViewById(R.id.btnAuto);
			buttonAuto.setBackgroundResource(R.drawable.button_auto_on);
			if (resultCode == RESULT_OK) { //QR code was found, save GPS coords and QR info to data
				String scanContent = intent.getStringExtra("SCAN_RESULT"); 
				Calendar calendar = Calendar.getInstance();
				java.util.Date now = calendar.getTime();
				java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(now.getTime());
				String time = currentTimestamp.toString();
				String info = scanContent+" ,Lat:"+curr_lat+" ,Lon:"+curr_lon+" ,Time:"+time;
				Toast toast = Toast.makeText(getApplicationContext(), info,
						Toast.LENGTH_SHORT);
				Log.i("app.main",info);
				toast.show();
				try {
				    File newFolder = new File(Environment.getExternalStorageDirectory(), "RescueRobotics");
				    if (!newFolder.exists()) {
				        newFolder.mkdir();
				    }
				    try {
				        File file = new File(newFolder, time + ".txt");
				        file.createNewFile();
				        FileOutputStream fos=new FileOutputStream(file);
		                try {
		                	byte[] b = info.getBytes();
		                    fos.write(b);
		                    fos.close();
		                } catch (IOException e) {
		                	Log.e("app.main","Couldn't write to SD");
		                }
				    } catch (Exception ex) {
				    	Log.e("app.main","Couldn't write to SD");
				    }
				} catch (Exception e) {
				    Log.e("app.main","Couldn't write to SD");
				}
				
			} else { //QR Code not found
				Toast toast = Toast.makeText(getApplicationContext(),
						"No scan data received!", Toast.LENGTH_SHORT);
				toast.show();
			}
		}
		
		//Restart the main app due to problems with resuming after scan activity finishes
		Intent mStartActivity = new Intent(this, Main_activity.class);
		mStartActivity.putExtra("autoMode", true);
		int randomNum = 10 + (int)(Math.random()*5); 
		//Pass info to the restarted activity such that the robot turns for a random amount of time
		mStartActivity.putExtra("turnCounter",randomNum);
		mStartActivity.putExtra("pauseCounter",0);
		int mPendingIntentId = 123456;
		PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
		//Alarm manager will close out the app and wake up in 100 ms to restart
		AlarmManager mgr = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
		mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
		System.exit(0);
	}

	/****************************************************** functions from IOIOActivity *********************************************************************************/

	/**
	 * Create the {@link IOIO_thread}. Called by the
	 * {@link IOIOAndroidApplicationHelper}. <br>
	 * Function copied from original IOIOActivity.
	 * 
	 * @see {@link #get_ioio_data()} {@link #start_IOIO()}
	 * */
	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
		if (m_ioio_thread == null
				&& connectionType
						.matches("ioio.lib.android.bluetooth.BluetoothIOIOConnection")) {
			m_ioio_thread = new IOIO_thread(this);
			return m_ioio_thread;
		} else
			return null;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i("activity cycle","main activity being destroyed");
		helper_.destroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.i("activity cycle","main activity starting");
		helper_.start();
	}

	@Override
	protected void onStop() {
		Log.i("activity cycle","main activity stopping");
		super.onStop();
		helper_.stop();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
			if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
			helper_.restart();
		}
	}
	
	public void setSonar2Text(final String str) 
	{
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				sonar2.setText(str);
			}
		});
	}
}
