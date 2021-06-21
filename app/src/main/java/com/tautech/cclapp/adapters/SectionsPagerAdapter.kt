package com.tautech.cclapp.adapters

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.ManageDeliveryItemsFragment
import com.tautech.cclapp.activities.ui_delivery_detail.delivery_form.DeliveryFormFragment
import com.tautech.cclapp.activities.ui_delivery_detail.delivery_payment.DeliveryPaymentFragment
import com.tautech.cclapp.models.DeliveryLine

private val TAB_TITLES = arrayOf(
    R.string.form,
    R.string.items,
    R.string.payment
)

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(private val context: Context, fm: FragmentManager,
                           private var onDeliveryLineChangedCallback: ((deliveryLine: DeliveryLine) -> Unit) ? = null,
                           private val exceptLastPage: Boolean = false) :
    FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getItem(position: Int): Fragment {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).
        Log.i("SECTIONS_PAGER_ADAPTER", "position: $position")
        val fragment: Fragment? = when(position){
            0 -> {
                DeliveryFormFragment.getInstance()
            }
            1 -> {
                ManageDeliveryItemsFragment.getInstance(onDeliveryLineChangedCallback)
            }
            2 -> {
                DeliveryPaymentFragment.getInstance()
            }
            else -> {
                DeliveryFormFragment.getInstance()
            }
        }
        return fragment!!
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        val  exceptPages = if (exceptLastPage){ 1 }else{ 0 }
        return 3 - exceptPages
        //return 3
    }

    fun clear(){
        DeliveryFormFragment.mInstance = null
        ManageDeliveryItemsFragment.mInstance = null
        DeliveryFormFragment.mInstance = null
    }
}