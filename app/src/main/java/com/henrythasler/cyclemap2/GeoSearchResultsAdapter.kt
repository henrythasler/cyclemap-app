package com.henrythasler.cyclemap2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.search.result.SearchSuggestion

class GeoSearchResultsAdapter(private val onClick: (SearchSuggestion) -> Unit) : RecyclerView.Adapter<GeoSearchResultsAdapter.ViewHolder>() {

    val dataSet: MutableList<SearchSuggestion> = mutableListOf()
    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    class ViewHolder(view: View, val onClick: (SearchSuggestion) -> Unit) : RecyclerView.ViewHolder(view) {
        val textView: TextView
        private var currentItem: SearchSuggestion? = null

        init {
            // Define click listener for the ViewHolder's View
            textView = view.findViewById(R.id.textView)
            view.setOnClickListener {
                currentItem?.let { onClick(it) }
            }
        }

        fun bind(item: SearchSuggestion) {
            currentItem = item
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.geosearch_row_item, viewGroup, false)

        return ViewHolder(view, onClick)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        val item = dataSet[position]
        viewHolder.textView.text = "${item.name}, ${item.address?.region}, ${item.address?.country}"
        viewHolder.bind(dataSet[position])
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size
}
