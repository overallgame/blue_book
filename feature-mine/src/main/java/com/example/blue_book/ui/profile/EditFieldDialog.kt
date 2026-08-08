package com.example.blue_book.ui.profile

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.example.blue_book.feature_mine.R

enum class FieldType {
    NICKNAME, BIO, OCCUPATION, SCHOOL, GENDER, REGION
}

interface OnFieldConfirmedListener {
    fun onFieldConfirmed(fieldType: FieldType, value: String)
}

class EditFieldDialog : DialogFragment() {

    companion object {
        private const val ARG_FIELD_TYPE = "field_type"
        private const val ARG_TITLE = "title"
        private const val ARG_CURRENT_VALUE = "current_value"

        fun newInstance(fieldType: FieldType, title: String, currentValue: String): EditFieldDialog {
            return EditFieldDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_FIELD_TYPE, fieldType.name)
                    putString(ARG_TITLE, title)
                    putString(ARG_CURRENT_VALUE, currentValue)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val fieldTypeName = arguments?.getString(ARG_FIELD_TYPE) ?: return super.onCreateDialog(savedInstanceState)
        val fieldType = try { FieldType.valueOf(fieldTypeName) } catch (_: Exception) { return super.onCreateDialog(savedInstanceState) }
        val title = arguments?.getString(ARG_TITLE).orEmpty()
        val currentValue = arguments?.getString(ARG_CURRENT_VALUE).orEmpty()

        val listener = parentFragment as? OnFieldConfirmedListener
            ?: parentFragmentManager.fragments.firstNotNullOfOrNull { it as? OnFieldConfirmedListener }

        return when (fieldType) {
            FieldType.GENDER -> createGenderDialog(title, currentValue, listener, fieldType)
            FieldType.REGION -> createRegionDialog(title, currentValue, listener, fieldType)
            else -> createTextEditDialog(title, currentValue, listener, fieldType)
        }
    }

    private fun createTextEditDialog(
        title: String,
        currentValue: String,
        listener: OnFieldConfirmedListener?,
        fieldType: FieldType
    ): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_field, null)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialog_input_layout)
        val editText = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialog_input_edit)

        inputLayout.hint = title
        inputLayout.counterMaxLength = if (fieldType == FieldType.BIO) 100 else 30
        editText.setText(currentValue)
        editText.setSelection(editText.text?.length ?: 0)

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton("确认") { _, _ ->
                val value = editText.text?.toString().orEmpty()
                listener?.onFieldConfirmed(fieldType, value)
            }
            .setNegativeButton("取消", null)
            .create()
    }

    private fun createGenderDialog(
        title: String,
        currentValue: String,
        listener: OnFieldConfirmedListener?,
        fieldType: FieldType
    ): Dialog {
        val options = arrayOf("男", "女", "其他")
        val checkedIndex = options.indexOfFirst { it == currentValue }.coerceAtLeast(0)

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                listener?.onFieldConfirmed(fieldType, options[which])
                dialog.dismiss()
            }
            .create()
    }

    private fun createRegionDialog(
        title: String,
        @Suppress("UNUSED_PARAMETER") currentValue: String,
        listener: OnFieldConfirmedListener?,
        fieldType: FieldType
    ): Dialog {
        val regions = arrayOf("北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "重庆", "其他")

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(regions) { dialog, which ->
                listener?.onFieldConfirmed(fieldType, regions[which])
                dialog.dismiss()
            }
            .create()
    }
}
