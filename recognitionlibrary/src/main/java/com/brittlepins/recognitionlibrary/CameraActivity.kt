package com.brittlepins.recognitionlibrary

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.common.modeldownload.FirebaseRemoteModel
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions

class CameraActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_REQUEST_CODE = 10
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

    private val TAG = "AddComponentActivity"
    private val activity: Activity = this

    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var preview: Preview
    private lateinit var viewFinder: TextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        graphicOverlay = findViewById(R.id.graphicOverlay)
        viewFinder = findViewById(R.id.view_finder)

        graphicOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        configureModel()
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateTransform() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun configureModel() {
        val conditions = FirebaseModelDownloadConditions.Builder().requireWifi().build()
        val remoteModel = FirebaseRemoteModel.Builder("components")
            .enableModelUpdates(true)
            .setInitialDownloadConditions(conditions)
            .setUpdatesDownloadConditions(conditions)
            .build()
        FirebaseModelManager.getInstance().registerRemoteModel(remoteModel)
    }

    private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().apply {
            //
        }.build()
        preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = viewFinder.parent as ViewGroup

            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            val analyzerThread = HandlerThread("ObjectDetection").apply { start() }
            setCallbackHandler(Handler(analyzerThread.looper))
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()
        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            analyzer = ImageAnalyzer(
                activity,
                applicationContext,
                graphicOverlay,
                preview,
                viewFinder,
                resources
            )
        }

        CameraX.bindToLifecycle(this, analyzerUseCase, preview)
    }

    private fun updateTransform() {
        val matrix = Matrix()

        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        viewFinder.setTransform(matrix)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private class ImageAnalyzer(
        private val activity: Activity,
        private val ctx: Context,
        private val overlay: GraphicOverlay,
        private val preview: Preview,
        private val viewFinder: TextureView,
        private val resources: Resources
    ) : ImageAnalysis.Analyzer {
        private val TAG = "ImageAnalyzer"
        private var done = false

        val objectDetectionOptions: FirebaseVisionObjectDetectorOptions = FirebaseVisionObjectDetectorOptions.Builder()
            .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
            .build()
        val objectDetector: FirebaseVisionObjectDetector = FirebaseVision.getInstance().getOnDeviceObjectDetector(objectDetectionOptions)

        override fun analyze(imageProxy: ImageProxy, rotationDegrees: Int) {
            val COMPENSATED_ROTATION = SparseIntArray().apply {
                append(Surface.ROTATION_0, FirebaseVisionImageMetadata.ROTATION_90)
                append(Surface.ROTATION_90, FirebaseVisionImageMetadata.ROTATION_0)
                append(Surface.ROTATION_180, FirebaseVisionImageMetadata.ROTATION_270)
                append(Surface.ROTATION_270, FirebaseVisionImageMetadata.ROTATION_180)
            }
            if (!done) {
                val image = imageProxy.image ?: return
                val visionImage = FirebaseVisionImage.fromMediaImage(image, COMPENSATED_ROTATION[activity.windowManager.defaultDisplay.rotation])

                Thread(Runnable {
                    objectDetector.processImage(visionImage)
                        .addOnSuccessListener { detectedObjects ->
                            if (detectedObjects.size > 0) {
                                showObjectBox(detectedObjects[0], visionImage)
                                for (obj in detectedObjects) {
                                    labelImage(visionImage, obj.boundingBox)
                                    Log.d(TAG, "${obj.trackingId} - ${obj.boundingBox.flattenToString()}")
                                }
                                imageProxy.close()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Could not detect object: ${e.message}")
                            analyze(imageProxy, rotationDegrees)
                        }
                }).start()

            }
        }

        private fun labelImage(image: FirebaseVisionImage, boundingBox: Rect) {
            val labelerOptions = FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder()
                .setRemoteModelName("components")
                .setConfidenceThreshold(0f)
                .build()
            val labeler = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(labelerOptions)

            Thread(Runnable {
                labeler.processImage(FirebaseVisionImage.fromBitmap(image.bitmap))
                    .addOnSuccessListener { labels ->
                        if (labels.size > 0 && labels[0].confidence >= 0.7f) {
                            val timer = object: CountDownTimer(1500, 1) {
                                override fun onTick(millisUntilFinished: Long) {}
                                override fun onFinish() {
                                    CameraX.unbind(preview)
                                    showNewComponentPrompt(labels[0].text)
                                }
                            }
                            done = true
                            timer.start()
                        } else {
                            done = false
                        }
                    }
                    .addOnFailureListener {
                            e -> Log.e(TAG, "Could not label image: ${e.message}")
                        done = false
                        labelImage(image, boundingBox)
                    }
            }).start()

        }

        private fun showNewComponentPrompt(label: String) {
            done = true
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            Snackbar.make(viewFinder, label, Snackbar.LENGTH_INDEFINITE).show()
        }

        private fun showObjectBox(obj: FirebaseVisionObject, img: FirebaseVisionImage) {
            overlay.clear()
            overlay.add(ObjectGraphicInProminentMode(overlay, obj, ObjectConfirmationController(overlay), viewFinder, img))
        }
    }
}
