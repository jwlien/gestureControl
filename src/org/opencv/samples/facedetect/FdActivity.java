package org.opencv.samples.facedetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

public class FdActivity extends Activity implements CvCameraViewListener2 {

    private static final String    TAG                 = "OCVSample::Activity";
    private static final Scalar    HAND_RECT_COLOR     = new Scalar(255, 0, 0);
    private static final Scalar	   HAND_CONTOUR_COLOR  = new Scalar(0, 255, 0);
    private static final Scalar	   WHITE			   = new Scalar(255,255,255);
    
    private static int			   COMMAND_TIMEOUT_TIME = 1;
    
    
    private static enum			   COMMAND_TYPE {
    	UP,
    	DOWN,
    	LEFT,
    	RIGHT
    }
    private COMMAND_TYPE		   mCommand;
    private boolean				   mCommandValid=false;
    
    private int					   mUndersamplingFactor = 4;
    private int					   mErosionKernelSize = 44;
    private float				   mMinBBArea = 0.01f;


    private Mat                    mRgba;
    private Mat					   mRgbaFull;
    private Mat					   mRGB;
    private Mat					   mRgbaPrev;
    private Mat                    mGray;
    private Mat					   mGrayFull;
    
    private Mat					   mOutputImage;
    
    private Mat					   mRedPrev;
    private Mat					   mGreenPrev;
    private Mat					   mBluePrev;
    private Mat					   mRedDiff;
    private Mat					   mGreenDiff;
    private Mat					   mBlueDiff;
    
    private Mat					   mGrayDiff;
    private Mat					   mGrayPrev;
    
    private Mat					   mGrayMask;
    
    private Mat					   mGrayStatic;

    private Mat					   mGrayDiffMasked;

    private Mat					   mGrayInverseMask;

    private Mat					   mFreezeFrame;
    
    private Mat					   mMhi;
    private Mat					   mMmask;
    private Mat					   mMorientation;
    private Mat					   mSmask;
    private MatOfRect			   mSBoundingBox;
    
    
    private File                   mCascadeFile;
    private CascadeClassifier      mJavaDetector;
    
    private BackgroundSubtractorMOG2 mBGsub;
    private Mat						mFGmask;
    

    private int					   mFrameNum = 1;
    private int					   mLastFrame = 1;
    
    private int					   mDefectNum = 0;
    

    private float                  mRelativeFaceSizeMin   = 0.05f;
    private float                  mRelativeFaceSizeMax   = 0.5f;
    
    private int                    mAbsoluteFaceSizeMin   = 0;
    private int					   mAbsoluteFaceSizeMax	  = 0;

