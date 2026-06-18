package ru.dors.androidusbcdc

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
        val view: View
        var viewHolder: ViewHolder?

        if (convertView == null) {
            // При первом запуске (first time inflation) создаём View и ViewHolder
            view = LayoutInflater.from(context).inflate(R.layout.row, parent, false)
            viewHolder = ViewHolder(view)
            // Для быстрого последующего доступа сохраняем ViewHolder в tag-е
            (view as ViewGroup).tag = viewHolder
        } else {
            // Повторно использует уже существующий View
            view = convertView
            viewHolder = (view as ViewGroup).tag as ViewHolder
        }

        // Используем ссылку на ViewHolder для связывания данных при выводе
        val portData = arrayList[position]
        viewHolder.apply {
            idNumber.text = portData.id.toString()
            writeEndpoint.text = "Write Endpoint: ${portData.writeEndpoint}"
            readEndpoint.text = "Read Endpoint: ${portData.readEndpoint}"
        }

        return view
    }

/*  TODO: попробовать оптимизированный код
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.row, parent, false)
            .also { it.tag = ViewHolder(it) }

        val viewHolder = view.tag as ViewHolder
        val portData = arrayList[position]

        viewHolder.apply {
            idNumber.text = portData.id.toString()
            writeEndpoint.text = "Write Endpoint: ${portData.writeEndpoint}"
            readEndpoint.text = "Read Endpoint: ${portData.readEndpoint}"
        }

        return view
    }
*/ 
}
