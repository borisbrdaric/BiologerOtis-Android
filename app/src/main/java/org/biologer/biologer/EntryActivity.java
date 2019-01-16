package org.biologer.biologer;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.model.LatLng;

import org.biologer.biologer.model.Entry;
import org.biologer.biologer.model.Stage;
import org.biologer.biologer.model.StageDao;
import org.biologer.biologer.model.Taxon;
import org.biologer.biologer.model.TaxonDao;
import org.biologer.biologer.model.UserData;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EntryActivity extends AppCompatActivity implements View.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "Biologer.Entry";

    private static final int REQUEST_POL = 1001;
    private static final int REQUEST_TAKSON = 1002;
    private static final int REQUEST_RAZVOJNI_STADIJUM = 1003;
    private static final int REQUEST_ZIVA = 1004;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL = 1005;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1006;
    private static final int REQUEST_LOCATION = 1;

    private String mCurrentPhotoPath;

    private LocationManager locationManager;
    private LocationListener locationListener;
    String latitude = "0", longitude = "0";
    private double elev = 0.0;
    private LatLng nLokacija = new LatLng(0.0, 0.0);
    private Double acc = 0.0;
    private int IMAGE_VIEW = 0;
    private static int mInterval = 5000;
    private static final String IMAGE_DIRECTORY = "/biologer";
    private int GALLERY = 1, CAMERA = 2, MAP = 3;

    private TextView tvTakson, tv_gps, tvStage, tv_more, tv_latitude, tv_longitude;
    private CustomEditText et_razlogSmrti, et_komentar, et_brojJedinki;
    AutoCompleteTextView acTextView;
    private ImageView ib_pic1, ib_pic2, ib_pic3, iv_map;
    private CheckBox check_dead;
    private LinearLayout more, smrt;
    private ArrayList<Taxon> taksoni;
    private boolean save_enabled = false;
    private ArrayList<Stage> stages;
    private RadioButton rb_male, rb_female;
    private Uri contentURI;
    private String slika1, slika2, slika3;
    private SwipeRefreshLayout swipe;

    Calendar calendar;
    SimpleDateFormat simpleDateFormat;

    // Get the user data
    List<UserData> list = App.get().getDaoSession().getUserDataDao().loadAll();
    UserData userdata = list.get(0);
    private int user_data_license = userdata.getData_license();
    private int user_image_license = userdata.getImage_license();

    String project_name = SettingsManager.getProjectName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);

        // Add a toolbar to the Activity
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.entry_title);
        // Add the back button to the toolbar
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        //liste taksona i stage-ova iz baze
        taksoni = (ArrayList<Taxon>) App.get().getDaoSession().getTaxonDao().loadAll();
        stages = (ArrayList<Stage>) App.get().getDaoSession().getStageDao().loadAll();

        //proveri da li ima permisions, ako nema dodaj eksplicitno
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL);
            }
        }

        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this);
        tv_latitude = findViewById(R.id.tv_latitude);
        tv_longitude = findViewById(R.id.tv_longitude);
        tv_gps = findViewById(R.id.tv_gps);

        tvStage = findViewById(R.id.tvStage);
        tvStage.setOnClickListener(this);
        tvStage.setEnabled(false);

        et_razlogSmrti = (CustomEditText) findViewById(R.id.et_razlogSmrti);
        et_komentar = (CustomEditText) findViewById(R.id.et_komentar);
        et_brojJedinki = (CustomEditText) findViewById(R.id.et_brojJedinki);
        rb_male = findViewById(R.id.rb_musko);
        rb_female = findViewById(R.id.rb_zensko);

        more = (LinearLayout) findViewById(R.id.more);
        ViewGroup.LayoutParams params_more = more.getLayoutParams();
        params_more.height = 0;
        more.setLayoutParams(params_more);

        /*
        * This adds a checkbox for dead specimen and the comment on dead specimen.
        */
        check_dead = (CheckBox) findViewById(R.id.dead_specimen);
        check_dead.setOnClickListener(this);
        smrt = (LinearLayout) findViewById(R.id.smrt);
        ViewGroup.LayoutParams params = smrt.getLayoutParams();
        params.height = 0;
        smrt.setLayoutParams(params);

        ib_pic1 = (ImageView) findViewById(R.id.ib_pic1);
        ib_pic1.setOnClickListener(this);
        ib_pic2 = (ImageView) findViewById(R.id.ib_pic2);
        ib_pic2.setOnClickListener(this);
        ib_pic3 = (ImageView) findViewById(R.id.ib_pic3);
        ib_pic3.setOnClickListener(this);
        iv_map = (ImageView) findViewById(R.id.iv_map);
        iv_map.setOnClickListener(this);
        tv_more = (TextView) findViewById(R.id.tv_more);
        tv_more.setOnClickListener(this);

        // Autocomplete textbox for Taxon entry
        final String[] taksonometrija1 = new String[taksoni.size()];
        for (int i = 0; i < taksoni.size(); i++) {
            taksonometrija1[i] = taksoni.get(i).getName();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, taksonometrija1);
        acTextView = findViewById(R.id.tvTakson_auto);
        acTextView.setAdapter(adapter);
        acTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (acTextView.getText().toString().length() != 0) {
                    tvStage.setEnabled(true);
                    // Enable/disable save button from Toolbar
                    save_enabled = true;
                    invalidateOptionsMenu();
                } else {
                    tvStage.setEnabled(true);
                    // Enable/disable save button from Toolbar
                    save_enabled = false;
                    invalidateOptionsMenu();
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                if (acTextView.getText().toString().length() != 0) {
                    // Enable/disable save button from Toolbar
                    save_enabled = true;
                    invalidateOptionsMenu();
                } else {
                    // Enable/disable save button from Toolbar
                    save_enabled = false;
                    invalidateOptionsMenu();
                }
            }
        });

        // Define locatonListener and locationManager in order to
        // to receive the Location.
        // Call the function updateLocation() to do all the magic...
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                nLokacija = new LatLng(location.getLatitude(), location.getLongitude());
                setLocationValues(location.getLatitude(), location.getLongitude());
                elev = location.getAltitude();
                acc = Double.valueOf(location.getAccuracy());
                tv_gps.setText(String.format(Locale.ENGLISH, "%.0f", acc));
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {
            }

            @Override
            public void onProviderEnabled(String s) {
            }

            @Override
            public void onProviderDisabled(String s) {
                buildAlertMessageNoGps();
            }
        };

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        getLocation(100, 2);
    }

    // Add Save button in the right part of the toolbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.save_menu, menu);
        return true;
    }

    // Customize Save item to enable if when needed
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_save);
        if (save_enabled) {
            item.setEnabled(true);
            item.getIcon().setAlpha(255);
        } else {
            // disabled
            item.setEnabled(false);
            item.getIcon().setAlpha(30);
        }
        return true;
    }

    // Proces running after clicking the toolbar buttons
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if (id == android.R.id.home) {
            this.finish();
            return true;
        }
        if (id == R.id.action_save) {
            String naziv = acTextView.getText().toString();
            Taxon taxon = App.get().getDaoSession().getTaxonDao().queryBuilder().where(TaxonDao.Properties.Name.eq(naziv)).unique();
            if (taxon == null) {
                acTextView.setError(getString(R.string.taxa_mandatory));
            } else {
                saveEntry();
            }
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    //klikabilni view-ovi
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tvStage:
                showStageDialog();
                break;
            case R.id.ib_pic1:
                IMAGE_VIEW = 1;
                showPictureDialog();
                break;
            case R.id.ib_pic2:
                IMAGE_VIEW = 2;
                showPictureDialog();
                break;
            case R.id.ib_pic3:
                IMAGE_VIEW = 3;
                showPictureDialog();
                break;
            case R.id.tv_more:
                showMore();
                break;
            case R.id.dead_specimen:
                showDeadComment();
                break;
            case R.id.iv_map:
                showMap();
                break;
        }
    }

    /*
    This calls other functions to get the values, check the validity of
    the data and to finally save it into the entry
     */
    private void saveEntry() {
        String naziv = acTextView.getText().toString();
        Taxon taxon = App.get().getDaoSession().getTaxonDao().queryBuilder().where(TaxonDao.Properties.Name.eq(naziv)).unique();
        // Insure that the taxon is entered correctly
        if (taxon == null) {
            acTextView.setError(getString(R.string.taxa_mandatory));
        } else {
            // If the location is not loaded, warn the user and
            // don’t send crappy data into the online database!
            if (nLokacija.latitude == 0) {
                buildAlertMessageNoCoordinates();
            } else {
                // If the location is not precise ask the user to
                // wait for a precise location, or to go for it anyhow...
                if (nLokacija.latitude > 0 && acc >= 25) {
                    buildAlertMessageUnpreciseCoordinates();
                }
            }

            if (nLokacija.latitude > 0 && (acc <= 25)) {
                // Save the taxon
                entrySaver(taxon);
            }
        }
    }

    /*
    This gathers all the data into the entry
     */
    private void entrySaver(final Taxon taxon) {
        Stage stage = (tvStage.getTag() != null) ? (Stage) tvStage.getTag() : null;
        String komentar = (et_komentar.getText().toString() != null) ? et_komentar.getText().toString() : "";
        Integer brojJedinki = (et_brojJedinki.getText().toString().trim().length() > 0) ? Integer.valueOf(et_brojJedinki.getText().toString()) : 0;
        Long selectedStage = (stage != null) ? stage.getStageId() : null;
        String razlogSmrti = (et_razlogSmrti.getText() != null) ? et_razlogSmrti.getText().toString() : "";

        calendar = Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        String fullDate = simpleDateFormat.format(calendar.getTime());
        String day = fullDate.substring(0, 2);
        String month = fullDate.substring(3, 5);
        String year = fullDate.substring(6, 10);
        String time = fullDate.substring(11, 16);
        long taxon_id = taxon.getId();
        String taxon_name = taxon.getName();

        // Get the data structure and save it into a database
        Entry entry1 = new Entry(null, taxon_id, taxon_name, year, month, day,
                komentar, brojJedinki, maleFemale(), selectedStage, String.valueOf(!check_dead.isChecked()), razlogSmrti,
                nLokacija.latitude, nLokacija.longitude, acc, elev, "", slika1, slika2, slika3,
                project_name, "", String.valueOf(user_data_license), user_image_license, time, "1,2,3");
        App.get().getDaoSession().getEntryDao().insertOrReplace(entry1);
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private String maleFemale() {
        String sex = "";
        if (rb_male.isChecked()) {
            sex = "male";
        } else if (rb_female.isChecked()) {
            sex = "female";
        }
        return sex;
    }

    private void showTaksoniDialog() {
        if (taksoni != null) {
            final String[] taksonometrija = new String[taksoni.size()];
            for (int i = 0; i < taksoni.size(); i++) {
                taksonometrija[i] = taksoni.get(i).getName();
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setItems(taksonometrija, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    tvTakson.setText(taksonometrija[i]);
                    tvTakson.setTag(taksoni.get(i));
                    if (tvTakson.getText() == getString(R.string.please_select)) {
                        tvStage.setEnabled(false);
                    } else {
                        tvStage.setEnabled(true);
                        tvStage.setHint(getString(R.string.please_select));
                        // Enable/disable save button from Toolbar
                        save_enabled = true;
                        invalidateOptionsMenu();
                    }
                }
            });
            tvStage.setText("");
            builder.show();
        }
    }

    private void showStageDialog() {
        Taxon t = App.get().getDaoSession().getTaxonDao().queryBuilder().where(TaxonDao.Properties.Name.eq(acTextView.getText())).unique();
        stages = (ArrayList<Stage>) App.get().getDaoSession().getStageDao().queryBuilder().where(StageDao.Properties.TaxonId.eq(t.getId())).list();
        if (stages != null) {
            final String[] stadijumi = new String[stages.size()];
            for (int i = 0; i < stages.size(); i++) {
                stadijumi[i] = stages.get(i).getName();
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setItems(stadijumi, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    tvStage.setText(stadijumi[i]);
                    tvStage.setTag(stages.get(i));
                }
            });
            builder.show();
        }
    }

    private void showMap() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("latlong", nLokacija);
        startActivityForResult(intent, MAP);
    }

    private void showPictureDialog() {
        AlertDialog.Builder pictureDialog = new AlertDialog.Builder(this);
        pictureDialog.setTitle(getString(R.string.choose_picture));
        String[] pictureDialogItems = {
                getString(R.string.choose_picture_gallery),
                getString(R.string.choose_picture_camera)};
        pictureDialog.setItems(pictureDialogItems,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                choosePhotoFromGallery();
                                break;
                            case 1:
                                checkCameraPermision();
                                break;
                        }
                    }
                });
        pictureDialog.show();
    }

    public void choosePhotoFromGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(galleryIntent, GALLERY);
    }

    private void takePhotoFromCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // startActivityForResult(takePictureIntent, CAMERA);
            dispatchTakePictureIntent();
        } else {
            et_razlogSmrti.setText("greska");  //?????? nemam pojma sto sam ovo stavio
        }
    }

    private void checkCameraPermision() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);
            }
        } else {
            takePhotoFromCamera();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == this.RESULT_CANCELED) {
            return;
        }
        if (requestCode == GALLERY) {
            if (data != null) {
                contentURI = data.getData();
                try {
                    File f = createImageFile();
                    copyFile(new File(getPath(data.getData())), f);
                    galleryAddPic();
                    switch (IMAGE_VIEW) {
                        case 1:
                            Glide.with(this)
                                    .load(mCurrentPhotoPath)
                                    .into(ib_pic1);
                            slika1 = mCurrentPhotoPath;
                            break;
                        case 2:
                            Glide.with(this)
                                    .load(mCurrentPhotoPath)
                                    .into(ib_pic2);
                            slika2 = mCurrentPhotoPath;
                            break;
                        case 3:
                            Glide.with(this)
                                    .load(mCurrentPhotoPath)
                                    .into(ib_pic3);
                            slika3 = mCurrentPhotoPath;
                            break;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(EntryActivity.this, "Failed!", Toast.LENGTH_SHORT).show();
                }
            }

        } else if (requestCode == CAMERA) {

            galleryAddPic();
            switch (IMAGE_VIEW) {
                case 1:
                    Glide.with(this)
                            .load(mCurrentPhotoPath)
                            .into(ib_pic1);
                    slika1 = mCurrentPhotoPath;
                    break;
                case 2:
                    Glide.with(this)
                            .load(mCurrentPhotoPath)
                            .into(ib_pic2);
                    slika2 = mCurrentPhotoPath;
                    break;
                case 3:
                    Glide.with(this)
                            .load(mCurrentPhotoPath)
                            .into(ib_pic3);
                    slika3 = mCurrentPhotoPath;
                    break;
            }

            Toast.makeText(EntryActivity.this, "Image Saved!", Toast.LENGTH_SHORT).show();
        }

        // Get data from Google MapActivity.java and save it as local variables
        if (requestCode == MAP) {
            locationManager.removeUpdates(locationListener);
            nLokacija = data.getParcelableExtra("google_map_latlong");
            setLocationValues(nLokacija.latitude, nLokacija.longitude);
            acc = Double.valueOf(data.getExtras().getString("google_map_accuracy"));
            elev = Double.valueOf(data.getExtras().getString("google_map_elevation"));
            if (data.getExtras().getString("google_map_accuracy").equals("0.0")) {
                tv_gps.setText(R.string.not_available);
            } else {
                tv_gps.setText(String.format(Locale.ENGLISH, "%.0f", acc));
            }
        }
    }

    public String getPath(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        startManagingCursor(cursor);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    private void copyFile(File sourceFile, File destFile) throws IOException {
        if (!sourceFile.exists()) {
            return;
        }

        FileChannel source = null;
        FileChannel destination = null;
        source = new FileInputStream(sourceFile).getChannel();
        destination = new FileOutputStream(destFile).getChannel();
        if (destination != null && source != null) {
            destination.transferFrom(source, 0, source.size());
        }
        if (source != null) {
            source.close();
        }
        if (destination != null) {
            destination.close();
        }
    }

    public String saveImage(Bitmap myBitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        //myBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        File wallpaperDirectory = new File(
                Environment.getExternalStorageDirectory() + IMAGE_DIRECTORY);
        // have the object build the directory structure, if needed.
        if (!wallpaperDirectory.exists()) {
            wallpaperDirectory.mkdirs();
        }

        try {
            File f = createImageFile(); /*new File(wallpaperDirectory, Calendar.getInstance()
                    .getTimeInMillis() + ".jpg");*/
            //f.createNewFile();
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bytes.toByteArray());
            MediaScannerConnection.scanFile(this,
                    new String[]{f.getPath()},
                    new String[]{"image/jpeg"}, null);
            fo.close();
            Log.d("TAG", "File Saved::--->" + f.getAbsolutePath());

            return f.getAbsolutePath();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return "";
    }

    public void showMore() {
        ViewGroup.LayoutParams params_btn_more = tv_more.getLayoutParams();
        params_btn_more.height = 0;
        tv_more.setLayoutParams(params_btn_more);
        ViewGroup.LayoutParams params = more.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        more.setLayoutParams(params);
    }

    public void showDeadComment() {
        if (check_dead.isChecked()) {
            ViewGroup.LayoutParams params = smrt.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            smrt.setLayoutParams(params);
        } else {
            ViewGroup.LayoutParams params = smrt.getLayoutParams();
            params.height = 0;
            smrt.setLayoutParams(params);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    finish();
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhotoFromCamera();
                } else {

                }
                return;
            }
        }
    }

    // Function used to retrieve the location
    private void getLocation(int time, int distance) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(EntryActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, time, distance, locationListener);
        }
    }

    private void setLocationValues(double latti, double longi) {
        latitude = String.format(Locale.ENGLISH, "%.4f", (latti));
        longitude = String.format(Locale.ENGLISH, "%.4f", (longi));
        tv_latitude.setText(latitude);
        tv_longitude.setText(longitude);
    }

    protected void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.enable_location))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.enable_location_yes), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton(getString(R.string.enable_location_no), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    // Show the message if the location is not loaded
    protected void buildAlertMessageNoCoordinates() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.location_is_zero))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.wait), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        getLocation(0, 0);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        finish();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    protected void buildAlertMessageUnpreciseCoordinates() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.unprecise_coordinates))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.wait), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        getLocation(0,0);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(getString(R.string.save_anyway), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        // Save the taxon
                        String naziv = acTextView.getText().toString();
                        Taxon taxon = App.get().getDaoSession().getTaxonDao().queryBuilder().where(TaxonDao.Properties.Name.eq(naziv)).unique();
                        entrySaver(taxon);
                        }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    // Get Location if user refresh the view
    @Override
    public void onRefresh() {
        swipe.setRefreshing(true);
        getLocation(0, 0);
        swipe.setRefreshing(false);
    }

    // Resize the picture
    private Bitmap resizePic(Bitmap image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 87, out);
        Bitmap decoded = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
        return decoded;
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(EntryActivity.this, LandingActivity.class);
        startActivity(intent);
        super.onBackPressed();
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "biologer");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                mediaStorageDir
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File

            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                /*Uri photoURI = Uri.fromFile(photoFile);*//* FileProvider.getUriForFile(this,
                        "org.biologer.biologer.fileprovider",
                        photoFile)*//*;*/
                Uri photoURI;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    photoURI = FileProvider.getUriForFile(EntryActivity.this, "org.biologer.biologer.fileprovider", photoFile);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    photoURI = Uri.fromFile(photoFile);
                }

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, CAMERA);
            }
        }
    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }
}