    private CameraBridgeViewBase   mOpenCvCameraView;
    

    
    private MediaPlayer mediaPlayer;
    private TextView songName;
    private float mVolume = 6, maxVolume = 1.0f, minVolume = 0.2f;
    private double startTime = 0;
    private double endTime = 0;
    
    
    

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());


                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public FdActivity() {

    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCameraIndex(1);
        mOpenCvCameraView.setCvCameraViewListener(this);
        
        mediaPlayer = MediaPlayer.create(this, R.raw.levels);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mGrayFull = new Mat();
        mRgba = new Mat();
        mRgbaFull = new Mat();
        mRGB = new Mat();
        mRedPrev = new Mat();
        mGreenPrev = new Mat();
        mBluePrev = new Mat();
        mRedDiff = new Mat();
        mGreenDiff = new Mat();
        mBlueDiff = new Mat();
        mGrayPrev = new Mat();
        mGrayDiff = new Mat();
        mGrayDiff = new Mat();
        mGrayDiffMasked = new Mat();
        mGrayStatic = new Mat();
        mGrayMask = new Mat();
        mGrayInverseMask = new Mat();
        mMhi = new Mat();
        mMmask = new Mat();
        mMorientation = new Mat();
        mSmask = new Mat();
        mOutputImage = new Mat();
        mFreezeFrame = new Mat();
        
        mBGsub = new BackgroundSubtractorMOG2();
        mFGmask = new Mat();
        
        mSBoundingBox = new MatOfRect();
       
        
    }

    public void onCameraViewStopped() {
        mGray.release();
        mGrayFull.release();
        mRgba.release();
        mRgbaFull.release();
        mRGB.release();
        mRedPrev.release();
        mGreenPrev.release();
        mBluePrev.release();
        mRedDiff.release();
        mGreenDiff.release();
        mBlueDiff.release();
        mGrayPrev.release();
        mGrayDiff.release();
        mGrayDiff.release();

        mGrayDiffMasked.release();
        mGrayMask.release();
        mGrayInverseMask.release();
        mGrayStatic.release();
        mMhi.release();
        mMmask.release();
        mMorientation.release();
        mSmask.release();
        mSBoundingBox.release();
        mOutputImage.release();
        mFreezeFrame.release();
        mFGmask.release();
        
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgbaFull = inputFrame.rgba();
        mGrayFull = inputFrame.gray();
        
        
        Size origSize = mRgbaFull.size();
        
        double shrinkFactor = 1/(double)mUndersamplingFactor;
        //Log.d(TAG,"shrinkFactor = "+shrinkFactor);
        
        Imgproc.resize(mRgbaFull, mRgba, new Size(), shrinkFactor, shrinkFactor, Imgproc.INTER_LINEAR);
        Imgproc.resize(mGrayFull, mGray, new Size(), shrinkFactor, shrinkFactor, Imgproc.INTER_LINEAR);
        

        //mBGsub.apply(mRgbaFull, mFGmask, 0.01);
        
        int M,N;
        
        M = mGray.rows();
        N = mGray.cols();
        
        Mat mRed = new Mat();
        Mat mGreen = new Mat();
        Mat mBlue = new Mat();

        Core.extractChannel(mRgba, mRed, 0);
        Core.extractChannel(mRgba, mGreen, 1);
        Core.extractChannel(mRgba, mBlue, 2);
        
             
        
        if (mFrameNum == 1) {
        	mRgbaPrev = inputFrame.rgba();
        	
        	mRedPrev = mRed.clone();
        	mGreenPrev = mGreen.clone();
        	mBluePrev = mBlue.clone();
        }
        
        Core.absdiff(mRed, mRedPrev, mRedDiff);
        Core.absdiff(mGreen, mGreenPrev, mGreenDiff);
        Core.absdiff(mBlue, mBluePrev, mBlueDiff);
        
        mRedPrev.release();
        mGreenPrev.release();
        mBluePrev.release();
        
        mRedPrev = mRed.clone();
        mGreenPrev = mGreen.clone();
        mBluePrev = mBlue.clone();
        

        
        Imgproc.threshold(mRedDiff, mRedDiff, 15, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(mGreenDiff, mGreenDiff, 15, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(mBlueDiff, mBlueDiff, 15, 255, Imgproc.THRESH_BINARY);
        
        List<Mat> mRgbaSplit = new ArrayList<Mat>(3);
        
        
        mRgbaSplit.add(0, mRedDiff);
        mRgbaSplit.add(1, mGreenDiff);
        mRgbaSplit.add(2, mBlueDiff);
        
        
        
        Core.merge(mRgbaSplit, mRedDiff);
        Imgproc.cvtColor(mRedDiff, mGrayDiff, Imgproc.COLOR_RGB2GRAY);
        

        

        
        if (mFrameNum==1) {
        	Log.d(TAG,"mFrameNum=" + mFrameNum);
        	mMhi = Mat.zeros(mGrayDiff.size(), CvType.CV_32F);
        	mFreezeFrame = mGrayDiff.clone();
        }
        
        int kernelsize = Math.round(mErosionKernelSize/mUndersamplingFactor);
        //Log.d(TAG,"kernelsize = "+kernelsize);
        
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(kernelsize,kernelsize));
        
        Imgproc.erode(mGrayDiff, mGrayMask, kernel);
        
        for (int i = 1; i<=3; i++) {
        	Imgproc.dilate(mGrayMask, mGrayMask, kernel);
        }
        
        Imgproc.threshold(mGrayMask, mGrayMask, 25, 255, Imgproc.THRESH_BINARY);
        
        
//        mGrayDiffMasked = Mat.zeros(mGrayDiff.size(), mGrayDiff.type());
//        mGrayDiff.copyTo(mGrayDiffMasked, mGrayMask);
        
//        Mat tmp = new Mat();
//
//        Core.scaleAdd(Mat.ones(mGrayDiff.size(), mGrayDiff.type()), 255, Mat.zeros(mGrayDiff.size(), mGrayDiff.type()), tmp);
//        Core.subtract(tmp, mGrayDiff, mGrayInverseMask);
//        
//
//
//        mGray.copyTo(mGrayStatic,mGrayInverseMask);
        
        
        //Log.d(TAG,"size of mGrayMask="+mGrayMask.size()+" size of mMhi="+mMhi.size());
		Video.updateMotionHistory(mGrayMask, mMhi, mFrameNum, 1);
		
		
		Video.calcMotionGradient(mMhi, mMmask, mMorientation, 1, 1, 3);
			
		Video.segmentMotion(mMhi, mSmask, mSBoundingBox, mFrameNum, 1);

//		int height = mGray.rows();
//        if (mAbsoluteFaceSizeMin == 0) {  
//            if (Math.round(height * mRelativeFaceSizeMin) > 0) {
//                mAbsoluteFaceSizeMin = Math.round(height * mRelativeFaceSizeMin);
//            }
//        }
//        
//        if (mAbsoluteFaceSizeMax == 0) {
//            if (Math.round(height * mRelativeFaceSizeMax) > 0) {
//                mAbsoluteFaceSizeMax = Math.round(height * mRelativeFaceSizeMax);
//            }
//        }
//
//        MatOfRect faces = new MatOfRect();
//        
//        if (mJavaDetector != null)
//        	mJavaDetector.detectMultiScale(mGrayStatic, faces, 1.1, 2, 2,
//        			new Size(mAbsoluteFaceSizeMin, mAbsoluteFaceSizeMin), new Size(mAbsoluteFaceSizeMax, mAbsoluteFaceSizeMax));
//     
//        
//        Rect[] facesArray = faces.toArray();
//        
//        for (int i = 0; i< facesArray.length; i++) {
//        	Core.rectangle(mGray, facesArray[i].tl(), facesArray[i].br(), HAND_RECT_COLOR, 3);
//        	Log.d(TAG, "Face Detected.");
//        }

        
        Rect[] motionArray = mSBoundingBox.toArray();
        double orientationAngle, orientationDegrees, orientationRadians;
        double amplitudeInOrientation;
        double whratio;

        
        Mat localOrientation, localMmask, localMhi;
        
        boolean commandTimeOut;
    	
    	if (mFrameNum > mLastFrame + COMMAND_TIMEOUT_TIME)
    		commandTimeOut = false;
    	else
    		commandTimeOut = true;
        

        
        double minArea = mMinBBArea * mGrayDiff.rows()*mGrayDiff.cols();
        
//        mFreezeFrame.release();
//        mFreezeFrame = mGray.clone();
//        Imgproc.cvtColor(mFreezeFrame, mFreezeFrame, Imgproc.COLOR_GRAY2RGB);
        
        double bestGestureAIO = 0;


        for (int i = 0; i < motionArray.length; i++) {
        	
        
        	
        	localOrientation = mMorientation.submat(motionArray[i]);
        	localMmask = mMmask.submat(motionArray[i]);
        	localMhi = mMhi.submat(motionArray[i]);
        			
        	orientationAngle = Video.calcGlobalOrientation(localOrientation, localMmask, localMhi, mFrameNum, 1);
        	orientationDegrees = orientationAngle;
        	
        	if (orientationAngle>=270)
        		orientationAngle = 360 - orientationAngle;
        	else if (orientationAngle>=180)
        		orientationAngle = orientationAngle - 180;
        	else if (orientationAngle>90) {
        		orientationAngle = 180 - orientationAngle;
        	}
        	
        	orientationRadians = orientationAngle * 2 * (Math.PI) / 360;
        	
        	if (orientationRadians < Math.atan2(motionArray[i].height,motionArray[i].width)) {
        		amplitudeInOrientation = (motionArray[i].width/Math.cos(orientationRadians));
        	}
        	else {
        		amplitudeInOrientation = (motionArray[i].height/Math.sin(orientationRadians));
        	}
        	
        	if (motionArray[i].width > motionArray[i].height)
        		whratio = motionArray[i].width/motionArray[i].height;
        	else
        		whratio = motionArray[i].height/motionArray[i].width;

        	double aionormallized = 1000*amplitudeInOrientation/(Math.sqrt(motionArray[i].area()));
        	
        	if (aionormallized>1000 && motionArray[i].area()>minArea && whratio>1.2) {
        		
//        		mFreezeFrame.release();
//                mFreezeFrame = mGrayDiff.clone();
//                Imgproc.cvtColor(mFreezeFrame, mFreezeFrame, Imgproc.COLOR_GRAY2RGB);
        		
        		mLastFrame = mFrameNum;
        		
        		Log.d(TAG, "orientationAngle=" + Math.round(orientationDegrees) + " width=" + motionArray[i].width + " height=" + motionArray[i].height + " aO=" + aionormallized);
        		
        		int x,y,w,h;
        		
        		x = motionArray[i].x;
        		y = motionArray[i].y;
        		w = motionArray[i].width;
        		h = motionArray[i].height;
        		
        		Rect handBBRect,origHandBBRect;
        		double handBBRectMargin = 1;
        		
        		COMMAND_TYPE thisCommand;
        		
        		
        		if (orientationDegrees>270+45) {
        			handBBRect = new Rect(Math.round(x+w-((0.9f)*h)),y,h,h);
        			thisCommand = COMMAND_TYPE.RIGHT;
        		} else if (orientationDegrees>180+45) {
        			handBBRect = new Rect(x,Math.round(y-((0.1f)*w)),w,w);
        			thisCommand = COMMAND_TYPE.DOWN;
        		} else if (orientationDegrees>90+45) {
        			handBBRect = new Rect(Math.round(x-((0.1f)*h)),y,h,h);
        			thisCommand = COMMAND_TYPE.LEFT;
        		} else if (orientationDegrees>0+45) {
        			handBBRect = new Rect(x,Math.round(y+h-(0.9f*w)),w,w);
        			thisCommand = COMMAND_TYPE.UP;
        		} else {
        			handBBRect = new Rect(Math.round(x+w-((0.9f)*h)),y,h,h);
        			thisCommand = COMMAND_TYPE.RIGHT;
        		}
        		
        		boolean handBBRectValid = true;
        		
        		// double check new BB isn't out of bounds
        		if (handBBRect.x < 0)
        			handBBRectValid = false;
        		if (handBBRect.x>N)
        			handBBRectValid = false;
        		if (handBBRect.y < 0)
        			handBBRectValid = false;
        		if (handBBRect.y > M)
        			handBBRectValid = false;
        		if (handBBRect.x+handBBRect.width > N)
        			handBBRectValid = false;
        		if (handBBRect.y+handBBRect.height > M)
        			handBBRectValid = false;
        		
        		if (handBBRectValid==false) {
        			Log.d(TAG,"handBBRect Invalid"+handBBRect.x+","+handBBRect.y+","+handBBRect.width+","+handBBRect.height);
        			Log.d(TAG,"handBBRect M="+M+" N="+N);
        		}
        		
        		origHandBBRect = new Rect(handBBRect.x*mUndersamplingFactor,handBBRect.y*mUndersamplingFactor,handBBRect.width*mUndersamplingFactor,handBBRect.height*mUndersamplingFactor);
        		
        		if (handBBRectValid) {
        			
        			// extract origHandBBRect from hi rez images
        			
//        			Log.d(TAG,"mRgbasize = "+mRgbaPrev.size()+" "+origHandBBRect.x + " "+origHandBBRect.y + " "+origHandBBRect.size());
//        			
//        			
        			Mat rgbPrevBB = mRgbaPrev.submat(origHandBBRect);
        			Mat rgbBB = mRgbaFull.submat(origHandBBRect);
        			
        			Mat rPrevBB = new Mat();
        			Mat rBB = new Mat();
        			
        			Mat gPrevBB = new Mat();
        			Mat gBB = new Mat();
        			
        			Mat bPrevBB = new Mat();
        			Mat bBB = new Mat();
        			
        			Mat rDiff = new Mat();
        			Mat gDiff = new Mat();
        			Mat bDiff = new Mat();
        			
        			Core.extractChannel(rgbPrevBB, rPrevBB, 0);
        			Core.extractChannel(rgbPrevBB, gPrevBB, 1);
        			Core.extractChannel(rgbPrevBB, bPrevBB, 2);
        			
        			Core.extractChannel(rgbBB, rBB, 0);
        			Core.extractChannel(rgbBB, gBB, 1);
        			Core.extractChannel(rgbBB, bBB, 2);
        			
        			Core.absdiff(rBB, rPrevBB, rDiff);
        	        Core.absdiff(gBB, gPrevBB, gDiff);
        	        Core.absdiff(bBB, bPrevBB, bDiff);
        	        
        	        Imgproc.threshold(rDiff, rDiff, 25, 255, Imgproc.THRESH_BINARY);
        	        Imgproc.threshold(gDiff, gDiff, 25, 255, Imgproc.THRESH_BINARY);
        	        Imgproc.threshold(bDiff, bDiff, 25, 255, Imgproc.THRESH_BINARY);
        	        
        	        List<Mat> rgbBBSplit = new ArrayList<Mat>(3);
        	        
        	        
        	        rgbBBSplit.add(0, rDiff);
        	        rgbBBSplit.add(1, rDiff);
        	        rgbBBSplit.add(2, bDiff);
        	        
        	        Mat handBoundingBox = new Mat();
        	        
        	        rgbBB.release();
        	        
        	        Core.merge(rgbBBSplit, rgbBB);
        	        Imgproc.cvtColor(rgbBB, handBoundingBox, Imgproc.COLOR_RGB2GRAY);
        	        

        		
        			//Mat handBoundingBox = mFGmask.submat(origHandBBRect);
        			
//        	        Mat kernelBB = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3,3));
//        	        
//        	        //Imgproc.erode(handBoundingBox, handBoundingBox, kernelBB);
//        	        
//  
//        	        Imgproc.dilate(handBoundingBox, handBoundingBox, kernelBB);
        	     
        	        //Imgproc.threshold(handBoundingBox, handBoundingBox, 15, 255, Imgproc.THRESH_BINARY);
        			
	        		mFreezeFrame.release();
	        		mFreezeFrame = handBoundingBox.clone();
	        		Imgproc.cvtColor(mFreezeFrame, mFreezeFrame, Imgproc.COLOR_GRAY2RGB);
        			
//        			mFreezeFrame.release();
//        			mFreezeFrame = mRgbaFull.clone();
	        		
	        		//Imgproc.GaussianBlur(handBoundingBox, handBoundingBox, new Size(3, 3), 0);
	        		
	        		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
	        		
	        		Imgproc.findContours(handBoundingBox, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
	        		
	        		double maxArea = -1;
	        		int maxAreaIdx = -1;
	        		MatOfPoint contour = new MatOfPoint();
	        		
	        		for (int idx = 0; idx < contours.size(); idx++) {
	        			contour = contours.get(idx);
	        			double contourarea = Imgproc.contourArea(contour);
	        			if (contourarea > maxArea) {
	        				maxArea = contourarea;
	        				maxAreaIdx = idx;
	
	        				Log.i(TAG, "max contour idx: " + idx);
	        			}
	        		}
	        		
	        		MatOfInt hull = new MatOfInt();
	        		MatOfInt4 defects = new MatOfInt4();
	
	        		if (maxAreaIdx > -1) // contour detected
	        		{
	        	
		        		MatOfPoint2f new_contour = new MatOfPoint2f();
		        		double epsilon = 5;// debug here
		        		 
		        		contours.get(maxAreaIdx).convertTo(new_contour, CvType.CV_32FC2);
		        		//Processing on mMOP2f1 which is in type MatOfPoint2f
		        		Imgproc.approxPolyDP(new_contour, new_contour, epsilon, true); 
		        		//Convert back to MatOfPoint and put the new values back into the contours list
		        		new_contour.convertTo(contour, CvType.CV_32S); 
		        		 
		        		Imgproc.convexHull(contour, hull);
		        		
		        		if (contour.size().height>3) {
		
			        		Imgproc.convexityDefects(contour, hull, defects);
			
			        		long defect_num = defects.total();
			        		
			        		// hull to matofpoint
			        		MatOfPoint mopOut = new MatOfPoint();
			        		mopOut.create((int)hull.size().height,1,CvType.CV_32SC2);
		
			        		for(int j = 0; j < hull.size().height ; j++)
			        		{
			        		   int index = (int)hull.get(j, 0)[0];
			        		   double[] point = new double[] {
			        		       contour.get(index, 0)[0], contour.get(index, 0)[1]
			        		   };
			        		   mopOut.put(j, 0, point);
			        		}        
			        		
			        		List <MatOfPoint> hull_list = new ArrayList<MatOfPoint>();
			        		
			        		hull_list.add(0,mopOut);
			        		
			        		Imgproc.drawContours(mFreezeFrame, hull_list, 0, HAND_CONTOUR_COLOR, 2);
			        		
			        		// defect point reduce
			        		List<Point> vertex_point_list = new ArrayList<Point>();
			        		List<Point> defect_point_list = new ArrayList<Point>();
			        		int[] depth_list = new int[(int) defect_num];
	
			        		int DEPTH_THRESHOLD = 2000;
			        		int real_defect_num = 0;
	
			        		double[] point = new double[] { 0, 0 };
			        		Point current = new Point(point);
	
			        		int depth, index;
			        		
			        		Log.d(TAG,"Defect_num = "+ defect_num);
			        		
			        		double mean_depth=0;
			        		
			        		for (int j = 0; j < (int) defect_num; j++) {
	
				        		depth = (int) defects.get(j, 0)[3];
				        		if (depth < DEPTH_THRESHOLD)
				        			continue;
		
				        		Log.d(TAG, "defect depth=" + depth);
				        		depth_list[real_defect_num] = depth;
				        		real_defect_num++;
				        		
				        		mean_depth = mean_depth + depth;
		
				        		index = (int) defects.get(j, 0)[0];
				        		 
				        		current = new Point(contour.get(index, 0)[0],contour.get(index, 0)[1]);
				        		 
				        		// current.x = contour.get(index, 0)[0];
				        		// current.y = contour.get(index, 0)[1];
		
				        		vertex_point_list.add(current);
		
				        		index = (int) defects.get(j, 0)[2];
		
				        		// current.x = contour.get(index, 0)[0];
				        		// current.y = contour.get(index, 0)[1];
				        		current = new Point(contour.get(index, 0)[0],contour.get(index, 0)[1]);
				        		defect_point_list.add(current);
	
			        		}
			        		
			        		mean_depth = mean_depth/real_defect_num;
			        		
			        		Log.d(TAG,"Mean Depth= " + mean_depth);
			        		
			        		//calculate depth variance
			        		
			        		double variance_depth=0;
			        		
			        		for (int j=0;j<real_defect_num;j++) {
			        			
			        			variance_depth = variance_depth + (Math.pow((depth_list[j]-mean_depth),2));
			        			
			        		}
			        		
			        		variance_depth = variance_depth/real_defect_num;
			        		
			        		double normalized_variance_depth = Math.sqrt(variance_depth/Math.sqrt(maxArea));
			        		
			        		Log.d(TAG,"Normal Variance Depth= " + normalized_variance_depth);
			        		 
			        		Point p = new Point();
			        		p.x = 1;
			        		p.y = 1;
//			        		Core.putText(mFreezeFrame, Integer.toString(real_defect_num), p,
//			        		Core.FONT_HERSHEY_SIMPLEX, 2, WHITE, 3);
			        		 
			        		System.out.printf("real number defect =%d\n",real_defect_num);
	
			        		for (int k = 0; k < real_defect_num; k++) {
				        		Point vertex = vertex_point_list.get(k);
				        		Point defect = defect_point_list.get(k);
				        		Core.circle(mFreezeFrame, vertex, 2, new Scalar(10, 25, 155), 2);
				        		Core.circle(mFreezeFrame, defect, 2, new Scalar(280, 0, 55), 2);
		
				        		// double fontScale = 2;
				        		// Core.putText(rgba, Integer.toString(depth_list[i]), defect,
				        		// Core.FONT_HERSHEY_SIMPLEX, fontScale, mWhilte, 3);
			        		}
			        		
			        		if (real_defect_num>5 && normalized_variance_depth<600 && normalized_variance_depth>200) {
			        			if (amplitudeInOrientation>bestGestureAIO) {
			        				bestGestureAIO = amplitudeInOrientation;
			        				mCommand = thisCommand;
				        			mCommandValid = true;
				        			mLastFrame = mFrameNum;
				        			mDefectNum = real_defect_num;
			        			}
			        		} else if (real_defect_num>2) {
			        			if (amplitudeInOrientation>bestGestureAIO) {
			        				bestGestureAIO = amplitudeInOrientation;
			        				mCommand = thisCommand;
				        			mCommandValid = true;
				        			mLastFrame = mFrameNum;
				        			mDefectNum = real_defect_num;
			        			}
			        		}
			        		
		        		}
	        		}
        		}
        		
        		//Core.rectangle(mFreezeFrame, origHandBBRect.tl(), origHandBBRect.br(), HAND_RECT_COLOR, 2);
        		
        		Point p = new Point();
    	    	p.x = 100;
    	    	p.y = 100;
            	
            	if (mCommandValid && !commandTimeOut) {
            		
            		if (mCommand == COMMAND_TYPE.UP) {
            			
            			if(mDefectNum>5) {
            			if(mediaPlayer.isPlaying())
            			mediaPlayer.setVolume(maxVolume, maxVolume);
            			//Core.putText(mFreezeFrame, "UP", p,Core.FONT_HERSHEY_SIMPLEX, 2, WHITE, 3);
            			Log.d(TAG,"Command = UP");
            			} else if (mDefectNum>2) {
            				if(mediaPlayer.isPlaying()) {
            					mediaPlayer.seekTo(30000);
            				}
            			}
            		}
            		
    				if (mCommand == COMMAND_TYPE.DOWN) {
    					if(mediaPlayer.isPlaying())
    					mediaPlayer.setVolume(minVolume, minVolume); 
    					//Core.putText(mFreezeFrame, "DOWN", p,Core.FONT_HERSHEY_SIMPLEX, 2, WHITE, 3);
    					Log.d(TAG,"Command = DOWN");
    				}
    				
    				if (mCommand == COMMAND_TYPE.LEFT) {
    					if(!mediaPlayer.isPlaying())
    					mediaPlayer.start();
    					//Core.putText(mFreezeFrame, "LEFT", p,Core.FONT_HERSHEY_SIMPLEX, 2, WHITE, 3);
    					Log.d(TAG,"Command = LEFT");
    				}
    				
    				if (mCommand == COMMAND_TYPE.RIGHT) {
    					if(mediaPlayer.isPlaying())
    						mediaPlayer.pause();
    					//Core.putText(mFreezeFrame, "RIGHT", p,Core.FONT_HERSHEY_SIMPLEX, 2, WHITE, 3);
    					Log.d(TAG,"Command = RIGHT");
    				}
    				
    				commandTimeOut = true;
            		
            	}
        		
                
        	}
        }
            
        
        
        Mat flippedOutput = new Mat();
        
		Core.flip(mFreezeFrame, flippedOutput, 1);

		//Imgproc.resize(flippedOutput, mOutputImage, new Size(), mUndersamplingFactor, mUndersamplingFactor, Imgproc.INTER_LINEAR);
		Imgproc.resize(flippedOutput, mOutputImage, origSize, 0, 0, Imgproc.INTER_LINEAR);
		
		
        mRed.release();
        mGreen.release();
        mBlue.release();
        flippedOutput.release();
//        tmp.release();

        kernel.release();
        
//        faces.release();
        mRgbaSplit.clear();
        
        mFrameNum ++;
        

        
        if (mFrameNum % 5 == 0) {
        	//Core.addWeighted(mRgbaPrev, 0.8, mRgbaFull, 0.2, 0, mRgbaPrev);
        	mRgbaPrev.release();
        	mRgbaPrev = mRgbaFull.clone();
        	
        }
		
        return mOutputImage;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
   
        return true;
    }

    
}
