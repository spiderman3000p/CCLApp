package com.tautech.cclapp.classes

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import java.text.SimpleDateFormat
import java.util.*


class DatePickerFragmentDialog(val index: Int) : DialogFragment(), DatePickerDialog.OnDateSetListener{
    val c: Calendar = Calendar.getInstance()
    val TAG = "DATEPICKER_FRAGMENT"
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        return DatePickerDialog(requireContext(), this, year, month, day)
    }
    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        c.set(Calendar.YEAR, year)
        c.set(Calendar.MONTH, month)
        c.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        val selectedDate: String =
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(c.getTime())
        Log.d(TAG, "onDateSet: $selectedDate")
        // send date back to the target fragment
        // send date back to the target fragment
        val intent = Intent()
        intent.putExtra("selectedDate", selectedDate)
        intent.putExtra("index", index)
        targetFragment?.onActivityResult(
            targetRequestCode,
            Activity.RESULT_OK,
            intent
        )
    }
}