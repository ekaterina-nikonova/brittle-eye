package com.brittlepins.brittleeye

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.math.BigDecimal
import java.math.RoundingMode

class ComponentsAdapter(private val components: ArrayList<Pair<String, Float>>, val listener: (Int) -> Unit)
    : RecyclerView.Adapter<ComponentsAdapter.ComponentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComponentViewHolder {
        val inflater = LayoutInflater.from(parent?.context)
        return ComponentViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: ComponentViewHolder, position: Int) {
        val label = components[position].first
        val confidence = components[position].second
        holder.bind(confidence, label, position, listener)
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

        fun bind(confidence: Float, label: String, position: Int, listener: (Int) -> Unit) {
            val roundedConfidence = BigDecimal((confidence * 100).toDouble()).setScale(1, RoundingMode.HALF_EVEN)
            val confidenceText = "$roundedConfidence %"
            mConfidenceView?.text = confidenceText
            mLabelView?.text = label
            itemView.setOnClickListener { listener(position) }
        }
    }
}
