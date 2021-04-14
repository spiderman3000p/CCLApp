package com.tautech.cclapp.activities.ui_delivery_detail.delivery_detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.DeliveryDetailActivity
import com.tautech.cclapp.activities.DeliveryDetailActivityViewModel
import com.tautech.cclapp.classes.CclUtilities
import kotlinx.android.synthetic.main.fragment_delivery_detail.view.*

class DeliveryDetailFragment : Fragment() {
  val TAG = "DELIVERY_DETAIL_FRAGMENT"
  private val viewModel: DeliveryDetailActivityViewModel by activityViewModels()
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val root = inflater.inflate(R.layout.fragment_delivery_detail, container, false)
    viewModel.delivery.observe(viewLifecycleOwner, Observer{delivery ->
      root.deliveryStateChip.text = delivery.deliveryState
      root.deliveryStateChip.chipBackgroundColor = when(delivery.deliveryState) {
        "Created" -> ContextCompat.getColorStateList(requireContext(), R.color.created_bg)
        "Partial" -> ContextCompat.getColorStateList(requireContext(), R.color.partial_bg)
        "InDepot" -> ContextCompat.getColorStateList(requireContext(), R.color.indepot_bg)
        "Dispatched" -> ContextCompat.getColorStateList(requireContext(), R.color.dispatched_bg)
        "ReDispatched" -> ContextCompat.getColorStateList(requireContext(), R.color.redispatched_bg)
        "Cancelled" -> ContextCompat.getColorStateList(requireContext(), R.color.cancelled_bg)
        "Delivered" -> ContextCompat.getColorStateList(requireContext(), R.color.delivered_bg)
        "OnGoing" -> ContextCompat.getColorStateList(requireContext(), R.color.ongoing_bg)
        "UnDelivered" -> ContextCompat.getColorStateList(requireContext(), R.color.undelivered_bg)
        "Planned" -> ContextCompat.getColorStateList(requireContext(), R.color.planned_bg)
        "DeliveryPlanned" -> ContextCompat.getColorStateList(requireContext(), R.color.planned_bg)
        else -> ContextCompat.getColorStateList(requireContext(), R.color.created_bg)
      }
      val utilities = CclUtilities.getInstance()
      root.deliveryNumberTv.text = "#${delivery.deliveryNumber}"
      root.receiverNameTv.text = delivery.receiverName .let {
        if (it.isNullOrEmpty()) {
          getString(R.string.no_receiver_name)
        } else {
          it
        }
      }
      root.receiverAddressTv.text = delivery.receiverAddress
      root.dateTv2.text = delivery.orderDate
      //root.senderNameTv.text = delivery.senderName
      //root.citySenderNameTv.text = delivery.citySenderName
      root.citySenderNameTv.visibility = View.GONE
      root.senderNameTv.visibility = View.GONE
      root.textView2.visibility = View.GONE
      root.completedTv.text = "${getCompletedDeliveryLinesProgress()}% ${getString(R.string.completed)}"
      root.completedProgressBar.progress = getCompletedDeliveryLinesProgress()
      root.totalItemsChip.text = delivery.totalQuantity.toString()
      root.totalCertifiedItemsChip.text = delivery.totalCertified.toString()
      root.totalWeightChip.text = utilities.formatQuantity(delivery.totalWeight ?: 0.0) + " kg"
      root.totalValueChip.text = utilities.formatQuantity(delivery.totalValue ?: 0.0) + " $"
      if (activity?.packageManager != null) {
        if (!delivery.receiverAddressLatitude.isNullOrEmpty() && !delivery.receiverAddressLongitude.isNullOrEmpty()) {
          // Create a Uri from an intent string. Use the result to create an Intent.
          val gmmIntentUri =
            Uri.parse("google.navigation:q=${delivery.receiverAddressLatitude},${delivery.receiverAddressLongitude}")
          // Create an Intent from gmmIntentUri. Set the action to ACTION_VIEW
          val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
          // Make the Intent explicit by setting the Google Maps package
          mapIntent.setPackage("com.google.android.apps.maps")
          mapIntent.resolveActivity(activity?.packageManager!!).let {
            root.howGoBtn.setOnClickListener { view ->
              // Attempt to start an activity that can handle the Intent
              if (it != null) {
                startActivity(mapIntent)
              } else {
                Toast.makeText(requireContext(),
                  getString(R.string.no_map_app_installed),
                  Toast.LENGTH_SHORT).show()
              }
            }
          }
        } else {
          Log.e(TAG, getString(R.string.invalid_receiver_coords))
          root.howGoBtn.visibility = View.GONE
        }
      }
    })
    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as DeliveryDetailActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    (activity as DeliveryDetailActivity).supportActionBar?.setDisplayShowHomeEnabled(true)

  }

  fun getCompletedDeliveryLinesProgress(): Int {
    return ((viewModel.delivery.value?.totalDelivered ?: 0) * 100) / (viewModel.delivery.value?.totalQuantity ?: 1)
  }
}