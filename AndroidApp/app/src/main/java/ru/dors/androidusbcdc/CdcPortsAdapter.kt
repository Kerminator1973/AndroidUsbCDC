package ru.dors.androidusbcdc

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class CdcPortsAdapter(private val context: Context, private val arrayList: java.util.ArrayList<CdcPortData>) : BaseAdapter() {

    private lateinit var idNumber: TextView
    private lateinit var writeEndpoint: TextView
    private lateinit var readEndpoint: TextView

    override fun getCount(): Int {
        return arrayList.size
    }

    override fun getItem(position: Int): Any {
        return position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {

        val convertView: View?
        convertView = LayoutInflater.from(context).inflate(R.layout.row, parent, false)
        idNumber = convertView.findViewById(R.id.idNumber)
        writeEndpoint = convertView.findViewById(R.id.writeEndpoint)
        readEndpoint = convertView.findViewById(R.id.readEndpoint)

        idNumber.text = arrayList[position].getId().toString()
        writeEndpoint.text = "Write Endpoint: " + arrayList[position].getWriteEndpoint()
        readEndpoint.text = "Read Endpoint: " + arrayList[position].getReadEndpoint()

        return convertView
    }
}