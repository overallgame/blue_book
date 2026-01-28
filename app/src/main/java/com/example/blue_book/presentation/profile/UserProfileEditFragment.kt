package com.example.blue_book.presentation.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.blue_book.databinding.UserProfilePageBinding
import com.example.blue_book.presentation.image.ImagePickerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserProfileEditFragment : Fragment() {

    private var _binding: UserProfilePageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserProfileViewModel by viewModels()
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private var avatarUrl: String? = null
    private var backgroundUrl: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = UserProfilePageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initActivityResult()
        initToolbar()
        initImagePickers()
        observeViewModel()
        viewModel.dispatch(UserProfileIntent.Init)
    }

    private fun initActivityResult() {
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            val tag = result.data?.getStringExtra(ImagePickerActivity.EXTRA_TAG)
            when (tag) {
                "avatar" -> {
                    avatarUrl = uri.toString()
                    binding.userInfoAvatar.setImageURI(uri)
                }
                "backgroundImage" -> {
                    backgroundUrl = uri.toString()
                    binding.userInfoBackgroundImage.setImageURI(uri)
                }
            }
        }
    }

    private fun initToolbar() {
        binding.userInfoToolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.userInfoModify.setOnClickListener {
            viewModel.dispatch(
                UserProfileIntent.SubmitUpdate(
                    nickname = binding.userInfoNickname.text?.toString().orEmpty(),
                    introduction = binding.userInfoIntroduction.text?.toString(),
                    sex = binding.userInfoSex.text?.toString(),
                    birthday = binding.userInfoBirthday.text?.toString(),
                    career = binding.userInfoCareer.text?.toString(),
                    region = binding.userInfoRegion.text?.toString(),
                    school = binding.userInfoSchool.text?.toString(),
                    avatar = avatarUrl,
                    background = backgroundUrl
                )
            )
        }
    }

    private fun initImagePickers() {
        binding.userInfoAvatar.setOnClickListener { openCustomImagePicker("avatar") }
        binding.userInfoBackgroundImage.setOnClickListener { openCustomImagePicker("backgroundImage") }
    }

    private fun openCustomImagePicker(tag: String) {
        val intent = Intent(requireContext(), ImagePickerActivity::class.java)
        intent.putExtra(ImagePickerActivity.EXTRA_TAG, tag)
        pickImageLauncher.launch(intent)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        state.user?.let { u ->
                            avatarUrl = u.avatar
                            backgroundUrl = u.background
                            binding.userInfoNickname.setText(u.nickname ?: "")
                            binding.userInfoPhone.text = u.phone
                            binding.userInfoIntroduction.setText(u.introduction ?: "")
                            binding.userInfoSex.setText(u.sex ?: "")
                            binding.userInfoBirthday.setText(u.birthday ?: "")
                            binding.userInfoCareer.setText(u.career ?: "")
                            binding.userInfoRegion.setText(u.region ?: "")
                            binding.userInfoSchool.setText(u.school ?: "")
                            u.avatar?.let { Glide.with(requireContext()).load(it).into(binding.userInfoAvatar) }
                            u.background?.let { Glide.with(requireContext()).load(it).into(binding.userInfoBackgroundImage) }
                        }
                    }
                }
                launch {
                    viewModel.uiEffect.collect { effect ->
                        when (effect) {
                            is UserProfileEffect.ShowToast -> android.widget.Toast.makeText(
                                requireContext(),
                                effect.message,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            UserProfileEffect.ClosePage -> requireActivity().onBackPressedDispatcher.onBackPressed()
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