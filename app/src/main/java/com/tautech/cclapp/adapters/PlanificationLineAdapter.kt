package com.tautech.cclapp.adapters

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tautech.cclapp.activities.DeliveryDetailActivity
import com.tautech.cclapp.R
import com.tautech.cclapp.models.*
import kotlinx.android.synthetic.main.planification_card_item.view.*

class PlanificationLineAdapter(private val dataList: MutableList<PlanificationLine> = mutableListOf(), val planification: Planification?, val context: Context):
    RecyclerView.Adapter<PlanificationLineAdapter.MyViewHolder>() {
    class MyViewHolder(val itemView: View, val planification: Planification?): RecyclerView.ViewHolder(itemView) {
        val openButton: Button
        init {
            openButton = itemView.findViewById(R.id.openBtn)
            openButton.visibility = View.GONE
            if (planification?.state == "OnGoing") {
                openButton.visibility = View.VISIBLE
            }
        }
        fun bindItems(delivery: PlanificationLine? = null) {
            Log.i("Planification Line Adapter", "delivery: $delivery")
            itemView.unitsTv?.visibility = View.GONE
            var colorStateList: ColorStateList? = null
            var backgroundColor: Int = 0
            when (delivery?.deliveryState) {
                "Created" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.created_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.created_bg)
                }
                "Planned" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.planned_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.planned_bg)
                }
                "OnGoing" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.ongoing_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.ongoing_bg)
                }
                "InDepot" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.indepot_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.indepot_bg)
                }
                "Delivered" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.delivered_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.delivered_bg)
                }
                "UnDelivered" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.undelivered_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.delivered_bg)
                }
                "Partial" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.partial_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.partial_bg)
                }
                "ReDispatched" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.redispatched_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.redispatched_bg)
                }
                "Cancelled" -> {
                    backgroundColor = ContextCompat.getColor(itemView.context, R.color.cancelled_bg)
                    colorStateList = ContextCompat.getColorStateList(itemView.context, R.color.cancelled_bg)
                }
            }
            itemView.cardView.setCardBackgroundColor(backgroundColor)
            itemView.stateTv.backgroundTintList = colorStateList
            itemView.stateTv.text = delivery?.deliveryState
            itemView.titleTv?.text = "${delivery?.id}-${delivery?.referenceDocument ?: ""}"
            val operation = when(planification?.planificationType){
                "National" -> itemView.context.getString(R.string.certified)
                else -> itemView.context.getString(R.string.delivered)
            }
            val percent = when(planification?.planificationType){
                "National" -> 0.00//TODO: necesito certificate para cada delivery
                else -> (100 * (delivery?.totalDelivered ?: 0) / (delivery?.totalQuantity ?: 1)).toDouble()
            }
            val percentStr = String.format("%.2f", percent) + "% " + operation
            itemView.percentCertifiedTv?.text = percentStr
            //itemView.progressBar.setProgress(percent.toInt(), true)
            itemView.customerTv?.text = delivery?.receiverName
            itemView.addressTv?.text = delivery?.receiverAddress
            itemView.dateTv?.text = delivery?.deliveryDate
            itemView.qtyTv?.text = delivery?.totalQuantity.toString()
            itemView.deliveryLinesTv?.text = delivery?.totalQuantity.toString()
            //itemView.unitsTv.text = planification.totalUnits.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.planification_card_item, parent, false)
        return MyViewHolder(view, planification)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bindItems(dataList[position])
        holder.openButton.setOnClickListener { view ->
            // iniciar nuevo activity
            // TODO: quitar eso de estado = null
            val intent = Intent(view.context, DeliveryDetailActivity::class.java).apply {
                putExtra("delivery", dataList[position])
                putExtra("planification", planification)
            }
            //intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP and Intent.FLAG_ACTIVITY_CLEAR_TASK
            view.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }
}