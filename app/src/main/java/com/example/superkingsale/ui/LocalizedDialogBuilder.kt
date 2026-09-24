package com.example.superkingsale.ui

import android.content.Context
import android.content.DialogInterface

/** Applies the selected app language consistently to native confirmation and form dialogs. */
class LocalizedDialogBuilder(private val languageContext: Context) : com.google.android.material.dialog.MaterialAlertDialogBuilder(languageContext) {
    override fun setTitle(title: CharSequence?): LocalizedDialogBuilder {
        super.setTitle(title?.let { languageContext.tr(it.toString()) }); return this
    }
    override fun setMessage(message: CharSequence?): LocalizedDialogBuilder {
        super.setMessage(message?.let { languageContext.tr(it.toString()) }); return this
    }
    override fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): LocalizedDialogBuilder {
        super.setPositiveButton(text?.let { languageContext.tr(it.toString()) }, listener); return this
    }
    override fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): LocalizedDialogBuilder {
        super.setNegativeButton(text?.let { languageContext.tr(it.toString()) }, listener); return this
    }
    override fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): LocalizedDialogBuilder {
        super.setNeutralButton(text?.let { languageContext.tr(it.toString()) }, listener); return this
    }
}
