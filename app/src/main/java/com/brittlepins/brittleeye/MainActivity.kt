package com.brittlepins.brittleeye

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.drawToBitmap
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brittlepins.recognitionlibrary.CameraActivity
import com.google.android.material.snackbar.Snackbar

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import viewModel.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    val ACTION_COMPONENT = "com.brittlepins.recognitionlibrary.ACTION_COMPONENT"

    private lateinit var viewModel: MainViewModel
    private var clipboard: ClipboardManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

        viewModel = ViewModelProviders.of(this)
            .get(MainViewModel::class.java)
        viewModel.prompt.observe(this, Observer {
            prompt.text = it
        })

        emailFAB.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.action.equals(ACTION_COMPONENT)) {
            val imgBytes = intent.getByteArrayExtra("component_img")
            val img = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)

            componentImageView.setImageBitmap(img)
            prompt.text = intent.getStringExtra("component_name")

            val labels = intent.getSerializableExtra("labels") as HashMap<String, Float>
            val labelArray = arrayListOf<Pair<String, Float>>()
            labels.keys.forEach {
                labelArray.add(Pair(it, labels[it]!!))
            }

            val manager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
            labelsRecyclerView.apply {
                setHasFixedSize(true)
                adapter = ComponentsAdapter(labelArray) {
                    val label = labelArray[it].first
                    Log.d(this.javaClass.simpleName, label)
                    val clipData = ClipData.newPlainText("text", label)
                    clipboard?.setPrimaryClip(clipData)

                    val caption = "Label $label is copied to clipboard"
                    Snackbar.make(mainContentScrollView, caption, Snackbar.LENGTH_LONG)
                        .setAction("OK") {}
                        .show()
                }
                layoutManager = manager
            }

            val set = ConstraintSet()
            val layout: ConstraintLayout

            layout = mainContentContainer as ConstraintLayout
            set.clone(layout)
            // The following breaks the connection.
            set.clear(R.id.prompt, ConstraintSet.BOTTOM)
            // Comment out line above and uncomment line below to make the connection.
            // set.connect(R.id.bottomText, ConstraintSet.TOP, R.id.imageView, ConstraintSet.BOTTOM, 0);
            set.setMargin(R.id.prompt, ConstraintSet.TOP, 24)
            set.applyTo(layout)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_view -> performImageAction("view")
            R.id.action_share -> performImageAction("share")
            R.id.action_about -> startAboutActivity()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performImageAction(imageAction: String): Boolean {
        try {
            val bitmap = componentImageView.drawToBitmap()
            val file = File(externalCacheDir, "temp.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()

            val intent = Intent().apply {
                when (imageAction) {
                    "view" -> action = Intent.ACTION_VIEW
                    "share" -> {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                    }
                }
                setDataAndType(Uri.fromFile(file), "image/*")
            }

            var title = ""
            when (imageAction) {
                "view" -> title = getString(R.string.view_title)
                "share" -> title = getString(R.string.share_title)
            }
            val chooser: Intent = Intent.createChooser(intent, title)

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(chooser)
            } else {
                Snackbar.make(
                    mainContentContainer,
                    getString(R.string.no_apps_found),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Snackbar.make(
                mainContentContainer,
                getString(R.string.share_image_fail),
                Snackbar.LENGTH_SHORT
            ).show()
        }
        return true
    }

    private fun startAboutActivity() : Boolean {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
        return true
    }
}
