package com.example.blue_book.presentation.image

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.R

class GalleryFragment : Fragment() {

    private var tag: String? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var recyclerView: RecyclerView
    private lateinit var galleryAdapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tag = arguments?.getString(ARG_TAG)
        initPermissionLauncher()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.galleryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        galleryAdapter = GalleryAdapter(emptyList()) { uri ->
            (activity as? ImagePickerActivity)?.openCrop(uri, tag)
        }
        recyclerView.adapter = galleryAdapter

        checkPermissionAndLoad()
    }

    private fun initPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                loadAndShowImages()
            } else {
                // 权限被拒绝，这里可以根据需要提示用户
            }
        }
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            loadAndShowImages()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadAndShowImages() {
        val images = loadImages()
        galleryAdapter = GalleryAdapter(images) { uri ->
            (activity as? ImagePickerActivity)?.openCrop(uri, tag)
        }
        recyclerView.adapter = galleryAdapter
    }

    private fun loadImages(): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                uris.add(contentUri)
            }
        }
        return uris
    }

    class GalleryAdapter(
        private val items: List<Uri>,
        private val onClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: androidx.appcompat.widget.AppCompatImageView =
                itemView.findViewById(R.id.itemImage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery_image, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = items[position]
            Glide.with(holder.imageView.context)
                .load(uri)
                .centerCrop()
                .into(holder.imageView)
            holder.itemView.setOnClickListener { onClick(uri) }
        }
    }

    companion object {
        private const val ARG_TAG = "arg_tag"

        fun newInstance(tag: String?): GalleryFragment {
            val fragment = GalleryFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_TAG, tag)
            }
            return fragment
        }
    }
}
