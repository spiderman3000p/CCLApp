package com.tautech.cclapp.classes

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import kotlinx.android.synthetic.main.fragment_planification_preview.*
import java.text.NumberFormat
import java.util.*
import androidx.lifecycle.Observer
import com.tautech.cclapp.models.Delivery
import kotlin.collections.ArrayList


class PreviewPlanificationDialog(val function: () -> Unit, val objectC: Totals) : DialogFragment() {
    lateinit  var toolbar: Toolbar
    val TAG = "PREVIEW_DIALOG"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
    }

    override fun onStart() {
        super.onStart()
        val dialog: Dialog? = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.getWindow()?.setLayout(width, height)
            dialog.getWindow()?.setWindowAnimations(R.style.AppThemeSlide);
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view: View = inflater.inflate(R.layout.fragment_planification_preview, container, false)
        toolbar = view.findViewById(R.id.toolbar)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setNavigationOnClickListener { v: View? -> dismiss() }
        toolbar.title = "Planification Preview"
        toolbar.setTitleTextColor(getColor(requireContext(), R.color.white))
        doneBtn2.setOnClickListener {
            function()
            dismiss()
        }
        drawList()
    }

    fun drawList(){
        planificationTotalTv.text = formatNumber(objectC.totalPlanificationValue)
        totalValueDeliveredTv.text = formatNumber(objectC.totalDelivered)
        itemsLabelTv.text = "${objectC.undeliveredDeliveries?.size} Deliveries"
        val totalDevolution = objectC.totalPlanificationValue - objectC.totalUndelivered
        totalValueDevolutionsTv.text = formatNumber(totalDevolution)
        differenceTv.text = formatNumber(totalDevolution)
        if (!objectC.undeliveredDeliveries.isEmpty()) {
            objectC.undeliveredDeliveries.forEach {
                val viewAux = View.inflate(requireContext(),
                    R.layout.linear_layout_item,
                    null) as LinearLayout
                (viewAux.getChildAt(0) as TextView).text = it.referenceDocument
                (viewAux.getChildAt(1) as TextView).text = formatNumber(it.totalValue ?: 0.0)
                devolutionsItemsList.addView(viewAux)
            }
            devolutionsItemsList.visibility = View.VISIBLE
        }
    }

    fun formatNumber(number: Double): String{
        val format: NumberFormat = NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 0
        format.currency = Currency.getInstance("COP")
        return format.format(number)
    }

    class Totals(
        var totalPlanificationValue: Double = 0.00,
        var totalDelivered: Double = 0.00,
        var totalUndelivered: Double = 0.00,
        val undeliveredDeliveries: ArrayList<Delivery> = arrayListOf()
    ){}

    companion object {
        fun display(fragmentManager: FragmentManager, objectC: Totals, function: () -> Unit): PreviewPlanificationDialog? {
            val previewDialog = PreviewPlanificationDialog(function, objectC)
            previewDialog.show(fragmentManager, "preview_dialog")
            return previewDialog
        }
    }
}