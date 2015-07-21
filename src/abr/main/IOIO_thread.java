package abr.main;

import android.util.Log;
import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;

public class IOIO_thread extends BaseIOIOLooper 
{
	private PwmOutput pwm_speed_output, pwm_steering_output, pwm_pan_output, pwm_tilt_output;
	Main_activity the_gui;					// reference to the main activity

	int pwm_pan, pwm_tilt, pwm_speed, pwm_steering;
	private AnalogInput sonar2;
	int sonarPulseCounter;
	private DigitalOutput sonar_pulse;
	int sonar2_reading;
	static final int DEFAULT_PWM = 1500, MAX_PWM = 2000, MIN_PWM = 1000;

	public IOIO_thread(Main_activity gui)
	{
		the_gui = gui;

		sonarPulseCounter = 0;
		sonar2_reading = 1000;
		
		pwm_pan = 1500;
		pwm_tilt= 1500;
		pwm_speed = 1500;
		pwm_steering = 1500;
		
		Log.e("abr controller", "IOIO thread creation");
	}

	@Override
	public void setup() throws ConnectionLostException 
	{
		try 
		{
			pwm_speed_output = ioio_.openPwmOutput(3, 50); //motor channel 4: front left
			pwm_steering_output = ioio_.openPwmOutput(4, 50); //motor channel 3: back left
			pwm_pan_output = ioio_.openPwmOutput(5, 50); //motor channel 1: back right;
			pwm_tilt_output = ioio_.openPwmOutput(6, 50); //motor channel 1: back right;

			pwm_speed_output.setPulseWidth(1500);
			pwm_steering_output.setPulseWidth(1500);
			pwm_pan_output.setPulseWidth(1500);
			pwm_tilt_output.setPulseWidth(1500);

			sonar2 = ioio_.openAnalogInput(43);
			sonar_pulse = ioio_.openDigitalOutput(40,false);
		} 
		catch (ConnectionLostException e){throw e;}
	}

	@Override
	public void loop() throws ConnectionLostException 
	{	
		if (sonarPulseCounter==5){
			sonarPulseCounter = 0;
			sonar_pulse.write(true);
			float reading1 = 0;
			try {
				reading1 = sonar2.getVoltage();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sonar2_reading = (int)(reading1/(float)(3.3/512)); //Only works for sonar with 3.3V input
			sonar_pulse.write(false);
		}	
		else{
			sonarPulseCounter++;
		}		
		if(pwm_speed > MAX_PWM) pwm_speed = MAX_PWM;
    	else if(pwm_speed < MIN_PWM) pwm_speed = MIN_PWM;
    	
    	if(pwm_steering > MAX_PWM) pwm_steering = MAX_PWM;
    	else if(pwm_steering < MIN_PWM) pwm_steering = MIN_PWM;
    	
    	if(pwm_pan > MAX_PWM) pwm_pan = MAX_PWM;
    	else if(pwm_pan < MIN_PWM) pwm_pan = MIN_PWM;
    	
    	if(pwm_tilt > MAX_PWM) pwm_tilt = MAX_PWM;
    	else if(pwm_tilt < MIN_PWM) pwm_tilt = MIN_PWM;
    	
		ioio_.beginBatch();
		try 
		{							
			pwm_speed_output.setPulseWidth(pwm_speed);	
			pwm_steering_output.setPulseWidth(pwm_steering);

			pwm_pan_output.setPulseWidth(pwm_pan);
			pwm_tilt_output.setPulseWidth(pwm_tilt);

			Thread.sleep(10);			
		} 
		catch (InterruptedException e){ ioio_.disconnect();}
		finally{ ioio_.endBatch();}
	}

	public synchronized void set_speed(int speed)
	{
		pwm_speed = speed;
		if(pwm_speed > MAX_PWM) pwm_speed = MAX_PWM;
    	else if(pwm_speed < MIN_PWM) pwm_speed = MIN_PWM;
	}

	public synchronized void set_steering(int steering)
	{
		pwm_steering = steering;
		if(pwm_steering > MAX_PWM) pwm_steering = MAX_PWM;
    	else if(pwm_steering < MIN_PWM) pwm_steering = MIN_PWM;
	}
	
	public synchronized void set_pan(int pan)
	{
		pwm_pan = pan;
		if(pwm_pan > MAX_PWM) pwm_pan = MAX_PWM;
    	else if(pwm_pan < MIN_PWM) pwm_pan = MIN_PWM;
	}
	
	public synchronized void set_tilt(int tilt)
	{
		pwm_tilt = tilt;
		if(pwm_tilt > MAX_PWM) pwm_tilt = MAX_PWM;
    	else if(pwm_tilt < MIN_PWM) pwm_tilt = MIN_PWM;
	}
	
	public synchronized int get_speed()
	{
		return pwm_speed;
	}

	public synchronized int get_steering()
	{
		return pwm_steering;
	}
	
	public synchronized int get_pan()
	{
		return pwm_pan;
	}
	
	public synchronized int get_tilt()
	{
		return pwm_tilt;
	}
	
	public synchronized int get_sonar2_reading(){
		return sonar2_reading;
	}
	
	@Override
	public void disconnected() {
		Log.i("blar","IOIO_thread disconnected");
	}
}
