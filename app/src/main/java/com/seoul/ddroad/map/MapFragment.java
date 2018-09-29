package com.seoul.ddroad.map;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;
import com.seoul.ddroad.R;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.seoul.ddroad.diary.SqlLiteDao;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MapFragment extends Fragment implements LocationListener, OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowClickListener {
    private static String TAG = MapFragment.class.getSimpleName();
    private GoogleMap googleMap;
    private LatLng SEOUL = new LatLng(37.56, 126.97);
    private MapView mapView;
    private LatLng curLatlng;
    private ArrayList<LatLng> latLngList;
    private Polyline polyline;
    private LocationRequest locRequest;
    private FusedLocationProviderClient fusedLocClient;
    private LocationCallback locCallback, locCallback_walk;
    private Marker marker;
    private String state = "";
    private Button btnPrevious;
    private long start, end;
    private int hour, min;
    private float km;


    @BindView(R.id.btn_walk)
    Button btn_walk;
    @BindView(R.id.btn_cafe)
    Button btn_cafe;
    @BindView(R.id.btn_hospital)
    Button btn_hospital;
    @BindView(R.id.btn_hotel)
    Button btn_hotel;
    @BindView(R.id.btn_salon)
    Button btn_salon;
    @BindView(R.id.btn_trail)
    Button btn_trail;
    @BindView(R.id.layout_result)
    RelativeLayout layout_result;
    @BindView(R.id.tv_hour)
    TextView tv_hour;
    @BindView(R.id.tv_minute)
    TextView tv_minute;
    @BindView(R.id.tv_km)
    TextView tv_km;

    public MapFragment() {

    }

    protected void createLocationRequest() {
        locRequest = new LocationRequest();
        locRequest.setInterval(2000);
        locRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createLocationRequest();
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        ButterKnife.bind(this, view);

        return view;
    }

    @OnClick(R.id.btn_walk)
    void startWalk(View view) {
        view.setSelected(!view.isSelected());
        if (view.isSelected()) { //산책 시작
            start = System.currentTimeMillis();
            latLngList = new ArrayList<>();
            changeCallback(locCallback, locCallback_walk, true);
        } else { //산책 끝
            end = System.currentTimeMillis();
            changeCallback(locCallback_walk, locCallback, false);
            calcResult(latLngList);

            Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.translate);
            btn_walk.setVisibility(View.GONE);
            layout_result.setVisibility(View.VISIBLE);
            layout_result.startAnimation(anim);
//
//            //다이얼로그
//            PolylineDialog dialog = new PolylineDialog();
//            Bundle args = new Bundle();
//            args.putParcelableArrayList("pointList", latLngList);
//            dialog.setArguments(args);
//            dialog.setTargetFragment(this, 2);
//            dialog.show(getActivity().getSupportFragmentManager(), "tag");
//            if (polyline != null)
//                polyline.remove();
        }
    }


    private void calcResult(ArrayList<LatLng> latLngList) {
        double totDistance = 0;

        LatLng prev = latLngList.get(0);
        for (LatLng latLng : latLngList) {
            totDistance += SphericalUtil.computeDistanceBetween(prev, latLng);
            prev = latLng;
        }
        km = Float.parseFloat(String.format("%.1f", totDistance));
        tv_km.setText(String.valueOf(km));

        long time = (end - start) / 1000 / 60;//분
        if (time > 60) {
            hour = (int) (time / 60);
            min = (int) (time % 60);
        } else {
            hour = 0;
            min = (int) time;
        }
        tv_hour.setText(String.valueOf(hour));
        tv_minute.setText(String.valueOf(min));
    }


    // 콜백 메소드 교체 (경로 그리기 <-> 마커 업데이트만)
    public void changeCallback(LocationCallback callback1, LocationCallback callback2, boolean isWalk) {
        if (ActivityCompat.checkSelfPermission(this.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this.getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!isWalk)
            fusedLocClient.removeLocationUpdates(callback1);
        fusedLocClient.requestLocationUpdates(locRequest, callback2, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* if (resultCode == Activity.RESULT_OK) { // 경로 저장
            latLngList = data.getExtras().getParcelableArrayList("pointList");
            drawPolyline(latLngList);

            // 스크린샷 저장
            GoogleMap.SnapshotReadyCallback callback = new GoogleMap.SnapshotReadyCallback() {
                @Override
                public void onSnapshotReady(Bitmap bitmap) {
                    String imgPath = screenshot(bitmap);
                    SqlLiteDao sqlDao = new SqlLiteDao(getContext());
                    //sqlDao.insertScreenShot(imgPath);
                }
            };
            googleMap.snapshot(callback);
            btn_walk.setVisibility(View.VISIBLE);
            Toast.makeText(this.getContext(), "저장되었습니다", Toast.LENGTH_LONG).show();
            latLngList = null;
        }
        */
    }

    public void drawPolyline(List<LatLng> pointList) {
        if (polyline != null)
            polyline.remove();
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.RED)
                .width(15)
                .addAll(pointList)
                .geodesic(true);
        polyline = googleMap.addPolyline(polylineOptions);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mapView = (MapView) getView().findViewById(R.id.map);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }
        fusedLocClient = LocationServices.getFusedLocationProviderClient(this.getContext());
        setLocCallback();
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
        startLocationUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        startLocationUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        fusedLocClient.removeLocationUpdates(locCallback);
    }

    //location 콜백메소드 정의
    public void setLocCallback() {
        locCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null)
                    return;
                for (Location location : locationResult.getLocations()) {
                    setCurLatlng(location);
                }
            }
        };

        locCallback_walk = new LocationCallback() { // 산책
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null)
                    return;
                latLngList.add(curLatlng);
                drawPolyline(latLngList);
            }
        };
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this.getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this.getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocClient.requestLocationUpdates(locRequest, locCallback, null);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        googleMap.setOnInfoWindowClickListener(this);
        setDefaultLoc(this.getContext());
        setCurMarker();
    }


    //초기 위치 설정
    public void setDefaultLoc(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;

        int result = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        if (result == PackageManager.PERMISSION_GRANTED) {
            loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        if (loc != null)
            curLatlng = new LatLng(loc.getLatitude(), loc.getLongitude());
        else
            curLatlng = SEOUL;
    }


    //위치 바뀔 때
    @Override
    public void onLocationChanged(Location location) {
        curLatlng = new LatLng(location.getLatitude(), location.getLongitude());
        setCurMarker();
    }

    // set current latlng
    public void setCurLatlng(Location location) {
        curLatlng = new LatLng(location.getLatitude(), location.getLongitude());
        setCurMarker();
    }

    // update current marker position
    public void setCurMarker() {
        if (marker != null)
            marker.setPosition(curLatlng);
        else {
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(curLatlng);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(curLatlng, 16));
            marker = googleMap.addMarker(markerOptions);
            marker.showInfoWindow();
        }

    }


    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @OnClick(R.id.btn_ok)
    public void clickOk() {
        layout_result.setVisibility(View.GONE);
        btn_walk.setVisibility(View.VISIBLE);
        latLngList = null;
        if (polyline != null)
            polyline.remove();
    }

    @OnClick(R.id.btn_capture)
    public void clickCapture() {
        GoogleMap.SnapshotReadyCallback callback = new GoogleMap.SnapshotReadyCallback() {
            @Override
            public void onSnapshotReady(Bitmap bitmap) {
                String imgPath = screenshot(bitmap);
                String content = "";
                if (hour > 0)
                    content += String.valueOf(hour) + "시간 ";
                content = content + String.valueOf(min) + "분 동안 " + String.valueOf(km) + "km 걸었어요";
                SqlLiteDao sqlDao = new SqlLiteDao(getContext());
                sqlDao.insertScreenShot(imgPath, content);
            }
        };
        googleMap.snapshot(callback);
        latLngList = null;
        if (polyline != null)
            polyline.remove();

        layout_result.setVisibility(View.GONE);
        btn_walk.setVisibility(View.VISIBLE);
        Toast.makeText(this.getContext(), "저장되었습니다", Toast.LENGTH_LONG).show();
    }


    public String screenshot(Bitmap captureBitmap) {
        FileOutputStream fos;
        File file = new File(this.getContext().getFilesDir(), "CaptureDir"); // 폴더 경로
        Log.d(TAG, this.getContext().getFilesDir().toString());
        if (!file.exists()) {  // 해당 폴더 없으면 만들어라
            file.mkdirs();
        }

        String strFilePath = file + "/" + "test" + ".png";
        File fileCacheItem = new File(strFilePath);
        try {
            fos = new FileOutputStream(fileCacheItem);
            captureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            polyline.remove();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return strFilePath;
    }


    @OnClick({R.id.btn_cafe, R.id.btn_hotel, R.id.btn_hospital, R.id.btn_salon, R.id.btn_trail})
    void clickSearch(View view) {
        view.setSelected(!view.isSelected());

        googleMap.clear();
        marker = null;
        setCurMarker();
        if (latLngList != null)
            drawPolyline(latLngList);

        if (view.isSelected()) {
            if (btnPrevious != null)
                btnPrevious.setSelected(false);
            showMarker(view.getTag().toString());
            btnPrevious = (Button) view;
        } else
            btnPrevious = null;
    }


    private void showMarker(String category) { //버튼 클릭했을 때
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier(category, "drawable", getContext().getPackageName()));
        Bitmap bitmap_resize = Bitmap.createScaledBitmap(bitmap, 120, 120, false);
        switch (category) {
            case "marker_cafe":
                for (Data data : DataSet.cafeList)
                    addMarker(data, bitmap_resize);
                break;
            case "marker_hotel":
                for (Data data : DataSet.hotelList)
                    addMarker(data, bitmap_resize);
                break;
            case "marker_hospital":
                for (Data data : DataSet.hospitalList)
                    addMarker(data, bitmap_resize);
                break;
            case "marker_salon":
                for (Data data : DataSet.salonList)
                    addMarker(data, bitmap_resize);
                break;
            case "marker_trail":
                for (Data data : DataSet.trailList)
                    addMarker(data, bitmap_resize);
                break;
        }
    }

    private void addMarker(Data data, Bitmap bitmap) { //마커 추가
        LatLng position = new LatLng(data.getLatitude(), data.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions()
                .title(data.getTitle())
                .position(position)
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap));
        Marker marker = googleMap.addMarker(markerOptions);
        marker.setTag(data);
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        DataDialog dialog = new DataDialog();
        Bundle args = new Bundle();
        args.putSerializable("data", (Data) (marker.getTag()));
        args.putParcelable("curLatlng", curLatlng);
        dialog.setArguments(args);
        dialog.show(getActivity().getFragmentManager(), "tag");
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        CameraUpdate center = CameraUpdateFactory.newLatLng(marker.getPosition());
        googleMap.animateCamera(center);
        return false;
    }


}