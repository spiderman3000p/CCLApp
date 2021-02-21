package com.tautech.cclapp.ui.main

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.tautech.cclapp.activities.ManageDeliveryItemsFragment
import com.tautech.cclapp.R
import com.tautech.cclapp.activities.ui_delivery_detail.delivery_form.DeliveryFormFragment

private val TAB_TITLES = arrayOf(
    R.string.form,
    R.string.items
)

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(private val context: Context, fm: FragmentManager) :
    FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    var formFragment: DeliveryFormFragment? = null
    override fun getItem(position: Int): Fragment {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).
        Log.i("SECTIONS_PAGER_ADAPTER", "position: $position")
        val fragment: Fragment? = when(position){
            0 -> {
                if (formFragment == null){
                    formFragment = DeliveryFormFragment()
                }
                formFragment
            }
            1 -> {
                ManageDeliveryItemsFragment(true)
            }
            else -> {
                if (formFragment == null){
                    formFragment = DeliveryFormFragment()
                }
                formFragment
            }
        }
        return fragment!!
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        // Show 2 total pages.
        return 2
    }

    fun getDeliveryFormFragment(): DeliveryFormFragment? {
        return formFragment
    }
}