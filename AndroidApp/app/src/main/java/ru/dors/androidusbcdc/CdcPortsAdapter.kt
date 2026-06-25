package ru.dors.androidusbcdc

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

class CdcPortsAdapter(
    private val context: Context,
    // Мы могли бы определить Callback-интерфейс, через которым компонент мог бы уведомлять
    // MainActivity о выборе порта пользователем. Но это выглядит избыточным. Кажется, что
    // функциональной лямбда-функции достаточно. Unit - это аналог void в C/C++
    private val clickListener: (position: Int) -> Unit
) : RecyclerView.Adapter<CdcPortsAdapter.PortViewHolder>() {

    // Класс List не содержит методов для изменения содержимого, что позволяет говорить
    // о том, что повлиять на изменяемый внешний код адаптер не сможет
    private var portList: List<CdcPortData> = emptyList()

    fun updateData(newPorts: List<CdcPortData>) {
        portList = newPorts

        // В более сложных случаях имеет смысл использовать DiffUtil.calculateDiff()
        notifyDataSetChanged()
    }

    class PortViewHolder(view: ViewGroup) : RecyclerView.ViewHolder(view) {
        val idNumber: TextView = view.findViewById(R.id.idNumber)
        val writeEndpoint: TextView = view.findViewById(R.id.writeEndpoint)
        val readEndpoint: TextView = view.findViewById(R.id.readEndpoint)
    }

    // Метод сообщает RecyclerView о том, сколько элементов в списке
    override fun getItemCount(): Int = portList.size

    // Optional: мы можем использовать стабильные идентификаторы для анимации. Если это нужно,
    // то необходимо полноценно реализовать этот метод
    override fun getItemId(position: Int): Long = position.toLong()

    /**
     * Метод создаёт View Holder. Вызывается, когда RecyclerView создаёт совершенно новый view
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.row, parent, false)
        return PortViewHolder(view as ViewGroup)
    }

    /**
     * Метод позволяет связать существующий View Holder и данные для конкретной строки массива.
     * Этот метод вызывается каждый раз, когда View необходимо обновить/отобразить элемента
     * массива в конкретной позиции.
     * Это аналог getView() из BaseAdapter-а
     */
    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        val portData = portList[position]

        // Используем ссылку на ViewHolder для установки отображаемых данных
        holder.idNumber.text = portData.id.toString()
        holder.writeEndpoint.text = portData.writeEndpoint.toString()
        holder.readEndpoint.text = portData.readEndpoint.toString()

        // Устанавливаем обработчик событий нажатия пользователем на элемент RecyclerView
        holder.itemView.setOnClickListener {
            clickListener(position)
        }
    }
}
