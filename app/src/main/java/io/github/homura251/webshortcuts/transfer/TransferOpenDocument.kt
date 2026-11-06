package io.github.homura251.webshortcuts.transfer

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Restrict selection to documents that can be read as streams, excluding virtual-only files. */
class TransferOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
}
