package com.hazer.irunner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var btnStart: Button
    private lateinit var tvInfo: TextView
    private lateinit var tvTime: TextView

    private var aMap: AMap? = null
    private var isRunning = false

    private var startTime: Long = 0L
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                tvTime.text = String.format("用时: %02d:%02d", min, sec)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val requestCode = 1001

    // 位置相关
    private var locationClient: AMapLocationClient? = null
    private val trackPoints = mutableListOf<LatLng>()
    private var totalDistance = 0.0   // 米
    private var lastLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        btnStart = findViewById(R.id.btnStart)
        tvInfo = findViewById(R.id.tvInfo)
        tvTime = findViewById(R.id.tvTime)


        mapView.onCreate(savedInstanceState)
        aMap = mapView.map

        // 定位蓝点
        val style = MyLocationStyle()
        style.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
        style.interval(2000)
        aMap?.myLocationStyle = style
        aMap?.uiSettings?.isMyLocationButtonEnabled = true
        aMap?.isMyLocationEnabled = true
        aMap?.moveCamera(CameraUpdateFactory.zoomTo(18f))

        // 权限检查
        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, requestCode)
        }

        btnStart.setOnClickListener {
            if (!isRunning) {
                startRun()
            } else {
                stopRun()
            }
        }
    }

    private fun startRun() {
        isRunning = true
        btnStart.text = "停止跑步"
        tvInfo.text = "开始记录..."
        trackPoints.clear()
        totalDistance = 0.0
        lastLocation = null

        // 开始计时
        startTime = System.currentTimeMillis()
        timerHandler.post(timerRunnable)

        startLocationUpdates()
    }

    private fun stopRun() {
        isRunning = false
        btnStart.text = "开始跑步"

        // 停止计时器
        timerHandler.removeCallbacks(timerRunnable)

        // 停止定位
        locationClient?.stopLocation()

        // 总距离（公里）
        val totalKm = totalDistance / 1000.0
        // 总用时（秒）
        val totalTimeSec = (System.currentTimeMillis() - startTime) / 1000
        val totalMin = totalTimeSec / 60
        val totalSec = totalTimeSec % 60

        // 平均配速（分/公里）
        val pace = if (totalKm > 0) (totalTimeSec / 60.0) / totalKm else 0.0
        val paceMin = pace.toInt()
        val paceSec = ((pace - paceMin) * 60).toInt()

        // 显示最终结果
        tvInfo.text = """
🏁 跑步结束
总路程：${"%.2f".format(totalKm)} km
用时：${String.format("%02d:%02d", totalMin, totalSec)}
平均配速：${paceMin}'${String.format("%02d", paceSec)}"/km
""".trimIndent()
    }




    private fun startLocationUpdates() {
        if (!checkPermissions()) return

        if (locationClient == null) {
            locationClient = AMapLocationClient(applicationContext)
            val option = AMapLocationClientOption()
            option.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            option.interval = 2000   // 2秒更新一次
            locationClient!!.setLocationOption(option)

            locationClient!!.setLocationListener(AMapLocationListener { location ->
                if (location != null && location.errorCode == 0) {
                    updateTrack(location)
                }
            })
        }
        locationClient!!.startLocation()
    }

    private fun updateTrack(location: AMapLocation) {
        val latLng = LatLng(location.latitude, location.longitude)

        // 移动地图视角
        aMap?.moveCamera(CameraUpdateFactory.changeLatLng(latLng))

        // 计算距离
        if (lastLocation != null) {
            val distance = com.amap.api.maps.AMapUtils.calculateLineDistance(lastLocation, latLng)
            totalDistance += distance

            // 画轨迹
            aMap?.addPolyline(
                PolylineOptions()
                    .add(lastLocation, latLng)
                    .width(10f)
                    .color(Color.RED)
            )
        }

        // 计算配速
        val elapsedMinutes = (System.currentTimeMillis() - startTime) / 1000.0 / 60.0
        val distanceKm = totalDistance / 1000.0
        val pace = if (distanceKm > 0) elapsedMinutes / distanceKm else 0.0  // 分/公里
        val paceMin = pace.toInt()
        val paceSec = ((pace - paceMin) * 60).toInt()

        // 统一更新 UI
        tvInfo.text = """
用时：${tvTime.text.toString().replace("用时: ", "")}
速度：${String.format("%.2f", location.speed)} m/s
路程：${String.format("%.2f", distanceKm)} km
配速：${paceMin}'${String.format("%02d", paceSec)}"/km
""".trimIndent()


        trackPoints.add(latLng)
        lastLocation = latLng
    }


    private fun checkPermissions(): Boolean {
        for (perm in permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // 权限允许后直接启用定位
            aMap?.isMyLocationEnabled = true
        }
    }

    // 生命周期同步
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient?.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
