package com.brittlepins.brittleeye

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.brittlepins.recognitionlibrary.CameraActivity

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import viewModel.MainViewModel

class MainActivity : AppCompatActivity() {

    val ACTION_COMPONENT = "com.brittlepins.recognitionlibrary.ACTION_COMPONENT"

    private lateinit var viewModel : MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        viewModel = ViewModelProviders.of(this)
            .get(MainViewModel::class.java)
        viewModel.prompt.observe(this, Observer {
            prompt.text = it
        })

        fab.setOnClickListener {
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

            val set = ConstraintSet()
            val layout: ConstraintLayout

            layout = mainContentContainer as ConstraintLayout
            set.clone(layout)
            // The following breaks the connection.
            set.clear(R.id.prompt, ConstraintSet.BOTTOM)
            set.setMargin(R.id.prompt, ConstraintSet.TOP, 24)
            // Comment out line above and uncomment line below to make the connection.
            // set.connect(R.id.bottomText, ConstraintSet.TOP, R.id.imageView, ConstraintSet.BOTTOM, 0);
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
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
