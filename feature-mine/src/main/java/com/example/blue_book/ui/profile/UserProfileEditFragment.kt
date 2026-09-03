package com.example.blue_book.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.blue_book.feature_mine.R
import com.example.blue_book.feature_mine.databinding.UserProfilePageBinding
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.mine.MineActivity
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserProfileEditFragment : Fragment() {

	private var _binding: UserProfilePageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: UserProfileViewModel by viewModels()
	private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = UserProfilePageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initActivityResult()
		initToolbar()
		initImagePickers()
		initFieldNavigation()
		observeViewModel()
	}

	override fun onResume() {
		super.onResume()
		// 从字段编辑页返回后刷新展示值
		viewModel.dispatch(UserProfileIntent.Refresh)
	}

	private fun initActivityResult() {
		pickImageLauncher = registerForActivityResult(
			ActivityResultContracts.StartActivityForResult()
		) { result ->
			if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
			val uri = result.data?.data ?: return@registerForActivityResult
			when (result.data?.getStringExtra("tag")) {
				"avatar" -> {
					binding.userInfoAvatar.setImageURI(uri)
					viewModel.dispatch(UserProfileIntent.UpdateImages(avatar = uri.toString()))
				}
				"backgroundImage" -> {
					binding.userInfoBackgroundImage.setImageURI(uri)
					viewModel.dispatch(UserProfileIntent.UpdateImages(background = uri.toString()))
				}
			}
		}
	}

	private fun initToolbar() {
		binding.userInfoToolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
	}

	private fun initImagePickers() {
		// 头像与背景图沿用原有图片选择器逻辑，选择后即时保存
		binding.userInfoAvatar.setOnClickListener { openCustomImagePicker("avatar") }
		binding.userInfoRowBackground.setOnClickListener { openCustomImagePicker("backgroundImage") }
	}

	private fun initFieldNavigation() {
		binding.userInfoRowNickname.setOnClickListener {
			navigateToField(ProfileFieldEditFragment.FIELD_NICKNAME)
		}
		binding.userInfoRowIntroduction.setOnClickListener {
			navigateToField(ProfileFieldEditFragment.FIELD_INTRODUCTION)
		}
		binding.userInfoRowSex.setOnClickListener {
			navigateToField(ProfileFieldEditFragment.FIELD_SEX)
		}
		binding.userInfoRowBirthday.setOnClickListener {
			navigateToField(ProfileFieldEditFragment.FIELD_BIRTHDAY)
		}
		binding.userInfoRowRegion.setOnClickListener {
			navigateToField(ProfileFieldEditFragment.FIELD_REGION)
		}
		binding.userInfoRowCareer.setOnClickListener {
			navigateToField(ProfileFieldEditFragment.FIELD_CAREER)
		}
		binding.userInfoRowSchool.setOnClickListener {
			navigateToField(ProfileFieldEditFragment.FIELD_SCHOOL)
		}
		binding.userInfoRowXhsId.setOnClickListener {
			Toast.makeText(requireContext(), "小红书号暂不支持修改", Toast.LENGTH_SHORT).show()
		}
	}

	private fun navigateToField(field: String) {
		(requireActivity() as MineActivity).navigateToProfileFieldEdit(field)
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
							binding.userInfoPhone.text = u.phone
							bindValue(binding.userInfoNickname, u.nickname, "未填")
							bindValue(binding.userInfoIntroduction, u.introduction, "未填")
							bindValue(binding.userInfoSex, u.sex, "选择性别")
							bindValue(binding.userInfoBirthday, u.birthday, "选择生日")
							bindValue(binding.userInfoRegion, u.region, "选择所在的地区")
							bindValue(binding.userInfoCareer, u.career, "选择职业")
							bindValue(binding.userInfoSchool, u.school, "选择学校")
							u.avatar?.let { Glide.with(requireContext()).load(it).into(binding.userInfoAvatar) }
							u.background?.let { Glide.with(requireContext()).load(it).into(binding.userInfoBackgroundImage) }
						}
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is UserProfileEffect.ShowToast -> Toast.makeText(
								requireContext(),
								effect.message,
								Toast.LENGTH_SHORT
							).show()
							UserProfileEffect.ClosePage -> requireActivity().onBackPressedDispatcher.onBackPressed()
						}
					}
				}
			}
		}
	}

	/** 有值显示正常色，空值显示灰色占位文案 */
	private fun bindValue(view: TextView, value: String?, placeholder: String) {
		if (value.isNullOrBlank()) {
			view.text = placeholder
			view.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_placeholder))
		} else {
			view.text = value
			view.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_value))
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
