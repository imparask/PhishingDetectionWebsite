package com.finalyear.phishingwebsitedetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseCustomLocalModel;
import com.google.firebase.ml.custom.FirebaseCustomRemoteModel;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelInterpreterOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity" ;
    private FirebaseCustomRemoteModel remoteModel;
    private FirebaseModelInterpreter interpreter;
    private FirebaseModelInputs modelInputs;
    private FirebaseModelInputOutputOptions inputOutputOptions;
    private TextView mResult;
    private EditText mGetUrl;
    private Button mCheckUrl;
    private ProgressDialog mProgressDialog;
    private ProgressBar progressBar;
    private float[][] featureList;
    private boolean isDownloaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mResult = findViewById(R.id.tv_result);
        mGetUrl = findViewById(R.id.et_getUrl);
        mCheckUrl = findViewById(R.id.bt_checkUrl);
        progressBar = findViewById(R.id.pgb_MainActivity);

        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(getApplicationContext()));
        }

        mProgressDialog = new ProgressDialog(MainActivity.this);

        initialiseMLModel();

        mCheckUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = mGetUrl.getText().toString();
                boolean flag = android.util.Patterns.WEB_URL.matcher(url).matches();
                if(flag) {
                    progressBar.setVisibility(View.VISIBLE);
                    featureExtraction(url);
                }
                else{
                    mGetUrl.setError("Not a Valid URL");
                }
            }
        });
    }

    private void featureExtraction(final String url) {

        Thread newThread = new Thread() {
            @Override
            public void run() {
                Python python = Python.getInstance();
                PyObject pyObject = python.getModule("FeatureExtraction");
                PyObject outputObj = pyObject.callAttr("getAttributes",url);

                Object[] outArr = outputObj.asList().toArray();

                List<Float> outList = new ArrayList<>();

                Log.d(TAG,outputObj.toString());
                String ele = outArr[0].toString();

                for(int i=0;i<ele.length();i++){
                    if(ele.charAt(i)=='0' || ele.charAt(i)=='1' || ele.charAt(i)=='2'){
                        float x = Float.parseFloat(Character.toString(ele.charAt(i)));
                        outList.add(x);
                    }
                }
                featureList = new float[1][outList.size()];

                int i =0;
                for(Float f : outList){
                    featureList[0][i++]=f;
                }
                getUrlResult();
            }
        };
        newThread.start();
    }

    private void initialiseMLModel() {

        mProgressDialog.show();
        remoteModel = new FirebaseCustomRemoteModel.Builder(getString(R.string.model_name)).build();

        FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions.Builder().build();
        mProgressDialog.setMessage("Getting Resources...");
        FirebaseModelManager.getInstance().download(remoteModel, conditions)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        mProgressDialog.setMessage("Initialising Parameters...");
                        isDownloaded = true;
                        initialiseInterpreter();
                    }
                });
    }

    private void initialiseInterpreter() {

        FirebaseModelInterpreterOptions options;
        if (isDownloaded) {
            options = new FirebaseModelInterpreterOptions.Builder(remoteModel).build();
            try {
                interpreter = FirebaseModelInterpreter.getInstance(options);

                inputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                        .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 13})
                        .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 1})
                        .build();

            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                mProgressDialog.cancel();
            }
        }
    }

    private void getUrlResult(){
        if(isDownloaded) {
            try {
                modelInputs = new FirebaseModelInputs.Builder().add(featureList).build();
                interpreter.run(modelInputs, inputOutputOptions)
                        .addOnSuccessListener(new OnSuccessListener<FirebaseModelOutputs>() {
                            @Override
                            public void onSuccess(FirebaseModelOutputs result) {

                                float[][] output = result.getOutput(0);
                                float modelOutput = output[0][0];

                                Log.d(TAG,"Model Prediction Result : "+ modelOutput);
                                printResult(modelOutput);
                            }
                        });
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void printResult(float modelOutput) {
        String text;
        if(modelOutput >= 0.3){
            text = "Phishing Website";
            mResult.setText(text);
            mResult.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.phishing_website));
        }
        else{
            text = "Legitimate Website";
            mResult.setText(text);
            mResult.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.legitimate_website));
        }
        progressBar.setVisibility(View.INVISIBLE);
    }
}
