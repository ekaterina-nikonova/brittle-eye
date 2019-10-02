package com.brittlepins.recognitionlibrary

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.recreate
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
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.ByteArrayOutputStream
import java.util.*

class CameraActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_REQUEST_CODE = 10
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

    private val TAG = "AddComponentActivity"
    private val activity: Activity = this

    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var preview: Preview
    private lateinit var viewFinder: TextureView

    private var bufferDimens: Size = Size(0, 0)
    private var viewFinderDimens: Size = Size(0, 0)
    private var viewFinderRotation: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        graphicOverlay = findViewById(R.id.graphicOverlay)
        viewFinder = findViewById(R.id.view_finder)

        graphicOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        retryFAB.setOnClickListener {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            recreate(activity)
        }
        retryFAB.hide()

        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        configureModel()
        viewFinder.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            val viewFinder = view as TextureView
            val newViewFinderDimens = Size(right - left, bottom - top)
            Log.d(TAG, "View finder layout changed. Size: $newViewFinderDimens")
            val rotation = getDisplaySurfaceRotation(viewFinder.display)
            updateTransform(viewFinder, rotation, bufferDimens, newViewFinderDimens)
        }
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

            val rotation = getDisplaySurfaceRotation(viewFinder.display)
            updateTransform(viewFinder, rotation, it.textureSize, viewFinderDimens)
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
                resources,
                previewImg
            )
        }

        CameraX.bindToLifecycle(this, analyzerUseCase, preview)
    }

    private fun updateTransform(textureView: TextureView?, rotation: Int?, newBufferDimens: Size,
                                newViewFinderDimens: Size) {
        // This should not happen anyway, but now the linter knows
        val textureView = textureView ?: return

        if (rotation == viewFinderRotation &&
            Objects.equals(newBufferDimens, bufferDimens) &&
            Objects.equals(newViewFinderDimens, viewFinderDimens)) {
            // Nothing has changed, no need to transform output again
            return
        }

        if (rotation == null) {
            // Invalid rotation - wait for valid inputs before setting matrix
            return
        } else {
            // Update internal field with new inputs
            viewFinderRotation = rotation
        }

        if (newBufferDimens.width == 0 || newBufferDimens.height == 0) {
            // Invalid buffer dimens - wait for valid inputs before setting matrix
            return
        } else {
            // Update internal field with new inputs
            bufferDimens = newBufferDimens
        }

        if (newViewFinderDimens.width == 0 || newViewFinderDimens.height == 0) {
            // Invalid view finder dimens - wait for valid inputs before setting matrix
            return
        } else {
            // Update internal field with new inputs
            viewFinderDimens = newViewFinderDimens
        }

        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinderDimens.width / 2f
        val centerY = viewFinderDimens.height / 2f

        // Correct preview output to account for display rotation
        matrix.postRotate(-viewFinderRotation!!.toFloat(), centerX, centerY)

        // Buffers are rotated relative to the device's 'natural' orientation: swap width and height
        val bufferRatio = bufferDimens.height / bufferDimens.width.toFloat()

        val scaledWidth: Int
        val scaledHeight: Int
        // Match longest sides together -- i.e. apply center-crop transformation
        if (viewFinderDimens.width > viewFinderDimens.height) {
            scaledHeight = viewFinderDimens.width
            scaledWidth = Math.round(viewFinderDimens.width * bufferRatio)
        } else {
            scaledHeight = viewFinderDimens.height
            scaledWidth = Math.round(viewFinderDimens.height * bufferRatio)
        }

        // Compute the relative scale value
        val xScale = scaledWidth / viewFinderDimens.width.toFloat()
        val yScale = scaledHeight / viewFinderDimens.height.toFloat()

        // Scale input buffers to fill the view finder
        matrix.preScale(xScale, yScale, centerX, centerY)

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix)
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
        private val resources: Resources,
        private val previewImg: ImageView
    ) : ImageAnalysis.Analyzer {
        private val TAG = this::class.java.simpleName
        val ACTION_COMPONENT = "com.brittlepins.recognitionlibrary.ACTION_COMPONENT"
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
                            CameraX.unbindAll()
                            done = true
                            activity.retryFAB.show()

                            val frame = extendedFrame(boundingBox, image.bitmap.width, image.bitmap.height)
                            val croppedImage = Bitmap.createBitmap(
                                image.bitmap,
                                frame.getValue("left"),
                                frame.getValue("top"),
                                frame.getValue("width"),
                                frame.getValue("height")
                            )
                            previewImg.setImageBitmap(croppedImage)

                            val allLabels = hashMapOf<String, Float>().apply {
                                labels.forEach {
                                    this[it.text] = it.confidence
                                }
                            }

                            showNewComponentPrompt(labels[0].text, croppedImage, allLabels)
                        }
                    }
                    .addOnFailureListener {
                        e -> Log.e(TAG, "Could not label image: ${e.message}")
                        labelImage(image, boundingBox)
                    }
            }).start()

        }

        private fun extendedFrame(box: Rect, width: Int, height: Int) : Map<String, Int> {
            return mapOf(
                "left" to if (box.left - 16 > 0) box.left - 16 else box.left,
                "top" to if (box.top - 16 > 0) box.top - 16 else box.top,
                "width" to if (box.width() + 32 <= box.left + width) box.width() + 32 else box.width() ,
                "height" to if (box.height() + 32 <= box.top + height) box.height() + 32 else box.height()
            )
        }

        private fun showNewComponentPrompt(label: String, img: Bitmap, labels: HashMap<String, Float>) {
            done = true

            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            val snackbar = Snackbar.make(viewFinder, label, Snackbar.LENGTH_INDEFINITE)
            snackbar.setAction(ctx.getString(R.string.snackbar_save)) {
                val stream = ByteArrayOutputStream()
                img.compress(Bitmap.CompressFormat.PNG, 75, stream)
                val imgBytes = stream.toByteArray()

                val intent = Intent().apply {
                    action = ACTION_COMPONENT
                    putExtra("component_name", label)
                    putExtra("component_img", imgBytes)
                    putExtra("labels", labels)
                }

                if (intent.resolveActivity(ctx.packageManager) != null) {
                    activity.startActivity(intent)
                }
            }
            snackbar.show()
        }

        private fun showObjectBox(obj: FirebaseVisionObject, img: FirebaseVisionImage) {
            overlay.clear()
            overlay.add(ObjectGraphicInProminentMode(overlay, obj, ObjectConfirmationController(overlay), viewFinder, img))
        }
    }

    companion object {
        /** Helper function that gets the rotation of a [Display] in degrees */
        fun getDisplaySurfaceRotation(display: Display?) = when(display?.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> null
        }
    }
}
