package com.brittlepins.brittleeye

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ComponentsAdapter(private val components: ArrayList<Pair<String, Float>>)
    : RecyclerView.Adapter<ComponentsAdapter.ComponentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComponentViewHolder {
        val inflater = LayoutInflater.from(parent?.context)
        return ComponentViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: ComponentViewHolder, position: Int) {
        val label = components[position].first
        val confidence = components[position].second
        holder.bind(confidence, label)
    }

    override fun getItemCount(): Int = components.size

    class ComponentViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.component_layout, parent, false)) {
        private var mConfidenceView: TextView? = null
        private var mLabelView: TextView? = null


        init {
            mConfidenceView = itemView.findViewById(R.id.confidenceTextView)
            mLabelView = itemView.findViewById(R.id.labelTextView)
        }

        fun bind(confidence: Float, label: String) {
            mConfidenceView?.text = confidence.toString()
            mLabelView?.text = label
        }

    }
}
