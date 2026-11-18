package com.example.examen

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MedicionAdapter(private var lista: List<Medicion>) :
    RecyclerView.Adapter<MedicionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSensor: TextView = view.findViewById(R.id.tv_item_sensor)
        val tvEstado: TextView = view.findViewById(R.id.tv_item_estado)
        val tvValor: TextView = view.findViewById(R.id.tv_item_valor)
        val tvFecha: TextView = view.findViewById(R.id.tv_item_fecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.tvSensor.text = "Sensor: ${item.tipoSensor} (#${item.id})"
        holder.tvValor.text = String.format("Magnitud: %.2f", item.valor)
        holder.tvFecha.text = item.fechaHora

        if (item.isSynced == 1) {
            holder.tvEstado.text = "☁️ Nube"
            holder.tvEstado.setTextColor(Color.parseColor("#2E7D32"))
            holder.tvEstado.setBackgroundColor(Color.parseColor("#E8F5E9"))
        } else {
            holder.tvEstado.text = "⏳ Local"
            holder.tvEstado.setTextColor(Color.parseColor("#E65100"))
            holder.tvEstado.setBackgroundColor(Color.parseColor("#FFF3E0"))
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<Medicion>) {
        this.lista = nuevaLista
        notifyDataSetChanged()
    }
}
