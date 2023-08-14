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

    override fun getView(position: Int, reusedConvertView: View?, parent: ViewGroup): View? {
        // ListView пытается повторно использовать convertView. Первый раз он передаётся в getView()
        // нулевым, конструируется и возвращается обратно в ListView. В случае необходимости
        // сформировать ещё одну строки списка, повторно используется (recycled) ранее созданный
        // convertView
        val convertView : View? = reusedConvertView ?: LayoutInflater.from(context).inflate(R.layout.row, parent, false)
        if (null != convertView) {
            idNumber = convertView.findViewById(R.id.idNumber)
            idNumber.text = arrayList[position].getId().toString()

            writeEndpoint = convertView.findViewById(R.id.writeEndpoint)
            writeEndpoint.text = "Write Endpoint: " + arrayList[position].getWriteEndpoint()

            readEndpoint = convertView.findViewById(R.id.readEndpoint)
            readEndpoint.text = "Read Endpoint: " + arrayList[position].getReadEndpoint()
        }

        return convertView
    }
}