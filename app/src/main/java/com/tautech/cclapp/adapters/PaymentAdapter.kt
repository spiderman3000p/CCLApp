package com.tautech.cclapp.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.tautech.cclapp.R
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.models.Payment
import kotlinx.android.synthetic.main.payment_card_item.view.*

class PaymentAdapter(private val dataList: MutableList<Payment>, private val fragmentManager: FragmentManager): RecyclerView.Adapter<PaymentAdapter.MyViewHolder>(){
    lateinit var context: Context
    private var onRemoveItemCallback: (() -> Unit)? = null
    private var onEditItemCallback: ((payment: Payment) -> Unit)? = null
    class MyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        fun bindItems(payment: Payment){
            val utilities = CclUtilities.getInstance()
            itemView.typeTv.text = payment.detail?.paymentMethod ?: itemView.context.getString(R.string.unknown)
            itemView.amountTv.text = utilities.formatCurrencyNumber(payment.detail?.amount ?: 0.0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.payment_card_item, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bindItems(dataList[position])
        holder.itemView.deleteBtn.setOnClickListener {
            dataList.removeAt(position)
            onRemoveItemCallback?.let {
                it()
            }
        }
        holder.itemView.amountTv.setOnClickListener {
            onEditItemCallback?.let {
                it(dataList[position])
            }
        }
    }

    fun setOnRemoveItemCallback(callback: (() -> Unit)? = null){
        callback?.let {
            onRemoveItemCallback = it
        }
    }

    fun setOnEditItemCallback(callback: ((item: Payment) -> Unit)? = null){
        callback?.let {
            onEditItemCallback = it
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context
    }
}