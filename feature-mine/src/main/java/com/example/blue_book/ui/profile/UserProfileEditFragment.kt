package com.example.blue_book.ui.profile

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
import com.example.blue_book.feature_mine.databinding.UserProfilePageBinding
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
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
		initFieldPickers()
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

	private fun initFieldPickers() {
		// 性别选择器
		binding.userInfoSex.setOnClickListener {
			val options = arrayOf("男", "女", "其他")
			val current = options.indexOfFirst { it == binding.userInfoSex.text.toString() }
			android.app.AlertDialog.Builder(requireContext())
				.setTitle("选择性别")
				.setSingleChoiceItems(options, if (current >= 0) current else -1) { dialog, which ->
					binding.userInfoSex.setText(options[which])
					dialog.dismiss()
				}
				.show()
		}
		// 生日选择器
		binding.userInfoBirthday.setOnClickListener {
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
					binding.userInfoBirthday.setText("%d-%02d-%02d".format(year, month + 1, day))
				},
				cal.get(java.util.Calendar.YEAR),
				cal.get(java.util.Calendar.MONTH),
				cal.get(java.util.Calendar.DAY_OF_MONTH)
			).show()
		}
		// 地区选择器
		binding.userInfoRegion.setOnClickListener {
			val regions = arrayOf("北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "重庆", "其他")
			android.app.AlertDialog.Builder(requireContext())
				.setTitle("选择地区")
				.setItems(regions) { dialog, which ->
					binding.userInfoRegion.setText(regions[which])
					dialog.dismiss()
				}
				.show()
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
							binding.userInfoNickname.setText(u.nickname ?: "")
							binding.userInfoPhone.text = u.phone
							binding.userInfoIntroduction.setText(u.introduction ?: "")
							binding.userInfoSex.setText(u.sex ?: "点击选择")
							binding.userInfoBirthday.setText(u.birthday ?: "点击选择")
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