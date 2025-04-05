package com.example.whetherview

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import java.io.File

class MainActivity : AppCompatActivity() {
    private val filecurw = "current_whether.json"
    private val filefutuw = "future_whether.json"
    private lateinit var manager: SensorManager
    private var tempSensor: Sensor? = null
    private var presSensor: Sensor? = null
    private var humSensor: Sensor? = null
    private lateinit var cur1Text: TextView
    private lateinit var cur2Text: TextView
    private lateinit var cur3Text: TextView
    private lateinit var future1Text: TextView
    private lateinit var future2Text: TextView
    private lateinit var future3Text: TextView
    private lateinit var presBar: ProgressBar
    private lateinit var presBarText: TextView
    private var temp: Float? = null
    private var pres: Float? = null
    private var hum: Float? = null
    private var i: Int = 0
    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                when (it.sensor.type) {
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                        temp = it.values[0]
                    }
                    Sensor.TYPE_PRESSURE -> {
                        pres = it.values[0]
                        presBarText.text = "%.2f гПа".format(pres)
                        val clampedPressure = pres!!.coerceIn(900f, 1100f)
                        val partIndex = ((clampedPressure - 900) / 200 * 20).toInt()
                        presBar.progress = partIndex
                    }
                    Sensor.TYPE_RELATIVE_HUMIDITY -> {
                        hum = it.values[0]
                    }
                }
                if (temp != null && pres != null && hum != null) {
                    updateDate()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        cur1Text = findViewById(R.id.cur1TextView)
        cur2Text = findViewById(R.id.cur2TextView)
        cur3Text = findViewById(R.id.cur3TextView)
        future1Text = findViewById(R.id.future1TextView)
        future2Text = findViewById(R.id.future2TextView)
        future3Text = findViewById(R.id.future3TextView)
        val btnUpdate: Button = findViewById(R.id.btnUpdate)
        val btnClear: Button = findViewById(R.id.btnClear)
        presBar = findViewById(R.id.pressureBar)
        presBarText = findViewById(R.id.presBarText)

        btnUpdate.setOnClickListener {
            manager = getSystemService(SENSOR_SERVICE) as SensorManager
            tempSensor = manager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
            presSensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
            humSensor = manager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)

            if (tempSensor != null) {
                manager.registerListener(listener, tempSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            if (presSensor != null) {
                manager.registerListener(listener, presSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            if (humSensor != null) {
                manager.registerListener(listener, humSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        btnClear.setOnClickListener{
            clearInfo()
            updateInfo()
        }

        clearInfo()
        updateInfo()
    }

    private fun updateDate() {
        manager.unregisterListener(listener)

        val ws1 = readFromFile(filecurw)
        for (k in 0 until 3){
            ws1[k].time += 30
        }
        ws1[i].time = 0
        temp?.let { t ->
            ws1[i].temp = t
        }
        pres?.let { p ->
            ws1[i].pres = p
        }
        hum?.let { h ->
            ws1[i].hum = h
        }
        ws1[i].whether = determineWeather(ws1[i].pres, ws1[i].hum)
        saveToFile(filecurw, ws1)
        i+=1
        if (i == 3){
            i = 0
        }

        if (ws1[0].temp != -277.0f && ws1[1].temp != -277.0f && ws1[2].temp != -277.0f){
            val ws2 = readFromFile(filefutuw)
            culcPres(ws1, ws2)
            culcTemp(ws1, ws2)
            culcHum(ws1, ws2)
            ws2[0].whether = determineWeather(ws2[0].pres, ws2[0].hum)
            ws2[1].whether = determineWeather(ws2[1].pres, ws2[1].hum)
            ws2[2].whether = determineWeather(ws2[2].pres, ws2[2].hum)
            saveToFile(filefutuw, ws2)
        }

        temp = null
        pres = null
        hum = null
        updateInfo()
    }

    private fun updateInfo(){
        val ws1 = readFromFile(filecurw)
        feelDate(true, cur1Text, ws1, 0)
        feelDate(true, cur2Text, ws1, 1)
        feelDate(true, cur3Text, ws1, 2)
        val ws2 = readFromFile(filefutuw)
        feelDate(false, future1Text, ws2, 0)
        feelDate(false, future2Text, ws2, 1)
        feelDate(false, future3Text, ws2, 2)
    }

    private fun clearInfo(){
        val nf = String.format("%.2f", -277.0f).toFloat()
        val wl = listOf(
            Whether(0, nf, nf, nf, "сонячно", "висока"),
            Whether(30, nf, nf, nf, "хмарно", "середня"),
            Whether(60, nf, nf, nf, "дощ", "низька")
        )
        saveToFile(filecurw, wl)
        saveToFile(filefutuw, wl)
    }

    private fun saveToFile(filename: String, wl: List<Whether>) {
        val gson = Gson()
        val jsonString = gson.toJson(wl)

        try {
            val file = File(this.filesDir, filename)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readFromFile(filename: String): List<Whether> {
        val file = File(this.filesDir, filename)

        return try {
            val fileInput = file.bufferedReader().use { it.readText() }
            val gson = Gson()
            gson.fromJson(fileInput, Array<Whether>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun feelDate(isCur: Boolean,text: TextView, ws: List<Whether>, n: Int){
        if (isCur) {
            text.text = "${ws[n].time} хв назад\n" +
                    "t: ${ws[n].temp}°C\n" +
                    "тиск: ${ws[n].pres}hPa\n" +
                    "вологість: ${ws[n].hum}%\n" +
                    "погода: ${ws[n].whether}"
        }
        else{
            text.text = "через 30 хв\n" +
                    "t: ${ws[n].temp}°C\n" +
                    "тиск: ${ws[n].pres}hPa\n" +
                    "вологість: ${ws[n].hum}%\n" +
                    "погода: ${ws[n].whether}\n" +
                    "імовір: ${ws[n].chance}\n"
        }
    }

    private fun culcPres(ws1: List<Whether>, ws2: List<Whether>){
        var a = 0.0f
        var b = 0.0f
        var c = 0.0f
        for (k in 0 until 3){
            if(ws1[k].time == 0){
                a = ws1[k].pres
            }
            if(ws1[k].time == 30){
                b = ws1[k].pres
            }
            if(ws1[k].time == 60){
                c = ws1[k].pres
            }
        }
        val x = c - b
        var y = b - a
        var z = x - y
        if (z >= 1.5f){
            y *= -1
            ws2[0].pres = String.format("%.2f", a + y).toFloat()
            ws2[1].pres = String.format("%.2f", a + y / 5).toFloat()
            ws2[2].pres = String.format("%.2f", a - y).toFloat()
            return
        }
        if(z >= 0){
            ws2[0].pres = String.format("%.2f", a + z).toFloat()
            ws2[1].pres = String.format("%.2f", a + z / 5).toFloat()
            ws2[2].pres = String.format("%.2f", a - z).toFloat()
            return
        }
        if(z > -1.5){
            ws2[0].pres = String.format("%.2f", a - z).toFloat()
            ws2[1].pres = String.format("%.2f", a - z / 5).toFloat()
            ws2[2].pres = String.format("%.2f", a + z).toFloat()
            return
        }
        if(z <= -1.5f){
            y *= -1
            ws2[0].pres = String.format("%.2f", a - y).toFloat()
            ws2[1].pres = String.format("%.2f", a - y / 5).toFloat()
            ws2[2].pres = String.format("%.2f", a + y).toFloat()
            return
        }
    }

    private fun culcTemp(ws1: List<Whether>, ws2: List<Whether>){
        var a = 0.0f
        var b = 0.0f
        var c = 0.0f
        for (k in 0 until 3){
            if(ws1[k].time == 0){
                a = ws1[k].temp
            }
            if(ws1[k].time == 30){
                b = ws1[k].temp
            }
            if(ws1[k].time == 60){
                c = ws1[k].temp
            }
        }
        val x = c - b
        var y = b - a
        var z = x - y
        if (z >= 1.5f){
            y *= -1
            ws2[0].temp = String.format("%.2f", a + y).toFloat()
            ws2[1].temp = String.format("%.2f", a + y / 5).toFloat()
            ws2[2].temp = String.format("%.2f", a - y).toFloat()
            return
        }
        if(z >= 0){
            ws2[0].temp = String.format("%.2f", a + z).toFloat()
            ws2[1].temp = String.format("%.2f", a + z / 5).toFloat()
            ws2[2].temp = String.format("%.2f", a - z).toFloat()
            return
        }
        if(z > -1.5){
            ws2[0].temp = String.format("%.2f", a - z).toFloat()
            ws2[1].temp = String.format("%.2f", a - z / 5).toFloat()
            ws2[2].temp = String.format("%.2f", a + z).toFloat()
            return
        }
        if(z <= -1.5f){
            y *= -1
            ws2[0].temp = String.format("%.2f", a - y).toFloat()
            ws2[1].temp = String.format("%.2f", a - y / 5).toFloat()
            ws2[2].temp = String.format("%.2f", a + y).toFloat()
            return
        }
    }

    private fun culcHum(ws1: List<Whether>, ws2: List<Whether>){
        var a = 0.0f
        var b = 0.0f
        var c = 0.0f
        for (k in 0 until 3){
            if(ws1[k].time == 0){
                a = ws1[k].hum
            }
            if(ws1[k].time == 30){
                b = ws1[k].hum
            }
            if(ws1[k].time == 60){
                c = ws1[k].hum
            }
        }
        val x = c - b
        var y = b - a
        var z = x - y
        if (z >= 1.5f){
            y *= -1
            ws2[0].hum = String.format("%.2f", a - y).toFloat()
            ws2[1].hum = String.format("%.2f", a - y / 5).toFloat()
            ws2[2].hum = String.format("%.2f", a + y).toFloat()
            return
        }
        if(z >= 0){
            ws2[0].hum = String.format("%.2f", a - z).toFloat()
            ws2[1].hum = String.format("%.2f", a - z / 5).toFloat()
            ws2[2].hum = String.format("%.2f", a + z).toFloat()
            return
        }
        if(z > -1.5){
            ws2[0].hum = String.format("%.2f", a + z).toFloat()
            ws2[1].hum = String.format("%.2f", a + z / 5).toFloat()
            ws2[2].hum = String.format("%.2f", a - z).toFloat()
            return
        }
        if(z <= -1.5f){
            y *= -1
            ws2[0].hum = String.format("%.2f", a + y).toFloat()
            ws2[1].hum = String.format("%.2f", a + y / 5).toFloat()
            ws2[2].hum = String.format("%.2f", a - y).toFloat()
            return
        }
    }

    private fun determineWeather(pres: Float, hum: Float): String {
        return when {
            pres > 1010 && hum < 95-> "Сонячно"
            pres in 980.0..1020.0 && hum in 30.0..75.0-> "Хмарно"
            pres < 990 && hum > 5-> "Дощ"
            else -> "Невиз"
        }
    }

    private data class Whether(
        var time: Int,
        var temp: Float,
        var pres: Float,
        var hum: Float,
        var whether: String,
        var chance: String,
    )
}