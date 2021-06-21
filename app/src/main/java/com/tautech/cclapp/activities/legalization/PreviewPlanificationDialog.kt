package com.tautech.cclapp.activities.legalization

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.MPPointF
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.PlanificationDetailActivityViewModel
import com.tautech.cclapp.classes.AuthStateManager
import com.tautech.cclapp.classes.CclUtilities
import com.tautech.cclapp.classes.Configuration
import com.tautech.cclapp.interfaces.CclDataService
import com.tautech.cclapp.models.PlanificationPaymentDetail
import com.tautech.cclapp.models.PlanificationDetails
import com.tautech.cclapp.services.CclClient
import kotlinx.android.synthetic.main.fragment_planification_preview.*
import kotlinx.android.synthetic.main.payment_method_label.view.*
import net.openid.appauth.AuthorizationException
import org.jetbrains.anko.doAsync
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException


class PreviewPlanificationDialog(val mFunction: () -> Unit, val planificationId: Long) : DialogFragment(),
    OnChartValueSelectedListener {
    private var planificationDetails: PlanificationDetails? = null
    val TAG = "PREVIEW_DIALOG"
    private var mStateManager: AuthStateManager? = null
    private lateinit var viewModel: PlanificationDetailActivityViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
        mStateManager = AuthStateManager.getInstance(requireContext())
        val config = Configuration.getInstance(requireContext())
        if (config.hasConfigurationChanged()) {
            Log.e(TAG, "La configuracion de sesion ha cambiado. Se cerrara su sesion")
            return
        }
        if (!mStateManager!!.current.isAuthorized) {
            Log.e(TAG, "Sesion expirada. Su sesion ha expirado")
            return
        }
        val _viewModel: PlanificationDetailActivityViewModel by activityViewModels()
        viewModel = _viewModel
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
        //toolbar = view.findViewById(R.id.toolbar)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setNavigationOnClickListener { v: View? -> dismiss() }
        toolbar.title = "Planification Preview"
        toolbar.setTitleTextColor(getColor(requireContext(), R.color.white))
        doneBtn2.text = getString(R.string.legalize)
        getPlanificationDetails()
        initPieChart()
        initHorizontalBarChart()
        viewModel.legalizedComplete.observe(viewLifecycleOwner, {
            if(it) {
                mFunction()
                dismiss()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()")
        Log.i(TAG, "viewModel.legalizedComplete.value: ${viewModel.legalizedComplete.value}")
        if(viewModel.legalizedComplete.value == true){
            mFunction()
            dismiss()
        }
    }

    private fun initHorizontalBarChart(){
        barChart.setOnChartValueSelectedListener(this)
        //barChart.setHighlightEnabled(this);
        barChart.setDrawBarShadow(false)
        barChart.setDrawValueAboveBar(true)
        barChart.description.setEnabled(false)
        // if more than 60 entries are displayed in the chart, no values will be
        // drawn
        barChart.setMaxVisibleValueCount(60)
        // scaling can now only be done on x- and y-axis separately
        barChart.setPinchZoom(false)
        // draw shadows for each bar that show the maximum value
        // chart.setDrawBarShadow(true);
        barChart.setDrawGridBackground(false)

        val xl: XAxis = barChart.xAxis
        xl.position = XAxis.XAxisPosition.BOTTOM
        //xl.setTypeface(tfLight)
        xl.setDrawAxisLine(true)
        xl.setDrawGridLines(false)
        val labels = hashMapOf<Int, String>()
        labels[8] = getString(R.string.deliveries)
        labels[6] = getString(R.string.delivereds)
        labels[4] = getString(R.string.partials)
        labels[2] = getString(R.string.undelivereds)
        labels[0] = getString(R.string.pendings)
        val formatter: ValueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String? {
                return labels[value.toInt()] ?: ""
            }
        }
        xl.granularity = 1f // minimum axis-step (interval) is 1
        xl.valueFormatter = formatter
        val yl: YAxis = barChart.axisLeft
        //yl.setTypeface(tfLight)
        yl.setDrawAxisLine(true)
        yl.setDrawGridLines(true)
        yl.axisMinimum = 0f // this replaces setStartAtZero(true)
        val yr: YAxis = barChart.axisRight
        //yr.setTypeface(tfLight)
        yr.setDrawAxisLine(true)
        yr.setDrawGridLines(false)
        yr.axisMinimum = 0f // this replaces setStartAtZero(true)
        //yr.setInverted(true);
        barChart.setFitBars(true)
        barChart.animateY(2500)
    }

    fun initPieChart(){
        Log.i(TAG, "Setting chart configs...")
        // in this example, a LineChart is initialized from xml
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.setExtraOffsets(5f, 10f, 5f, 5f)
        pieChart.setCenterTextSize(14F)
        pieChart.dragDecelerationFrictionCoef = 0.95f
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(Color.WHITE)
        pieChart.setTransparentCircleColor(Color.WHITE)
        pieChart.setTransparentCircleAlpha(110)
        pieChart.holeRadius = 58f
        pieChart.transparentCircleRadius = 61f
        pieChart.setDrawCenterText(true)
        pieChart.rotationAngle = 0f
        // enable rotation of the pieChart by touch
        pieChart.isRotationEnabled = true
        pieChart.isHighlightPerTapEnabled = true

        // add a selection listener
        pieChart.setOnChartValueSelectedListener(this)
        pieChart.animateY(1400, Easing.EaseInOutQuad)
        // leyend config
        val l = pieChart.legend
        l.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        l.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        l.orientation = Legend.LegendOrientation.VERTICAL
        l.setDrawInside(false)
        l.xEntrySpace = 7f
        l.yEntrySpace = 0f
        l.yOffset = 0f
        l.xOffset = 0f
        l.textSize = 14f
        l.formSize = 14f
        // entry label styling
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.setEntryLabelTextSize(14f)
    }

    private fun setBarChartData(planificationDetails: PlanificationDetails){
        activity?.runOnUiThread {
            val barWidth = 2f
            val colors = listOf(
                getColor(requireContext(), R.color.created_bg),
                getColor(requireContext(), R.color.delivered_bg),
                getColor(requireContext(), R.color.partial_bg),
                getColor(requireContext(), R.color.undelivered_bg),
                getColor(requireContext(), R.color.ongoing_bg)
            )
            val values: ArrayList<BarEntry> = ArrayList()
            values.add(BarEntry(8f, planificationDetails.deliveries.toFloat(),
                resources.getDrawable(R.drawable.ic_done)))
            values.add(BarEntry(6f, planificationDetails.delivered.toFloat(),
                resources.getDrawable(R.drawable.ic_done)))
            values.add(BarEntry(4f, planificationDetails.partial.toFloat(),
                resources.getDrawable(R.drawable.ic_done)))
            values.add(BarEntry(2f, planificationDetails.undelivered.toFloat(),
                resources.getDrawable(R.drawable.ic_done)))
            values.add(BarEntry(0f, planificationDetails.pending_deliveries.toFloat(),
                resources.getDrawable(R.drawable.ic_done)))
            val set1: BarDataSet
            if (barChart.data != null &&
                barChart.data.dataSetCount > 0
            ) {
                set1 = barChart.data.getDataSetByIndex(0) as BarDataSet
                set1.values = values
                set1.colors = colors
                barChart.data.notifyDataChanged()
                barChart.notifyDataSetChanged()
            } else {
                set1 = BarDataSet(values, "")
                set1.setDrawIcons(false)
                set1.colors = colors
                //set1.axisDependency = YAxis.AxisDependency.LEFT
                val dataSets: ArrayList<IBarDataSet> = ArrayList()
                dataSets.add(set1)
                val data = BarData(dataSets)
                data.setValueTextSize(14f)
                //data.setValueTypeface(tfLight)
                data.barWidth = barWidth
                barChart.data = data
            }
        }
    }

    private fun setPieChartData(payments: ArrayList<PlanificationPaymentDetail>) {
        Log.i(TAG, "Seting data for received payments: $payments")
        val entries: ArrayList<PieEntry> = ArrayList()
        // NOTE: The order of the entries when being added to the entries array determines their position around the center of
        // the pieChart.
        val utilities = CclUtilities.getInstance()
        // add a lot of colors
        val colors: ArrayList<Int> = ArrayList()
        for (c in ColorTemplate.JOYFUL_COLORS) colors.add(c)
        for (c in ColorTemplate.COLORFUL_COLORS) colors.add(c)
        for (c in ColorTemplate.LIBERTY_COLORS) colors.add(c)
        for (c in ColorTemplate.PASTEL_COLORS) colors.add(c)
        for (c in ColorTemplate.VORDIPLOM_COLORS) colors.add(c)
        colors.add(ColorTemplate.getHoloBlue())

        var totalPaymentsAmount: Double = 0.0
        payments.forEachIndexed { index, it ->
            Log.i(TAG, "agregando payment $index, entrada para el chart")
            val entry = PieEntry(it.amount?.toFloat() ?: 0F, it.paymentMethod,
                resources.getDrawable(R.drawable.ic_done)
                    .setTint(ColorTemplate.PASTEL_COLORS[index]))
            entries.add(entry)
            activity?.runOnUiThread{
                val labelView = View.inflate(requireContext(), R.layout.payment_method_label, null)
                labelsContainer.addView(labelView)
                labelView.paymentMethodNameTv.text = it.paymentMethod
                labelView.totalAmountTv.text = utilities.formatCurrencyNumber(it.amount ?: 0.0)
                labelView.circleLabelIv.imageTintList = ColorStateList.valueOf(colors[index])
            }
            totalPaymentsAmount += it.amount ?: 0.0
        }
        val dataSet = PieDataSet(entries, "")
        dataSet.setDrawIcons(true)
        dataSet.sliceSpace = 10f
        dataSet.iconsOffset = MPPointF(40F, 40F)
        dataSet.selectionShift = 5f
        dataSet.colors = colors
        dataSet.valueFormatter = PercentFormatter(pieChart)
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))
        data.setValueTextSize(14f)
        data.setValueTextColor(Color.BLACK)
        //data.setValueTypeface(tfLight)
        activity?.runOnUiThread {
            pieChart.setData(data)
            pieChart.setUsePercentValues(true)
            pieChart.setDrawEntryLabels(false)
            pieChart.centerText = utilities.formatCurrencyNumber(totalPaymentsAmount).replace("COP", "")
            // undo all highlights
            pieChart.highlightValues(null)
            pieChart.invalidate()
        }
    }

    private fun fetchData(callback: ((String?, String?, AuthorizationException?) -> Unit)) {
        Log.i(TAG, "fetching data...")
        mStateManager?.current?.performActionWithFreshTokens(mStateManager?.mAuthService!!,
            callback)
    }

    private fun getPlanificationDetails(){
        fetchData(this::getPlanificationDetails)
    }

    private fun getPlanificationDetails(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?,
    ) {
        if (ex != null) {
            Log.e(TAG, "ocurrio una excepcion al cambiar estado de planificacion", ex)
            if (ex.type == 2 && ex.code == 2002 && ex.error == "invalid_grant") {
                Log.e(TAG, "Sesion expirada. Su sesion ha expirado")
            }
            return
        }
        showLoader()
        //val url = "planificationVO5s/search/findByPlanificationId?planificationId=$planificationId"
        val url = "collectionVO3s/search/findByPlanificationId?planificationId=$planificationId"
        val dataService: CclDataService? = CclClient.getInstance()?.create(
            CclDataService::class.java)
        if (dataService != null && accessToken != null) {
            doAsync {
                try {
                    val call = dataService.getPlanificationDetails3(url,
                        "Bearer $accessToken")
                        .execute()
                    call.body()?.let {
                        Log.i(TAG, "respuesta planification details $planificationId: $it")
                        planificationDetails = it
                        val cashPaymentsAmount = PlanificationPaymentDetail(amount = planificationDetails?.cash
                            ?: 0.0, paymentMethod = getString(
                            R.string.cash))
                        val creditPaymentsAmount = PlanificationPaymentDetail(amount = planificationDetails?.credit
                            ?: 0.0, paymentMethod = getString(
                            R.string.credit))
                        val returnedAmount = PlanificationPaymentDetail(amount = planificationDetails?.returns
                            ?: 0.0, paymentMethod = getString(
                            R.string.returned))
                        val electronicAmount = PlanificationPaymentDetail(amount = planificationDetails?.electronic
                            ?: 0.0, paymentMethod = getString(
                            R.string.returned))
                        val paymentsList = arrayListOf(cashPaymentsAmount, creditPaymentsAmount,
                            returnedAmount, electronicAmount)
                        setPieChartData(paymentsList)
                        setBarChartData(it)
                        if((planificationDetails?.cash ?: 0.0) > 0) {
                            doneBtn2.text = getString(R.string.validate_cash)
                            doneBtn2.setOnClickListener {
                                LegalizePaymentsDialog.display(parentFragmentManager, planificationId, mFunction)
                            }
                        } else {
                            doneBtn2.setOnClickListener {
                                mFunction()
                                dismiss()
                            }
                        }
                        doneBtn2.visibility = View.VISIBLE
                    }
                    hideLoader()
                } catch (toe: SocketTimeoutException) {
                    Log.e(TAG, "Network error when fetching planification details", toe)
                    CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.network_error_title),
                        getString(R.string.network_error))
                } catch (ioEx: IOException) {
                    Log.e(TAG,
                        "Network error when fetching planification details",
                        ioEx)
                    CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.network_error_title),
                        getString(R.string.network_error))
                } catch (jsonEx: JSONException) {
                    Log.e(TAG, "Failed to parse finalizing planification response", jsonEx)
                    CclUtilities.getInstance().showAlert(requireActivity(),getString(R.string.parsing_error_title),
                        getString(R.string.parsing_error))
                } finally {
                    hideLoader()
                }
            }
        }
    }

    fun hideLoader() {
        activity?.runOnUiThread {
            progressBar?.visibility = View.GONE
        }
    }

    fun showLoader() {
        activity?.runOnUiThread{
            progressBar?.visibility = View.VISIBLE
        }
    }

    companion object {
        fun display(fragmentManager: FragmentManager, planificationId: Long, mFunction: () -> Unit): PreviewPlanificationDialog? {
            val previewDialog = PreviewPlanificationDialog(mFunction, planificationId)
            previewDialog.show(fragmentManager, "preview_dialog")
            return previewDialog
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {

    }

    override fun onNothingSelected() {

    }
}