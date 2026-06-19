package ru.dors.androidusbcdc

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

// Change inheritance from BaseAdapter to RecyclerView.Adapter
class CdcPortsAdapter(private val context: Context, private val arrayList: java.util.ArrayList<CdcPortData>) :
    RecyclerView.Adapter<CdcPortsAdapter.PortViewHolder>() { // Pass ViewHolder type here

    // Update the ViewHolder class slightly for modern usage
    inner class PortViewHolder(view: ViewGroup) : RecyclerView.ViewHolder(view) {
        val idNumber: TextView = view.findViewById(R.id.idNumber)
        val writeEndpoint: TextView = view.findViewById(R.id.writeEndpoint)
        val readEndpoint: TextView = view.findViewById(R.id.readEndpoint)
    }

    // 1. The Adapter needs to tell the RecyclerView how many items it has. (No change in logic)
    override fun getItemCount(): Int = arrayList.size

    // Optional: If you need stable IDs for animations, implement this.
    // For simple lists, position mapping is fine.
    override fun getItemId(position: Int): Long = position.toLong()

    /**
     * 2. CREATE VIEW HOLDER (The equivalent of BaseAdapter's 'onCreateViewHolder')
     * Called when the RecyclerView needs a brand new view structure.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        // Inflate the row layout
        val view = LayoutInflater.from(context).inflate(R.layout.row, parent, false)
        return PortViewHolder(view as ViewGroup) // Return the ViewHolder wrapper
    }

    /**
     * 3. BIND VIEW HOLDER (The equivalent of BaseAdapter's 'getView')
     * Called every time a view needs to be updated/displayed at a specific position.
     */
    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        val portData = arrayList[position]

        // Use the ViewHolder references to set data
        holder.idNumber.text = portData.id.toString()
        holder.writeEndpoint.text = "Write Endpoint: ${portData.writeEndpoint}"
        holder.readEndpoint.text = "Read Endpoint: ${portData.readEndpoint}"

        // ******* CRITICAL: Setting up click handling *******
        // Instead of relying on a deprecated onItemClickListener, set a listener here
        // or in the Activity (see Step 3). For simplicity, we'll add it to the ViewHolder/Adapter later.
    }
}

/*
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

// В данном классе используется реализация шаблона ViewHolder, который обеспечивает
// эффективное отображение ListView
class CdcPortsAdapter(private val context: Context, private val arrayList: java.util.ArrayList<CdcPortData>) : BaseAdapter() {

    // Внутренний класс хранит ссылки на view, заменяя вызовы функции findViewById()
    private class ViewHolder(view: View) {
        val idNumber: TextView = view.findViewById(R.id.idNumber)
        val writeEndpoint: TextView = view.findViewById(R.id.writeEndpoint)
        val readEndpoint: TextView = view.findViewById(R.id.readEndpoint)
    }

    override fun getCount(): Int = arrayList.size
    override fun getItem(position: Int): Any = position
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        // При первом запуске (first time inflation) создаём view и привязываем к нему ViewHolder
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.row, parent, false)
            .also { it.tag = ViewHolder(it) }

        // Используем ViewHolder для связывания данных из контейнера с view
        val viewHolder = view.tag as ViewHolder
        val portData = arrayList[position]

        viewHolder.apply {
            idNumber.text = portData.id.toString()
            writeEndpoint.text = "Write Endpoint: ${portData.writeEndpoint}"
            readEndpoint.text = "Read Endpoint: ${portData.readEndpoint}"
        }

        return view
    }
}
*/