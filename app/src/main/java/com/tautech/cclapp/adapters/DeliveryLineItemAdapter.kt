package com.tautech.cclapp.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import com.tautech.cclapp.R
import com.tautech.cclapp.models.DeliveryLine
import kotlinx.android.synthetic.main.delivery_line_item.view.*
import kotlinx.android.synthetic.main.last_readed_item.view.referenceTv
import kotlinx.android.synthetic.main.last_readed_item.view.skuDescriptionTv

val TAG = "DeliveryLineItemAdapter"
class DeliveryLineItemAdapter(private val dataList: MutableList<DeliveryLine>, val editable: Boolean = false, val context: Context,
                              private var onDeliveryLineChangedCallback: ((deliveryLine: DeliveryLine) -> Unit)? = null):
    RecyclerView.Adapter<DeliveryLineItemAdapter.MyViewHolder>() {

    class MyViewHolder(var itemView: View, var editable: Boolean = false,
                       var onDeliveryLineChangedCallback: ((deliveryLine: DeliveryLine) -> Unit)? = null
    ): RecyclerView.ViewHolder(itemView), SeekBar.OnSeekBarChangeListener {
        var deliveryLine: DeliveryLine? = null
        fun bindItems(_deliveryLine: DeliveryLine, editable: Boolean = false) {
            deliveryLine = _deliveryLine
            itemView.skuDescriptionTv .text = deliveryLine?.description
            itemView.referenceTv.text = deliveryLine?.reference
            if (!editable) {
                itemView.quantitySeekBar.visibility = View.GONE
            }
            itemView.quantitySeekBar.setOnSeekBarChangeListener(this)
            itemView.quantityTv.text = "${deliveryLine?.delivered}/${deliveryLine?.quantity}"
            (itemView.quantitySeekBar as SeekBar).max = deliveryLine?.quantity ?: 0
            (itemView.quantitySeekBar as SeekBar).progress = deliveryLine?.delivered ?: 0
        }
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            Log.i(TAG, "progress changed for line: $deliveryLine")
            if (fromUser) {
                if (deliveryLine != null) {
                    if (progress >= deliveryLine?.quantity ?: 0) {
                        deliveryLine?.delivered = deliveryLine?.quantity ?: 0
                        itemView.quantityTv.text =
                            "${deliveryLine?.quantity}/${deliveryLine?.quantity}"
                        seekBar?.progress = deliveryLine?.quantity ?: 0
                    } else {
                        itemView.quantityTv.text = "${progress}/${deliveryLine?.quantity}"
                        deliveryLine?.delivered = progress
                    }
                    Log.i(TAG, "delivery line changed: $deliveryLine")
                    onDeliveryLineChangedCallback?.let{
                        it(deliveryLine!!)
                    }.also {
                        if (it == null) {
                            Log.i(TAG, "no se recibio view model o es invalido")
                        }
                    }
                } else {
                    Log.i(TAG, "delivery line es invalido")
                }
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {

        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.delivery_line_item, parent, false)
        return MyViewHolder(view, editable, onDeliveryLineChangedCallback)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bindItems(dataList[position], editable)
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    fun getDataList(): MutableList<DeliveryLine>{
        return dataList
    }
}