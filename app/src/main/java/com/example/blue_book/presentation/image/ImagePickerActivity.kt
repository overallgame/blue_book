package com.example.blue_book.presentation.image

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.R

class ImagePickerActivity : AppCompatActivity() {

    private var tag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_picker)

        tag = intent.getStringExtra(EXTRA_TAG)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.imagePickerContainer, GalleryFragment.newInstance(tag))
            }
        }
    }

    fun openCrop(uri: Uri, tag: String?) {
        supportFragmentManager.commit {
            replace(R.id.imagePickerContainer, ImageCropFragment.newInstance(uri, tag))
            addToBackStack(null)
        }
    }

    fun finishWithResult(uri: Uri, tag: String?) {
        val data = Intent().apply {
            this.data = uri
            putExtra(EXTRA_TAG, tag)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_TAG = "tag"
    }
}
