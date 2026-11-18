package com.example.examen

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MedicionAdapter(private var lista: List<Medicion>) :
    RecyclerView.Adapter<MedicionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSensor: TextView = view.findViewById(R.id.tv_item_sensor)
        val tvEstado: TextView = view.findViewById(R.id.tv_item_estado)
        val tvValor: TextView = view.findViewById(R.id.tv_item_valor)
        val tvEjes: TextView = view.findViewById(R.id.tv_item_ejes)
        val tvFecha: TextView = view.findViewById(R.id.tv_item_fecha)
        val tvFirestoreId: TextView = view.findViewById(R.id.tv_item_firestore_id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.tvSensor.text = "📡 ${item.tipoSensor} (#${item.id})"
        holder.tvValor.text = String.format(Locale.US, "%.2f", item.valor)
        holder.tvEjes.text = String.format(Locale.US, "X:%.1f | Y:%.1f | Z:%.1f", item.ejeX, item.ejeY, item.ejeZ)
        holder.tvFecha.text = "🕒 ${item.fechaHora}"

        if (item.isSynced == 1) {
            holder.tvEstado.text = "☁️ Sincronizado"
            holder.tvEstado.setTextColor(Color.parseColor("#1B5E20")) // Verde oscuro
            holder.tvEstado.setBackgroundColor(Color.parseColor("#E8F5E9")) // Verde claro
            val shortId = if (item.firestoreId.length > 8) item.firestoreId.substring(0, 8) + "..." else item.firestoreId
            holder.tvFirestoreId.text = "Doc: $shortId"
            holder.tvFirestoreId.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            holder.tvEstado.text = "⏳ Solo Local"
            holder.tvEstado.setTextColor(Color.parseColor("#E65100")) // Naranja oscuro
            holder.tvEstado.setBackgroundColor(Color.parseColor("#FFF3E0")) // Naranja claro
            holder.tvFirestoreId.text = "Doc: pendiente"
            holder.tvFirestoreId.setTextColor(Color.parseColor("#B0BEC5"))
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<Medicion>) {
        this.lista = nuevaLista
        notifyDataSetChanged()
    }
}
