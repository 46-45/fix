package com.example.facerecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var captureButton: Button
    private lateinit var imageView: ImageView
    private lateinit var tvPrediction: TextView

    companion object {
        private const val CAMERA_REQUEST_CODE = 100
        private const val IMAGE_SIZE = 100
        private const val PICK_IMAGE_REQUEST_CODE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        captureButton = findViewById(R.id.btnCapture)
        imageView = findViewById(R.id.imageView)
        tvPrediction = findViewById(R.id.tvPrediction)

        captureButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_REQUEST_CODE
                )
            }
        }

        val uploadButton: Button = findViewById(R.id.btnUpload)
        uploadButton.setOnClickListener {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST_CODE)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    processImage(imageBitmap)
                }
            }
            PICK_IMAGE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val selectedImageUri: Uri? = data?.data
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                    processImage(bitmap)
                }
            }
        }
    }


    private fun processImage(bitmap: Bitmap) {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, false)
        val grayscaleBitmap = convertToGrayscale(resizedBitmap)

        imageView.setImageBitmap(grayscaleBitmap)
        tvPrediction.visibility = View.GONE

        sendImageToApi(grayscaleBitmap)
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (row in 0 until height) {
            for (col in 0 until width) {
                val pixel = bitmap.getPixel(col, row)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val gray = (red + green + blue) / 3
                val grayscalePixel = Color.rgb(gray, gray, gray)
                grayscaleBitmap.setPixel(col, row, grayscalePixel)
            }
        }

        return grayscaleBitmap
    }

    private fun sendImageToApi(bitmap: Bitmap) {
        val file = createTempFile()
        file.deleteOnExit()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())

        val requestFile = RequestBody.create("image/png".toMediaTypeOrNull(), file)
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

        val apiService = ApiClient.retrofit.create(ApiService::class.java)
        val call = apiService.predictImage(body)

        call.enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(
                call: Call<PredictionResponse>,
                response: Response<PredictionResponse>
            ) {
                if (response.isSuccessful) {
                    val predictedName = response.body()?.predicted_name
                    Log.d("Prediction", "Predicted name: $predictedName")
                    tvPrediction.text = "Predicted name: $predictedName"
                    tvPrediction.visibility = View.VISIBLE
                } else {
                    Log.e("Prediction", "Prediction API request failed")
                }
            }

            override fun onFailure(call: Call<PredictionResponse>, t: Throwable) {
                Log.e("Prediction", "Prediction API request failed", t)
            }
        })
    }
}