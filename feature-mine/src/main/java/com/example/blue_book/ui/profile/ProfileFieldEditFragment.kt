package com.example.blue_book.ui.profile

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.blue_book.feature_mine.R
import com.example.blue_book.feature_mine.databinding.FragmentProfileFieldEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 通用单字段编辑页：根据 [ARG_FIELD] 决定编辑项，
 * 文本类字段走输入框，性别/地区/生日走选择控件，保存成功后自动返回
 */
@AndroidEntryPoint
class ProfileFieldEditFragment : Fragment() {

	companion object {
		const val ARG_FIELD = "arg_field"
		const val FIELD_NICKNAME = "nickname"
		const val FIELD_INTRODUCTION = "introduction"
		const val FIELD_SEX = "sex"
		const val FIELD_BIRTHDAY = "birthday"
		const val FIELD_REGION = "region"
		const val FIELD_CAREER = "career"
		const val FIELD_SCHOOL = "school"

		private const val NICKNAME_MAX_LENGTH = 24
		private const val INTRODUCTION_MAX_LENGTH = 100
		private const val CAREER_MAX_LENGTH = 20
		private const val SCHOOL_MAX_LENGTH = 30

		private val REGION_OPTIONS = arrayOf("北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "重庆", "其他")
	}

	private var _binding: FragmentProfileFieldEditBinding? = null
	private val binding get() = _binding!!
	private val viewModel: UserProfileViewModel by viewModels()
	private val field: String get() = requireArguments().getString(ARG_FIELD).orEmpty()
	private var seeded = false

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentProfileFieldEditBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.profileFieldToolbar.setNavigationOnClickListener { back() }
		when (field) {
			FIELD_NICKNAME -> setupTextInput("修改名字", NICKNAME_MAX_LENGTH, singleLine = true, hint = "好名字可以让人更容易记住你")
			FIELD_INTRODUCTION -> setupTextInput("修改简介", INTRODUCTION_MAX_LENGTH, singleLine = false, hint = "介绍一下自己吧")
			FIELD_CAREER -> setupTextInput("修改职业", CAREER_MAX_LENGTH, singleLine = true, hint = "填写你的职业")
			FIELD_SCHOOL -> setupTextInput("修改学校", SCHOOL_MAX_LENGTH, singleLine = true, hint = "填写你的学校")
			FIELD_SEX -> setupGender()
			FIELD_REGION -> setupRegion()
			FIELD_BIRTHDAY -> setupBirthday()
			else -> back()
		}
		observeViewModel()
		viewModel.dispatch(UserProfileIntent.Init)
	}

	private fun setupTextInput(title: String, maxLength: Int, singleLine: Boolean, hint: String) {
		binding.profileFieldToolbar.title = title
		binding.profileFieldInputCard.visibility = View.VISIBLE
		binding.profileFieldInput.hint = hint
		binding.profileFieldInput.filters = arrayOf(InputFilter.LengthFilter(maxLength))
		if (singleLine) {
			binding.profileFieldInput.inputType = InputType.TYPE_CLASS_TEXT
			binding.profileFieldInput.setSingleLine(true)
		} else {
			binding.profileFieldInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
			binding.profileFieldInput.minLines = 3
			binding.profileFieldInput.maxLines = 6
			binding.profileFieldInput.gravity = Gravity.TOP or Gravity.START
		}
		binding.profileFieldInput.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: android.text.Editable?) {
				binding.profileFieldCount.text = "${s?.length ?: 0}/$maxLength"
			}
		})
		binding.profileFieldSave.setOnClickListener {
			val text = binding.profileFieldInput.text?.toString()?.trim().orEmpty()
			if (field == FIELD_NICKNAME && text.isEmpty()) {
				Toast.makeText(requireContext(), "名字不能为空", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			val intent = when (field) {
				FIELD_NICKNAME -> UserProfileIntent.SubmitUpdate(nickname = text)
				FIELD_INTRODUCTION -> UserProfileIntent.SubmitUpdate(introduction = text)
				FIELD_CAREER -> UserProfileIntent.SubmitUpdate(career = text)
				FIELD_SCHOOL -> UserProfileIntent.SubmitUpdate(school = text)
				else -> null
			}
			intent?.let(viewModel::dispatch)
		}
	}

	private fun setupGender() {
		binding.profileFieldToolbar.title = "性别"
		binding.profileFieldGenderCard.visibility = View.VISIBLE
		binding.profileFieldSave.setOnClickListener {
			val value = when (binding.profileFieldGenderCard.checkedRadioButtonId) {
				R.id.profileField_gender_male -> "男"
				R.id.profileField_gender_female -> "女"
				R.id.profileField_gender_other -> "其他"
				else -> null
			}
			if (value == null) {
				Toast.makeText(requireContext(), "请选择性别", Toast.LENGTH_SHORT).show()
			} else {
				viewModel.dispatch(UserProfileIntent.SubmitUpdate(sex = value))
			}
		}
	}

	private fun setupRegion() {
		binding.profileFieldToolbar.title = "地区"
		binding.profileFieldRegionCard.visibility = View.VISIBLE
		binding.profileFieldSave.visibility = View.GONE
		binding.profileFieldRegionList.adapter = ArrayAdapter(requireContext(), R.layout.item_profile_region, REGION_OPTIONS)
		binding.profileFieldRegionList.setOnItemClickListener { _, _, position, _ ->
			viewModel.dispatch(UserProfileIntent.SubmitUpdate(region = REGION_OPTIONS[position]))
		}
	}

	private fun setupBirthday() {
		binding.profileFieldToolbar.title = "修改生日"
		binding.profileFieldBirthdayHint.visibility = View.VISIBLE
		binding.profileFieldSave.visibility = View.GONE
	}

	private fun showBirthdayDialog(birthday: String?) {
		val cal = Calendar.getInstance()
		if (!TextUtils.isEmpty(birthday)) {
			val parts = birthday!!.split("-")
			if (parts.size == 3) {
				try {
					cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
				} catch (_: Exception) {
				}
			}
		}
		DatePickerDialog(
			requireContext(),
			{ _, year, month, day ->
				viewModel.dispatch(UserProfileIntent.SubmitUpdate(birthday = "%d-%02d-%02d".format(year, month + 1, day)))
			},
			cal.get(Calendar.YEAR),
			cal.get(Calendar.MONTH),
			cal.get(Calendar.DAY_OF_MONTH)
		).apply {
			setOnCancelListener { back() }
		}.show()
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						val user = state.user ?: return@collect
						if (seeded) return@collect
						seeded = true
						when (field) {
							FIELD_NICKNAME -> seedInput(user.nickname)
							FIELD_INTRODUCTION -> seedInput(user.introduction)
							FIELD_CAREER -> seedInput(user.career)
							FIELD_SCHOOL -> seedInput(user.school)
							FIELD_SEX -> when (user.sex) {
								"男" -> binding.profileFieldGenderMale.isChecked = true
								"女" -> binding.profileFieldGenderFemale.isChecked = true
								"其他" -> binding.profileFieldGenderOther.isChecked = true
							}
							FIELD_BIRTHDAY -> showBirthdayDialog(user.birthday)
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
							UserProfileEffect.ClosePage -> back()
						}
					}
				}
			}
		}
	}

	private fun seedInput(value: String?) {
		binding.profileFieldInput.setText(value.orEmpty())
		binding.profileFieldInput.setSelection(binding.profileFieldInput.text?.length ?: 0)
	}

	private fun back() {
		requireActivity().onBackPressedDispatcher.onBackPressed()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
