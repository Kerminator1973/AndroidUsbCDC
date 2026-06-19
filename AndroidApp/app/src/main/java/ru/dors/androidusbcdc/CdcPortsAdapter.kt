package ru.dors.androidusbcdc

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

// Определяем Callback-интерфейс, через который MainActivity сможет получить уведомление
// о том, что пользователь осуществил выбор порта подключения
interface OnItemClickListener {
    fun onItemClick(position: Int)
}

// При создании экземпляра класса, необходимо указать не только массив портов для выбора, но
// и экземпляр класса, который реализует интерфейс OnItemClickListener
class CdcPortsAdapter(
    private val context: Context,
    private val arrayList: java.util.ArrayList<CdcPortData>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<CdcPortsAdapter.PortViewHolder>() {

    inner class PortViewHolder(view: ViewGroup) : RecyclerView.ViewHolder(view) {
        val idNumber: TextView = view.findViewById(R.id.idNumber)
        val writeEndpoint: TextView = view.findViewById(R.id.writeEndpoint)
        val readEndpoint: TextView = view.findViewById(R.id.readEndpoint)
    }

    // Метод сообщает RecyclerView о том, сколько элементов в списке
    override fun getItemCount(): Int = arrayList.size

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
        val portData = arrayList[position]

        // Используем ссылку на ViewHolder для установки отображаемых данных
        holder.idNumber.text = portData.id.toString()
        holder.writeEndpoint.text = "Write Endpoint: ${portData.writeEndpoint}"
        holder.readEndpoint.text = "Read Endpoint: ${portData.readEndpoint}"

        // Устанавливаем обработчик событий нажатия пользователем на элемент RecyclerView
        holder.itemView.setOnClickListener {
            listener.onItemClick(position) // Вызываем Callback-метод Activity
        }
    }
}
