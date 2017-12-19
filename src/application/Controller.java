package application;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	
	private Mat image;
	
	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	
	// These are added variables
	@FXML
	private Slider slider;
	@FXML
	private Slider volControl;
	@FXML
	private Slider spRate;
	private VideoCapture capture; 
	private ScheduledExecutorService timer;
	private String fileName = "resources/test.mp4";
	
	@FXML
	private Text nowPlaying;
	
	@FXML
	private Button selectFile;
	
	private static int counter = 0; // counter for frames
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		width = 64;
		height = 64;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 500;
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
		volControl.setValue(100);
		spRate.setValue(8000);
		spRate.valueProperty().addListener(new InvalidationListener() {
			
			@Override
			public void invalidated(Observable observable) {
				sampleRate = (int) spRate.getValue();
			}
		});
		volControl.valueProperty().addListener(new InvalidationListener() {
			
			@Override
			public void invalidated(Observable observable) {
				Mixer.Info [] mixers = AudioSystem.getMixerInfo();  
				for (Mixer.Info mixerInfo : mixers){
				    Mixer mixer = AudioSystem.getMixer(mixerInfo);
				    Line.Info [] lineInfos = mixer.getTargetLineInfo();
				    for (Line.Info lineInfo : lineInfos){
				        Line line = null;  
				        boolean opened = true;  
				        try {
				            line = mixer.getLine(lineInfo);  
				            opened = line.isOpen() || line instanceof Clip;
				            if (!opened)    
				                line.open();
				            FloatControl volCtrl = (FloatControl)line.getControl(FloatControl.Type.VOLUME);  
				            volCtrl.setValue((float) volControl.getValue() / 100);
				            //System.out.println(volControl.getValue());  
				        }  
				        catch (LineUnavailableException e) {  
				            e.printStackTrace();  
				        }  
				        catch (IllegalArgumentException iaEx) {  
				        }  
				        finally {  
				            if (line != null && !opened) 
				                line.close();
				        }  
				    }
				}
			}
		});
	}
	
	private String getImageFilename() {
		return fileName;
	}
	
	@FXML
	public void getFileName(ActionEvent event) throws InterruptedException { // when click on select file
		FileChooser fc = new FileChooser();
		File selectedFile = fc.showOpenDialog(null);
		if(selectedFile != null)
			fileName = selectedFile.getAbsolutePath();
		else
			System.out.println("cannot open this file");
	}
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException { // when click on play video
		String[] tokens = getImageFilename().split("\\.(?=[^\\.]+$)");
		String extension = tokens[tokens.length - 1];
//		for(int i = 0; i < tokens.length; i++) {
//			System.out.println(tokens[i]);
//		}
		String[] name = getImageFilename().split(Pattern.quote("\\"));
		nowPlaying.setText("Now Playing:  " + name[name.length - 1]);
		if(extension.equals("mp4") || extension.equals("mov") || extension.equals("avi") || extension.equals("wmv")) {
			capture = new VideoCapture(getImageFilename()); // open video file
			  if (capture.isOpened()) { // open successfully
				  if (capture != null && capture.isOpened()) { // the video must be open
					    double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
					    // create a runnable to fetch new frames periodically
					    Runnable frameGrabber = new Runnable() {
					      @Override
					      public void run() {
//					    	  System.out.println("RUNNING");
					    	  Mat frame = new Mat();
					          if (capture.read(frame)) { // decode successfully
					            Image im = Utilities.mat2Image(frame);
					            Utilities.onFXThread(imageView.imageProperty(), im); 
					            image = frame;
//					            Timer timer = new Timer();
//					            timer.scheduleAtFixedRate(new TimerTask(), 0, 1000);
					            if(counter % 30 == 0) {
						            try {
										playImage();
									} catch (LineUnavailableException e) {
										e.printStackTrace();
									}
					            }
					            counter++;
					            double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
					            double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
					            slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
					            if(currentFrameNumber == totalFrameCount) {
						        	  capture.release();
						        	  slider.setValue(0);
						        }
					          } else { // reach the end of the video
					            capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
					          }
					      }
					    };
							
					    // terminate the timer if it is running 
					    if (timer != null && !timer.isShutdown()) {
					      timer.shutdown();
					      timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
					    }
							
					    // run the frame grabber
					    timer = Executors.newSingleThreadScheduledExecutor();
					    timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
					  }
			  }
		} else {
			if(capture != null) {
				capture.release();
				slider.setValue(0);
			}
			String imageFilename = getImageFilename();
			image = Imgcodecs.imread(imageFilename);
			imageView.setImage(Utilities.mat2Image(image));
			try {
				playImage();
			} catch (LineUnavailableException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void playImage() throws LineUnavailableException{
		if (image != null) {
			// convert the image from RGB to grayscale
			Mat grayImage = new Mat();
			Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
			
			// resize the image
			Mat resizedImage = new Mat();
			Imgproc.resize(grayImage, resizedImage, new Size(width, height));
			
			// quantization
			double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
			for (int row = 0; row < resizedImage.rows(); row++) {
				for (int col = 0; col < resizedImage.cols(); col++) {
					roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
				}
			}
			
			// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
	        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, sampleRate);
            sourceDataLine.start();
            byte[] clickSound = new byte[numberOfSamplesPerColumn];
            for (int col = 0; col < width; col++) {
            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
            	
            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
            		double signal = 0;
            		double signal2 = 0;
                	for (int row = 0; row < height; row++) {
                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
                		int time = t + col * numberOfSamplesPerColumn;
                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
                		double click = Math.sin(2 * Math.PI * 50 * (double)time/sampleRate);
                		signal += roundedImage[row][col] * ss;
                		signal2 += roundedImage[row][col] * click;
                	}
                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
                	double normalizedSignal2 = signal2 / height;
                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
                	clickSound[t-1] = (byte) (normalizedSignal2*0x7F);
            	}
            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
            	//sourceDataLine.write(clickSound, 0, numberOfSamplesPerColumn);
            }
            sourceDataLine.write(clickSound, 0, numberOfSamplesPerColumn);
            sourceDataLine.drain();
            sourceDataLine.close();
		} else {
			System.out.println("no image selected");
		}
	}

}
