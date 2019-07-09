/*
 * Copyright 2016 Keval Patel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rakshith_ACUN.tflite;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.CameraError;
import com.androidhiddencamera.HiddenCameraService;
import com.androidhiddencamera.HiddenCameraUtils;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraFocus;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.os.SystemClock.sleep;


public class DemoCamService extends HiddenCameraService {


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    public static int age = 21;
    public static final int INPUT_SIZE = 64;
    private final String INPUT_NAME = "input";
    private final String OUTPUT_NAME = "output";

    private static final String MODEL_PATH = "model.tflite";
    public static final String LABEL_PATH = "lable.txt";
    public static final boolean QUANT=false;

    private Classifier classifier;
    private ExecutorService executor = Executors.newSingleThreadExecutor();



    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            if (HiddenCameraUtils.canOverDrawOtherApps(this)) {
                CameraConfig cameraConfig = new CameraConfig()
                        .getBuilder(this)
                        .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                        .setCameraResolution(CameraResolution.MEDIUM_RESOLUTION)
                        .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                        .setCameraFocus(CameraFocus.AUTO)
                        .build();

                startCamera(cameraConfig);

                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DemoCamService.this,
                                "Capturing image.", Toast.LENGTH_SHORT).show();


                        //Calling the tf_model.pb
                        predictTensorAge();


                        initTensorFlowAndLoadModel();

                        takePicture();
                    }
                }, 2000L);
            } else {

                //Open settings to grant permission for "Draw other apps".
                HiddenCameraUtils.openDrawOverPermissionSetting(this);
            }
        } else {

            //TODO Ask your parent activity for providing runtime permission
            Toast.makeText(this, "Camera permission not available", Toast.LENGTH_SHORT).show();
        }
        return START_NOT_STICKY;
    }

    private void predictTensorAge() { }

    @Override
    public void onImageCapture(@NonNull File imageFile) {
        Toast.makeText(this,
                "Captured image size is : " + imageFile.length( )  ,
                Toast.LENGTH_SHORT)
                .show();
         FaceDetector myFaceDetect;
         FaceDetector.Face[] myFace;
        float myEyesDistance;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        int targetWidth = bitmap.getWidth();
        int targetHeight = bitmap.getHeight();

        Matrix matrix = new Matrix();
        matrix.postScale(1f,1f);
        myFace = new FaceDetector.Face[5];
        myFaceDetect = new FaceDetector(targetWidth, targetHeight,
                5);
        int numberOfFaceDetected = myFaceDetect.findFaces(
                bitmap, myFace);
        Bitmap resizedBitmap = null;
        if (numberOfFaceDetected > 0) {
            FaceDetector.Face face = myFace[0];
            PointF myMidPoint = new PointF();
            face.getMidPoint(myMidPoint);
            myEyesDistance = face.eyesDistance();


            resizedBitmap = Bitmap.createBitmap(bitmap,
                    (int) (myMidPoint.x - myEyesDistance),
                    (int) (myMidPoint.y - myEyesDistance),64,64,matrix, true);


            final List<Classifier.Recognition> results = classifier.recognizeImage(resizedBitmap);
            Toast.makeText(getBaseContext(),"      "+results,Toast.LENGTH_SHORT).show();


            if(results.get(1).toString().equalsIgnoreCase("child") || age<18 ) {
                Toast.makeText(getBaseContext(), "Age verified Access denied", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getBaseContext(), denied.class);
                startActivity(intent);
            }
            else{
                Toast.makeText(getBaseContext(), " Age Verified:  Access Granted", Toast.LENGTH_SHORT).show();

            }

        } else {
            Toast.makeText(getBaseContext(),"No Faces Dected",Toast.LENGTH_SHORT).show();
           // Intent intent = new Intent(getBaseContext(),Nofave.class);
           // startActivity(intent);
            resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0
                    ,64, 64, matrix, true);
            resizedBitmap = resizedBitmap.createScaledBitmap(resizedBitmap, INPUT_SIZE, INPUT_SIZE, false);
            final List<Classifier.Recognition> results = classifier.recognizeImage(resizedBitmap);
            Toast.makeText(getBaseContext(),"      "+results,Toast.LENGTH_SHORT).show();




            if(results.get(1).toString().equalsIgnoreCase("child") || age<18 ) {

                sleep(3000);
                Toast.makeText(getBaseContext(), "Age verified Access denied", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getBaseContext(), denied.class);
                startActivity(intent);
            }
            else{
                sleep(3000);
                Toast.makeText(getBaseContext(), " Age Verified:  Access Granted", Toast.LENGTH_SHORT).show();

            }


        }





        // Do something with the image...

        stopSelf();
    }


    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_PATH,
                            LABEL_PATH,
                            INPUT_SIZE,
                            QUANT);
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }


    @Override
    public void onCameraError(@CameraError.CameraErrorCodes int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                //Camera open failed. Probably because another application
                //is using the camera
                Toast.makeText(this, R.string.error_cannot_open, Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_IMAGE_WRITE_FAILED:
                //Image write failed. Please check if you have provided WRITE_EXTERNAL_STORAGE permission
                Toast.makeText(this, R.string.error_cannot_write, Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_CAMERA_PERMISSION_NOT_AVAILABLE:
                //camera permission is not available
                //Ask for the camera permission before initializing it.
                Toast.makeText(this, R.string.error_cannot_get_permission, Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_OVERDRAW_PERMISSION:
                //Display information dialog to the user with steps to grant "Draw over other app"
                //permission for the app.
                HiddenCameraUtils.openDrawOverPermissionSetting(this);
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_FRONT_CAMERA:
                Toast.makeText(this, R.string.error_not_having_camera, Toast.LENGTH_LONG).show();
                break;
        }

        stopSelf();
    }
}
