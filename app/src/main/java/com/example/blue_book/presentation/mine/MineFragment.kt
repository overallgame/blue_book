package com.example.blue_book.presentation.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.blue_book.R
import com.example.blue_book.databinding.MinePageBinding
import com.example.blue_book.presentation.image.ImagePickerActivity
import com.example.blue_book.presentation.mine.page.MineCollectionFragment
import com.example.blue_book.presentation.mine.page.MineLoveFragment
import com.example.blue_book.presentation.mine.page.MineWorkFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MineFragment : Fragment() {

    private var _binding: MinePageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MineViewModel by viewModels()
    private lateinit var fragments: List<Fragment>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MinePageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initActivityResult()
        initSwipeRefreshLayout()
        initNavigationView()
        initRadioGroup()
        initViewPager()
        initImagePickers()
        observeViewModel()
        viewModel.dispatch(MineIntent.Init)
    }

    private fun initActivityResult() {
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val tag = result.data?.getStringExtra(ImagePickerActivity.EXTRA_TAG)
                when (tag) {
                    "avatar" -> {
                        binding.mineAvatar.setImageURI(uri)
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.dispatch(MineIntent.UpdateAvatar(uri.toString()))
                        }
                    }
                    "backgroundImage" -> {
                        binding.mineBackgroundImage.setImageURI(uri)
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.dispatch(MineIntent.UpdateBackground(uri.toString()))
                        }
                    }
                }
            }
        }
    }

    private fun initSwipeRefreshLayout() {
        binding.mineSwipeRefreshLayout.setOnRefreshListener {
            viewModel.dispatch(MineIntent.Refresh)
        }
    }

    private fun initNavigationView() {
        binding.mineNavButton.setOnClickListener {
            binding.layoutMine.openDrawer(GravityCompat.START)
        }
        binding.minePagerNavigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_backLogin -> {
                    viewModel.dispatch(MineIntent.Logout)
                    true
                }

                else -> {
                    binding.layoutMine.closeDrawers()
                    true
                }
            }
        }
    }

    private fun initRadioGroup() {
        binding.mineNavRadioGroup.setOnCheckedChangeListener { _, checkId ->
            when (checkId) {
                binding.mineWork.id -> binding.mineViewPager.currentItem = 0
                binding.mineCollection.id -> binding.mineViewPager.currentItem = 1
                binding.mineLove.id -> binding.mineViewPager.currentItem = 2
            }
        }
        binding.mineEditUserProfile.setOnClickListener {
            findNavController().navigate(R.id.userProfileEditFragment)
        }
    }

    private fun initImagePickers() {
        binding.mineAvatar.setOnClickListener {
            openCustomImagePicker("avatar")
        }
        binding.mineBackgroundImage.setOnClickListener {
            openCustomImagePicker("backgroundImage")
        }
    }

    private fun openCustomImagePicker(tag: String) {
        val intent = Intent(requireContext(), ImagePickerActivity::class.java)
        intent.putExtra(ImagePickerActivity.EXTRA_TAG, tag)
        pickImageLauncher.launch(intent)
    }

    private fun initViewPager() {
        binding.mineViewPager.run {
            fragments = listOf(MineWorkFragment(), MineCollectionFragment(), MineLoveFragment())
            adapter = object : FragmentStateAdapter(childFragmentManager, lifecycle) {
                override fun getItemCount(): Int = fragments.size
                override fun createFragment(position: Int): Fragment = fragments[position]
            }
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    when (position) {
                        0 -> binding.mineNavRadioGroup.check(binding.mineWork.id)
                        1 -> binding.mineNavRadioGroup.check(binding.mineCollection.id)
                        2 -> binding.mineNavRadioGroup.check(binding.mineLove.id)
                    }
                }
            })
        }
    }

    private fun observeViewModel() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {})
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val user = state.user
                        if (user != null) {
                            user.background?.let {
                                Glide.with(requireContext()).load(it)
                                    .into(binding.mineBackgroundImage)
                            }
                            user.avatar?.let {
                                Glide.with(requireContext()).load(it).into(binding.mineAvatar)
                            }
                            binding.mineNickname.text = user.nickname ?: user.phone
                            binding.mineIntroduction.text = user.introduction.orEmpty()
                        }
                        binding.mineSwipeRefreshLayout.isRefreshing = false
                    }
                }
                launch {
                    viewModel.uiEffect.collect { effect ->
                        when (effect) {
                            is MineEffect.ShowToast -> Toast.makeText(
                                requireContext(),
                                effect.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}