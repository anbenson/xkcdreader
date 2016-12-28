package com.benson.andrew.xkcdreader;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.picasso.Picasso;

import com.benson.andrew.xkcdreader.ShakeDetector.OnShakeListener;

public class MainActivity extends AppCompatActivity {

    private final class XKCDData {
        private int num;
        private String title;
        private int month;
        private int day;
        private int year;
        private String alt;
        private String imgUrl;

        public XKCDData(int num, String title, int month, int day, int year, String alt, String imgUrl) {
            this.num = num;
            this.title = title;
            this.month = month;
            this.day = day;
            this.year = year;
            this.alt = alt;
            this.imgUrl = imgUrl;
        }
    }

    private static final String XKCD_CURRENT_COMIC = "https://xkcd.com/info.0.json";
    private static final int MIN_SWIPE_DISTANCE = 300;

    private XKCDData currentComic;
    private int mostRecentComicNum;
    private OkHttpClient httpClient = new OkHttpClient();
    private ImageView comicImage;
    private TextView comicTitle;
    private TextView comicNum;
    private TextView comicDate;
    private TextView comicAlt;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;
    private float touchDownX;
    private float touchUpX;

    // Private API

    private String getUrlForComicNum(int num) {
        return "https://xkcd.com/" + String.valueOf(num) + "/info.0.json";
    }

    private void setCurrentComicImage() {
        if (currentComic != null) {
            final Context context = this;
            runOnUiThread(new Runnable() {
                public void run() {
                    Picasso.with(context)
                        .load(currentComic.imgUrl)
                        .resize(comicImage.getWidth(), 0)
                        .into(comicImage);
                    comicTitle.setText(currentComic.title);
                    comicNum.setText("#"+String.valueOf(currentComic.num));
                    comicDate.setText("POSTED " + currentComic.month + "/" + currentComic.day + "/" + currentComic.year);
                    comicAlt.setText("Alt-text: " + currentComic.alt);
                }
            });
        }
    }

    private void updateForDataFromXkcdUrl(final String url) {
        Request req = new Request.Builder()
            .url(url)
            .build();
        httpClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Request request, IOException e) {
                return;
            }

            @Override public void onResponse(Response response) {
                try {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    Gson gson = new Gson();
                    JsonObject obj;
                    try {
                        obj = gson.fromJson(response.body().string(), JsonObject.class);
                    } catch (IOException e) {
                        return;
                    }

                    int num = obj.getAsJsonPrimitive("num").getAsInt();
                    String title = obj.getAsJsonPrimitive("safe_title").getAsString();
                    int month = obj.getAsJsonPrimitive("month").getAsInt();
                    int day = obj.getAsJsonPrimitive("day").getAsInt();
                    int year = obj.getAsJsonPrimitive("year").getAsInt();
                    String alt = obj.getAsJsonPrimitive("alt").getAsString();
                    String imgUrl = obj.getAsJsonPrimitive("img").getAsString();

                    currentComic = new XKCDData(num, title, month, day, year, alt, imgUrl);
                    setCurrentComicImage();

                    if (url.equals(XKCD_CURRENT_COMIC)) {
                        mostRecentComicNum = num;
                    }
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    // Shake handling

    private void handleShakeEvent(int count) {
        int num = 404;
        while (num == 404) {
            num = ThreadLocalRandom.current().nextInt(1, mostRecentComicNum + 1);
        }
        updateForDataFromXkcdUrl(getUrlForComicNum(num));
    }

    // Swipe handling

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                touchUpX = event.getX();
                float deltaX = touchDownX - touchUpX;

                if (Math.abs(deltaX) > MIN_SWIPE_DISTANCE) {
                    if (deltaX < 0) {
                        updateForDataFromXkcdUrl(getUrlForComicNum(currentComic.num-1));
                    } else {
                        updateForDataFromXkcdUrl(getUrlForComicNum(currentComic.num+1));
                    }
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    // FAB click handling

    private int dpToPx(int dp) {
        Resources r = this.getResources();
        int px = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            r.getDisplayMetrics()
        );
        return px;
    }

    private void presentSearchDialog() {
        final EditText editText = new EditText(this);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        editText.setWidth(dpToPx(30));
        editText.setGravity(Gravity.CENTER_HORIZONTAL);
        editText.setLayoutParams(params);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Jump to a Comic");
        alertDialogBuilder
                .setView(editText)
                .setPositiveButton("JUMP", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        String sNum = editText.getText().toString();
                        if (sNum.length() > 0) {
                            updateForDataFromXkcdUrl(getUrlForComicNum(Integer.parseInt(sNum)));
                        }
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();

        alertDialog.show();
    }

    // Init code

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presentSearchDialog();
            }
        });

        // ShakeDetector initialization
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();
        shakeDetector.setOnShakeListener(new OnShakeListener() {
            @Override
            public void onShake(int count) {
                handleShakeEvent(count);
            }
        });

        // grab UI elements
        comicImage = (ImageView) findViewById(R.id.comicImage);
        comicTitle = (TextView) findViewById(R.id.comicTitle);
        comicNum = (TextView) findViewById(R.id.comicNum);
        comicDate = (TextView) findViewById(R.id.comicDate);
        comicAlt = (TextView) findViewById(R.id.comicAlt);

        // initially show current comic
        updateForDataFromXkcdUrl(XKCD_CURRENT_COMIC);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onPause() {
        sensorManager.unregisterListener(shakeDetector);
        super.onPause();
    }
}
