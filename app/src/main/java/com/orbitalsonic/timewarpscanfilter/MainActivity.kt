package com.orbitalsonic.timewarpscanfilter

import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Size
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.orbitalsonic.timewarpscanfilter.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ExecutionException

class MainActivity : AppCompatActivity(), CameraXConfig.Provider {

    private lateinit var binding: ActivityMainBinding

    private val REQUEST_CODE_PERMISSIONS = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        "android.permission.CAMERA",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE"
    )

    var cameraProvider: ProcessCameraProvider? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    var cameraSelector: CameraSelector? = null
    var imageAnalysis: ImageAnalysis? = null
    var mCamera: Camera? = null
    var preview: Preview? = null


    var captureMode = CAPTURE_MODE.PHOTO
    var warpDirection = WARP_DIRECTION.VERTICAL
    var capture = false
    var isSwitching = false

    var mFacing = 0
    var frameRate = 2
    var lineCount = 0
    var lineResolution = 50
    var resolutionX = 480
    var resolutionY = 640

    var resultBitmap: Bitmap? = null
    var resultBitmapList: List<Bitmap>? = null
    var subBitmap: Bitmap? = null

    enum class CAPTURE_MODE {
        PHOTO, GIF
    }

    enum class WARP_DIRECTION {
        VERTICAL, HORIZONTAL
    }

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onClickMethod()
        initCamera()
        resultBitmapList = ArrayList<Bitmap>()

        resolutionY = 640
        resolutionX = 480
    }

    private fun onClickMethod(){
        binding.btnHorizontal.setOnClickListener {
            startCapture( WARP_DIRECTION.HORIZONTAL)
        }
        binding.btnVertical.setOnClickListener {
            startCapture( WARP_DIRECTION.VERTICAL)
        }

        binding.btnSave.setOnClickListener {

        }
        binding.btnCancel.setOnClickListener { view ->
            resultCancel()
        }
        binding.btnSwitchCamera.setOnClickListener { view ->
            switchCameras()
        }
    }

    private fun initCamera(){
        binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val build =
            ImageAnalysis.Builder().setTargetResolution(Size(480, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        imageAnalysis = build
        build.setAnalyzer(ContextCompat.getMainExecutor(this), ImageCapture())
        cameraProviderFuture!!.addListener(
            { bindCamera() },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCamera() {
        try {
            cameraProvider = cameraProviderFuture!!.get()
            if (allPermissionsGranted()) {
                bindPreview(cameraProvider)
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        } catch (unused: InterruptedException) {
        } catch (unused: ExecutionException) {
        }
    }

    private fun showBeforeCaptureUI() {
        binding.beforeCaptureUI.visibility = View.VISIBLE
    }

    private fun hideBeforeCaptureUI() {
        binding.beforeCaptureUI.visibility = View.GONE
    }

    private fun showCaptureUI() {
    }

    private fun hideCaptureUI() {
    }

    private fun showResultUI() {
        binding.resultUI.visibility = View.VISIBLE
    }

    private fun hideResultUI() {
        binding.resultUI.visibility = View.GONE
    }

    private fun resumeToBeforeCaptureUI() {
        lineCount = 0
        resultBitmap = null
        resultBitmapList = null
        initializeImageView()
        hideResultUI()
        hideCaptureUI()
        showBeforeCaptureUI()
        capture = false
    }

    private fun resultCancel() {
        resumeToBeforeCaptureUI()
    }

    private fun switchCameras() {
        isSwitching = true
        if (mFacing == 0) {
            setFacing(1)
        } else {
            setFacing(0)
        }
        bindPreview(cameraProvider)
        isSwitching = false
    }

    private fun setFacing(i: Int) {
        mFacing = i
    }

    fun overlay(bitmap: Bitmap?, bitmap2: Bitmap?, i: Int, warp_direction: WARP_DIRECTION): Bitmap {
        Matrix().preScale(1.0f, -1.0f)
        val createBitmap = Bitmap.createBitmap(bitmap!!.width, bitmap.height, bitmap.config)
        val canvas = Canvas(createBitmap)
        val paint: Paint? = null
        canvas.drawBitmap(bitmap, Matrix(), paint)
        if (warp_direction == WARP_DIRECTION.VERTICAL) {
            canvas.drawBitmap(bitmap2!!, 0.0f, i.toFloat(), paint)
        }
        if (warp_direction == WARP_DIRECTION.HORIZONTAL) {
            canvas.drawBitmap(bitmap2!!, i.toFloat(), 0.0f, paint)
        }
        return createBitmap
    }

    fun overlay(bitmap: Bitmap, bitmap2: Bitmap?): Bitmap {
        Matrix().preScale(1.0f, -1.0f)
        val createBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(createBitmap)
        Paint().color = -1
        val paint: Paint? = null
        canvas.drawBitmap(bitmap, Matrix(), paint)
        canvas.drawBitmap(bitmap2!!, 0.0f, 0.0f, paint)
        return createBitmap
    }

    fun rotateBitmap(bitmap: Bitmap, i: Int): Bitmap {
        val matrix = Matrix()
        matrix.setRotate(i.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    fun drawScanEffect(list: List<Bitmap>, warp_direction: WARP_DIRECTION) {
        for (i in list.indices) {
            val canvas = Canvas(list[i])
            val paint = Paint()
            paint.strokeWidth = 10.0f
            paint.color = ContextCompat.getColor(this, R.color.teal_200)
            if (warp_direction == WARP_DIRECTION.VERTICAL) {
                canvas.drawLine(
                    0.0f,
                    (lineResolution * i).toFloat(),
                    list[0].width.toFloat(),
                    (lineResolution * i).toFloat(),
                    paint
                )
            } else if (warp_direction == WARP_DIRECTION.HORIZONTAL) {
                val f = (lineResolution * i).toFloat()
                canvas.drawLine(f, 0.0f, f, list[0].height.toFloat(), paint)
            }
            canvas.drawBitmap(list[i], 0.0f, 0.0f, null as Paint?)
        }
    }

    fun drawScanEffect(bitmap: Bitmap, warp_direction: WARP_DIRECTION, i: Int) {
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.strokeWidth = 10.0f
        paint.color = ContextCompat.getColor(this, R.color.teal_200)
        if (warp_direction == WARP_DIRECTION.VERTICAL) {
            val f = (i + 5).toFloat()
            canvas.drawLine(0.0f, f, bitmap.width.toFloat(), f, paint)
        } else if (warp_direction == WARP_DIRECTION.HORIZONTAL) {
            val f2 = (i + 5).toFloat()
            canvas.drawLine(f2, 0.0f, f2, bitmap.height.toFloat(), paint)
        }
        canvas.drawBitmap(bitmap, 0.0f, 0.0f, null as Paint?)
    }

    fun drawWaterMark(bitmap: Bitmap?, i: Int) {
        val canvas = Canvas(bitmap!!)
        if (i == 1) {
            canvas.scale(-1.0f, 1.0f, (canvas.width / 2).toFloat(), (canvas.height / 2).toFloat())
        }
        val paint = Paint()
        paint.color = -1
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        paint.textSize = 15.0f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("made with", paint.textSize, paint.textSize * 2.0f, paint)
        canvas.drawText("TIME WARP SCAN", paint.textSize, paint.textSize * 3.0f + 0.0f, paint)
        canvas.drawBitmap(bitmap, 0.0f, 0.0f, null as Paint?)
    }

    fun initializeImageView() {
        val createBitmap = Bitmap.createBitmap(resolutionX, resolutionY, Bitmap.Config.ARGB_8888)
        resultBitmap = createBitmap
        createBitmap.eraseColor(0)
        binding.resultImageView.setImageBitmap(resultBitmap)
    }

    private fun saveBitmapInGalary(bitmap: Bitmap?): Uri {
        val file =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
        val file2 = File("$file/time_warp")
        file2.mkdirs()
        val nextInt = Random().nextInt(10000)
        val file3 = File(file2, "Image-$nextInt.jpg")
        if (file3.exists()) {
            file3.delete()
        }
        try {
            val fileOutputStream = FileOutputStream(file3)
            bitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Uri.fromFile(file3)
    }

    private fun startCapture(warp_direction: WARP_DIRECTION) {
        warpDirection = warp_direction
        lineResolution = 2
        lineCount = 0
        resultBitmap = null
        resultBitmapList = null
        initializeImageView()
        hideResultUI()
        hideBeforeCaptureUI()
        showCaptureUI()
        capture = true
    }

    fun toBitmap(image: Image?): Bitmap {
        val planes = image!!.planes
        val buffer = planes[0].buffer
        val buffer2 = planes[1].buffer
        val buffer3 = planes[2].buffer
        val remaining = buffer.remaining()
        val remaining2 = buffer2.remaining()
        val remaining3 = buffer3.remaining()
        val bArr = ByteArray(remaining + remaining2 + remaining3)
        buffer[bArr, 0, remaining]
        buffer3[bArr, remaining, remaining3]
        buffer2[bArr, remaining + remaining3, remaining2]
        val yuvImage = YuvImage(bArr, 17, image.width, image.height, null)
        val byteArrayOutputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, yuvImage.width, yuvImage.height),
            75,
            byteArrayOutputStream
        )
        val byteArray = byteArrayOutputStream.toByteArray()
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    fun mirrorBitmap(bitmap: Bitmap?, i: Int, i2: Int): Bitmap {
        val matrix = Matrix()
        matrix.preScale(i.toFloat(), i2.toFloat())
        return Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bindPreview(processCameraProvider: ProcessCameraProvider?) {
        processCameraProvider!!.unbindAll()
        preview = Preview.Builder().setTargetResolution(Size(480, 640)).build()
        cameraSelector = CameraSelector.Builder().requireLensFacing(mFacing).build()
        val build =
            ImageAnalysis.Builder().setTargetResolution(Size(480, 640)).setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            )
                .build()
        imageAnalysis = build
        build.setAnalyzer(ContextCompat.getMainExecutor(this), ImageCapture())
        preview!!.setSurfaceProvider(binding.previewView.surfaceProvider)
        mCamera = processCameraProvider.bindToLifecycle(this, cameraSelector!!, preview)
        processCameraProvider.bindToLifecycle(this, cameraSelector!!, imageAnalysis, preview)
    }

    private fun allPermissionsGranted(): Boolean {
        for (str in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, str) != 0) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(i: Int, strArr: Array<String>, iArr: IntArray) {
        super.onRequestPermissionsResult(i, strArr, iArr)
        if (i != REQUEST_CODE_PERMISSIONS) {
            return
        }
        if (allPermissionsGranted()) {
            bindPreview(cameraProvider)
            return
        }
        Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    inner class ImageCapture() : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            var bitmap: Bitmap?
            if (binding.previewView.previewStreamState.value == PreviewView.StreamState.STREAMING && binding.previewView.getChildAt(
                    0
                ).javaClass == TextureView::class.java
            ) {
                bitmap = (binding.previewView.getChildAt(0) as TextureView).getBitmap(
                    resolutionX,
                    resolutionY
                )
            } else if (imageProxy.format == 35) {
                bitmap = rotateBitmap(toBitmap(imageProxy.image), 90)
                if (mFacing == 0) {
                    bitmap = mirrorBitmap(bitmap, 1, -1)
                }
            } else {
                bitmap = null
            }
            if (bitmap == null) {
                imageProxy.close()
                return
            }
            if ((lineCount >= resolutionY || warpDirection != WARP_DIRECTION.VERTICAL) && (lineCount >= resolutionX || warpDirection != WARP_DIRECTION.HORIZONTAL || mFacing != 0) && (lineCount >= resolutionX || warpDirection != WARP_DIRECTION.HORIZONTAL || mFacing != 1)) {
                if (capture) {
                    stopCapture()
                }
            } else if (capture) {
                val currentTimeMillis = System.currentTimeMillis()
                if (resultBitmap == null) {
                    initializeImageView()
                }
                if (resultBitmapList == null) {
                    resultBitmapList = ArrayList<Bitmap>()
                }
                if (warpDirection == WARP_DIRECTION.VERTICAL) {
                    subBitmap = Bitmap.createBitmap(
                        bitmap, 0, lineCount,
                        resolutionX, lineResolution
                    )
                } else if (warpDirection == WARP_DIRECTION.HORIZONTAL) {
                    subBitmap = Bitmap.createBitmap(
                        bitmap, lineCount, 0,
                        lineResolution, resolutionY
                    )
                }
                resultBitmap = overlay(
                    resultBitmap,
                    subBitmap, lineCount, warpDirection
                )
                binding.resultImageView.setImageBitmap(resultBitmap)

                drawScanEffect(bitmap, warpDirection, lineCount)
                lineCount += lineResolution
                val currentTimeMillis2 = currentTimeMillis - System.currentTimeMillis()
                if (currentTimeMillis2 < frameRate) {
                    try {
                        Thread.sleep(frameRate - currentTimeMillis2)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            binding.previewViewImageView.setImageBitmap(bitmap)
            imageProxy.close()
        }
    }

    fun stopCapture() {
        if (lineCount == resolutionY && warpDirection == WARP_DIRECTION.VERTICAL || lineCount == resolutionX && warpDirection == WARP_DIRECTION.HORIZONTAL) {
            showResultUI()
            hideCaptureUI()
        } else {
            hideCaptureUI()
        }
        capture = false
    }

    override fun onBackPressed() {
        if (binding.beforeCaptureUI.visibility == 0) {
            finish()
        } else {
            resumeToBeforeCaptureUI()
        }
    }

}