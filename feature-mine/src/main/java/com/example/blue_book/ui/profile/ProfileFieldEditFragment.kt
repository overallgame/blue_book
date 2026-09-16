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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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

		/** 已按服务器值初始化过输入框（见 [seeded]） */
		private const val KEY_SEEDED = "profile_field_seeded"

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
		// 重建（旋转、切换深色模式、进程恢复）时已输入的内容由视图状态恢复，
		// 这里必须记住「已经播过种」，否则 ViewModel 回放的服务器值会把用户输入覆盖掉，
		// 用户再点保存就把旧值写回去了（且毫无提示）。
		//
		// 生日页例外：它没有可被覆盖的输入控件（选完即提交），而这页唯一的入口就是
		// 弹出日期选择器——若沿用「已播种」，重建后选择器不再弹出、页面只剩一行提示
		// （保存按钮也是隐藏的），就成了一个没有任何入口的死页。
		seeded = field != FIELD_BIRTHDAY && savedInstanceState?.getBoolean(KEY_SEEDED) == true
		binding.profileFieldToolbar.setNavigationOnClickListener { back() }
		initWindowInsets()
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

	/**
	 * 背景色延展到状态栏后方；工具栏避让状态栏，底部让开系统手势条。
	 *
	 * 本页在 ProfileEditActivity 内，Activity 是全屏延展的、底下没有底部导航，
	 * 所以底部内边距要自己加（加在根布局的 padding 上，底色仍延展到屏幕边缘）。
	 */
	private fun initWindowInsets() {
		ViewCompat.setOnApplyWindowInsetsListener(binding.profileFieldToolbar) { v, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.updatePadding(top = bars.top)
			binding.root.updatePadding(bottom = bars.bottom)
			insets
		}
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
				FIELD_NICKNAME -> UserProfileIntent.UpdateNickname(text)
				FIELD_INTRODUCTION -> UserProfileIntent.UpdateBio(text)
				FIELD_CAREER -> UserProfileIntent.UpdateOccupation(text)
				FIELD_SCHOOL -> UserProfileIntent.UpdateSchool(text)
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
				viewModel.dispatch(UserProfileIntent.UpdateGender(value))
			}
		}
	}

	private fun setupRegion() {
		binding.profileFieldToolbar.title = "地区"
		binding.profileFieldRegionCard.visibility = View.VISIBLE
		binding.profileFieldSave.visibility = View.GONE
		binding.profileFieldRegionList.adapter = ArrayAdapter(requireContext(), R.layout.item_profile_region, REGION_OPTIONS)
		binding.profileFieldRegionList.setOnItemClickListener { _, _, position, _ ->
			viewModel.dispatch(UserProfileIntent.UpdateRegion(REGION_OPTIONS[position]))
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
		val dialog = DatePickerDialog(
			requireContext(),
			{ _, year, month, day ->
				viewModel.dispatch(UserProfileIntent.UpdateBirthday("%d-%02d-%02d".format(year, month + 1, day)))
			},
			cal.get(Calendar.YEAR),
			cal.get(Calendar.MONTH),
			cal.get(Calendar.DAY_OF_MONTH)
		)
		// 生日不允许选今天之后
		dialog.datePicker.maxDate = System.currentTimeMillis()
		dialog.setOnCancelListener { back() }
		dialog.show()
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
							UserProfileEffect.FieldUpdated -> back()
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

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		// 「已播种」要跟着实例状态走，否则重建后服务器值会覆盖用户已输入的内容（见 onViewCreated）
		outState.putBoolean(KEY_SEEDED, seeded)
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
