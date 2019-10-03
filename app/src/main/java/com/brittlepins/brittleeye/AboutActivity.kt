package com.brittlepins.brittleeye

import android.graphics.drawable.Drawable
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_about.*
import kotlinx.android.synthetic.main.content_about.*

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setSupportActionBar(toolbar)
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val components = arrayListOf<Pair<Drawable?, String>>()
        components.add(Pair(getDrawable(R.drawable.analogue_input_output), getString(R.string.analogue_input_output)))
        components.add(Pair(getDrawable(R.drawable.audio_coder_decoder), getString(R.string.ausio_coder_decoder)))
        components.add(Pair(getDrawable(R.drawable.bluetooth_module), getString(R.string.bluetooth_module)))
        components.add(Pair(getDrawable(R.drawable.dataflash_storage), getString(R.string.dataflash_storage)))
        components.add(Pair(getDrawable(R.drawable.flame_sensor), getString(R.string.flame_sensor)))
        components.add(Pair(getDrawable(R.drawable.heartbeat_sensor), getString(R.string.heartbeat_sensor)))
        components.add(Pair(getDrawable(R.drawable.reed_switch), getString(R.string.reed_switch)))

        val manager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        componentList.apply {
            setHasFixedSize(true)
            adapter = AboutComponentsAdapter(components)
            layoutManager = manager
        }
    }
}
