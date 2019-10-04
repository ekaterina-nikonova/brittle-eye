package com.brittlepins.brittleeye

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AboutComponentsAdapter(private val components: ArrayList<Pair<Drawable?, String>>)
    : RecyclerView.Adapter<AboutComponentsAdapter.ComponentViewHolder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComponentViewHolder {
        val inflater = LayoutInflater.from(parent?.context)
        return ComponentViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: ComponentViewHolder, position: Int) {
        val image = components[position].first
        val label = components[position].second
        holder.bind(image, label)
    }

    override fun getItemCount(): Int = components.size

    class ComponentViewHolder(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.about_component_layout, parent, false)) {

        private var mImageView: ImageView? = null
        private var mLabelView: TextView? = null

        init {
            mImageView = itemView.findViewById(R.id.aboutComponentImage)
            mLabelView = itemView.findViewById(R.id.aboutComponentLabel)
        }

        fun bind(image: Drawable?, label: String) {
            mImageView?.setImageDrawable(image)
            mLabelView?.text = label
        }
    }
}