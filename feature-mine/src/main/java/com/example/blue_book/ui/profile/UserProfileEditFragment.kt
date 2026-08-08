package com.example.blue_book.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.blue_book.feature_mine.databinding.UserProfilePageBinding
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserProfileEditFragment : Fragment(), OnFieldConfirmedListener {

    private var _binding: UserProfilePageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserProfileViewModel by viewModels()
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private var avatarUrl: String? = null
    private var backgroundUrl: String? = null
    private var pendingAvatarUri: String? = null
    private var pendingBackgroundUri: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = UserProfilePageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initActivityResult()
        initToolbar()
        initImagePickers()
        initFieldClickListeners()
        setupImagePreviewOverlays()
        observeViewModel()
        viewModel.dispatch(UserProfileIntent.Init)
    }

    private fun initActivityResult() {
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            val tag = result.data?.getStringExtra("tag")
            when (tag) {
                "avatar" -> {
                    pendingAvatarUri = uri.toString()
                    binding.userInfoAvatar.setImageURI(uri)
                    binding.avatarPreviewOverlay.visibility = View.VISIBLE
                }
                "backgroundImage" -> {
                    pendingBackgroundUri = uri.toString()
                    binding.userInfoBackgroundImage.setImageURI(uri)
                    binding.backgroundPreviewOverlay.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun initToolbar() {
        binding.userInfoToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun initImagePickers() {
        binding.userInfoAvatar.setOnClickListener { openCustomImagePicker("avatar") }
        binding.userInfoBackgroundImage.setOnClickListener { openCustomImagePicker("backgroundImage") }
    }

    private fun initFieldClickListeners() {
        binding.userInfoNicknameRow.setOnClickListener {
            showEditFieldDialog(FieldType.NICKNAME, "修改昵称", binding.userInfoNickname.text.toString())
        }
        binding.userInfoIntroductionRow.setOnClickListener {
            showEditFieldDialog(FieldType.BIO, "修改简介", binding.userInfoIntroduction.text.toString())
        }
        binding.userInfoSexRow.setOnClickListener {
            showEditFieldDialog(FieldType.GENDER, "选择性别", binding.userInfoSex.text.toString())
        }
        binding.userInfoBirthdayRow.setOnClickListener {
            showBirthdayPicker()
        }
        binding.userInfoCareerRow.setOnClickListener {
            showEditFieldDialog(FieldType.OCCUPATION, "修改职业", binding.userInfoCareer.text.toString())
        }
        binding.userInfoRegionRow.setOnClickListener {
            showEditFieldDialog(FieldType.REGION, "选择地区", binding.userInfoRegion.text.toString())
        }
        binding.userInfoSchoolRow.setOnClickListener {
            showEditFieldDialog(FieldType.SCHOOL, "修改学校", binding.userInfoSchool.text.toString())
        }
    }

    private fun showEditFieldDialog(fieldType: FieldType, title: String, currentValue: String) {
        val dialog = EditFieldDialog.newInstance(fieldType, title, currentValue)
        dialog.show(childFragmentManager, "edit_field")
    }

    private fun showBirthdayPicker() {
        val cal = java.util.Calendar.getInstance()
        try {
            binding.userInfoBirthday.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                val parts = it.split("-")
                if (parts.size == 3) {
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
            }
        } catch (_: Exception) {}
        android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val formatted = "%d-%02d-%02d".format(year, month + 1, day)
                viewModel.dispatch(UserProfileIntent.UpdateBirthday(formatted))
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    override fun onFieldConfirmed(fieldType: FieldType, value: String) {
        when (fieldType) {
            FieldType.NICKNAME -> viewModel.dispatch(UserProfileIntent.UpdateNickname(value))
            FieldType.BIO -> viewModel.dispatch(UserProfileIntent.UpdateBio(value))
            FieldType.GENDER -> viewModel.dispatch(UserProfileIntent.UpdateGender(value))
            FieldType.OCCUPATION -> viewModel.dispatch(UserProfileIntent.UpdateOccupation(value))
            FieldType.REGION -> viewModel.dispatch(UserProfileIntent.UpdateRegion(value))
            FieldType.SCHOOL -> viewModel.dispatch(UserProfileIntent.UpdateSchool(value))
        }
    }

    private fun setupImagePreviewOverlays() {
        binding.avatarConfirmBtn.setOnClickListener {
            pendingAvatarUri?.let { uri ->
                viewModel.dispatch(UserProfileIntent.UploadAvatar(uri))
            }
        }
        binding.avatarCancelBtn.setOnClickListener {
            binding.avatarPreviewOverlay.visibility = View.GONE
            pendingAvatarUri = null
            avatarUrl?.let { Glide.with(requireContext()).load(it).into(binding.userInfoAvatar) }
            viewModel.dispatch(UserProfileIntent.CancelAvatarPreview)
        }

        binding.backgroundConfirmBtn.setOnClickListener {
            pendingBackgroundUri?.let { uri ->
                viewModel.dispatch(UserProfileIntent.UploadBackground(uri))
            }
        }
        binding.backgroundCancelBtn.setOnClickListener {
            binding.backgroundPreviewOverlay.visibility = View.GONE
            pendingBackgroundUri = null
            backgroundUrl?.let { Glide.with(requireContext()).load(it).into(binding.userInfoBackgroundImage) }
            viewModel.dispatch(UserProfileIntent.CancelBackgroundPreview)
        }
    }

    private fun openCustomImagePicker(tag: String) {
        val intent = TheRouter.build(RoutePath.IMAGE_PICKER)
            .withString("tag", tag)
            .createIntent(requireContext())
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
                            binding.userInfoNickname.text = u.nickname ?: ""
                            binding.userInfoPhone.text = u.phone
                            binding.userInfoIntroduction.text = u.introduction ?: ""
                            binding.userInfoSex.text = u.sex ?: "点击选择"
                            binding.userInfoBirthday.text = u.birthday ?: "点击选择"
                            binding.userInfoCareer.text = u.career ?: ""
                            binding.userInfoRegion.text = u.region ?: ""
                            binding.userInfoSchool.text = u.school ?: ""
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

                            UserProfileEffect.FieldUpdated -> {
                                childFragmentManager.findFragmentByTag("edit_field")?.let { fragment ->
                                    (fragment as? DialogFragment)?.dismiss()
                                }
                                binding.avatarPreviewOverlay.visibility = View.GONE
                                binding.backgroundPreviewOverlay.visibility = View.GONE
                                pendingAvatarUri = null
                                pendingBackgroundUri = null
                            }
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
