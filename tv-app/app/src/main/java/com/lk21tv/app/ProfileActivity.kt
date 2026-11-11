package com.lk21tv.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Netflix-style "Who's watching?" screen. No login — profiles live on this device only and
 * keep their own Continue Watching history and recommendation algorithm data.
 */
class ProfileActivity : Activity() {

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var rowContainer: LinearLayout
    private lateinit var manageBtn: Button
    private var manageMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
            setPadding(dp(32), dp(24), dp(32), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(26))
        }
        val iconResId = resources.getIdentifier("ic_tv_logo", "drawable", packageName)
        if (iconResId != 0) {
            brandRow.addView(ImageView(this).apply {
                setImageResource(iconResId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) }
            })
        }
        brandRow.addView(TextView(this).apply {
            text = "Citeraloka"
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
        })
        root.addView(brandRow)

        titleView = TextView(this).apply {
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
        }
        root.addView(titleView)

        subtitleView = TextView(this).apply {
            setTextColor(Color.parseColor(MainActivity.TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(28))
        }
        root.addView(subtitleView)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        scroll.addView(rowContainer)
        root.addView(scroll)

        manageBtn = createButton("MANAGE PROFILES", isPrimary = false) {
            manageMode = !manageMode
            renderProfiles()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(30) }
        }
        root.addView(manageBtn)

        setContentView(root)
        renderProfiles()
    }

    override fun onResume() {
        super.onResume()
        renderProfiles()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (manageMode) {
            manageMode = false
            renderProfiles()
        } else {
            super.onBackPressed()
        }
    }

    private fun renderProfiles() {
        rowContainer.removeAllViews()
        val profiles = ProfileManager.getProfiles(this)
        val activeId = ProfileManager.getActiveId(this)

        if (profiles.isEmpty()) manageMode = false

        titleView.text = when {
            manageMode -> "MANAGE PROFILES"
            profiles.isEmpty() -> "CREATE YOUR PROFILE"
            else -> "WHO'S WATCHING?"
        }
        subtitleView.text = when {
            manageMode -> "Select a profile to edit or delete it"
            profiles.isEmpty() -> "No login needed. Profiles only keep your history and recommendations on this device."
            else -> "Each profile has its own Continue Watching and recommendations"
        }

        var focusTarget: View? = null

        profiles.forEach { profile ->
            val card = buildCard(
                label = profile.name,
                fillHex = ProfileManager.colorOf(profile),
                avatarText = ProfileManager.initialOf(profile.name),
                caption = if (manageMode) "EDIT" else null,
                isAddCard = false
            ) { onProfileClicked(profile) }
            rowContainer.addView(card)
            if (profile.id == activeId || focusTarget == null) focusTarget = card
        }

        if (profiles.size < ProfileManager.MAX_PROFILES && !manageMode) {
            val addCard = buildCard(
                label = "ADD PROFILE",
                fillHex = MainActivity.BG_CARD_TEAL,
                avatarText = "+",
                caption = null,
                isAddCard = true
            ) { showProfileDialog(null) }
            rowContainer.addView(addCard)
            if (focusTarget == null) focusTarget = addCard
        }

        manageBtn.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
        manageBtn.text = if (manageMode) "DONE" else "MANAGE PROFILES"

        focusTarget?.post { focusTarget?.requestFocus() }
    }

    private fun onProfileClicked(profile: UserProfile) {
        if (manageMode) {
            showProfileDialog(profile)
        } else {
            ProfileManager.setActive(this, profile.id)
            openHome()
        }
    }

    private fun openHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun buildCard(
        label: String,
        fillHex: String,
        avatarText: String,
        caption: String?,
        isAddCard: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val avatarSize = dp(104)
        val borderHex = if (isAddCard) MainActivity.BORDER_SUBTLE_TEAL else fillHex

        val avatar = TextView(this).apply {
            text = avatarText
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (isAddCard) MainActivity.ACCENT_TEAL else MainActivity.BG_DEEP_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isAddCard) 44f else 42f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedBg(fillHex, borderHex, if (isAddCard) 2 else 0, 14)
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
        }

        val nameView = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MainActivity.TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        }

        val captionView = TextView(this).apply {
            text = caption ?: ""
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MainActivity.TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            letterSpacing = 0.1f
            visibility = if (caption.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isFocusableInTouchMode = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
            }
            addView(avatar)
            addView(nameView)
            addView(captionView)

            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    avatar.background = createRoundedBg(fillHex, "#FFFFFF", 3, 14)
                    nameView.setTextColor(Color.parseColor("#FFFFFF"))
                    v.scaleX = 1.1f
                    v.scaleY = 1.1f
                } else {
                    avatar.background = createRoundedBg(fillHex, borderHex, if (isAddCard) 2 else 0, 14)
                    nameView.setTextColor(Color.parseColor(MainActivity.TEXT_SECONDARY))
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    /** Add (existing == null) or edit/delete a profile. */
    private fun showProfileDialog(existing: UserProfile?) {
        val isEdit = existing != null
        val allProfiles = ProfileManager.getProfiles(this)
        val usedColors = allProfiles.map { it.colorIndex }.toSet()
        var selectedColor = existing?.colorIndex
            ?: ProfileManager.AVATAR_COLORS.indices.firstOrNull { it !in usedColors }
            ?: 0

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(MainActivity.BG_SURFACE_TEAL, MainActivity.ACCENT_TEAL, 2, 12)
            setPadding(dp(26), dp(22), dp(26), dp(22))
        }

        box.addView(TextView(this).apply {
            text = if (isEdit) "EDIT PROFILE" else "ADD PROFILE"
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            setPadding(0, 0, 0, dp(14))
        })

        val preview = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            }
        }
        box.addView(preview)

        val nameInput = EditText(this).apply {
            hint = "Profile name"
            setText(existing?.name ?: "")
            setSelection(text.length)
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        box.addView(nameInput)

        box.addView(TextView(this).apply {
            text = "AVATAR COLOR"
            setTextColor(Color.parseColor(MainActivity.TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, dp(16), 0, dp(8))
        })

        val swatches = mutableListOf<TextView>()
        fun refreshPreview() {
            val hex = ProfileManager.AVATAR_COLORS[selectedColor]
            preview.text = ProfileManager.initialOf(nameInput.text.toString())
            preview.background = createRoundedBg(hex, hex, 0, 14)
            swatches.forEachIndexed { i, sw ->
                val c = ProfileManager.AVATAR_COLORS[i]
                sw.background = if (i == selectedColor) {
                    createRoundedBg(c, "#FFFFFF", 3, 10)
                } else {
                    createRoundedBg(c, c, 0, 10)
                }
            }
        }

        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ProfileManager.AVATAR_COLORS.forEachIndexed { index, _ ->
            val sw = TextView(this).apply {
                isFocusable = true
                isFocusableInTouchMode = false
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(10) }
                setOnFocusChangeListener { v, hasFocus ->
                    v.scaleX = if (hasFocus) 1.15f else 1.0f
                    v.scaleY = if (hasFocus) 1.15f else 1.0f
                }
                setOnClickListener {
                    selectedColor = index
                    refreshPreview()
                }
            }
            swatches.add(sw)
            swatchRow.addView(sw)
        }
        box.addView(swatchRow)

        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshPreview()
            }
        })
        refreshPreview()

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(22), 0, 0)
        }

        if (isEdit && allProfiles.size > 1) {
            btnRow.addView(createButton("DELETE", isPrimary = false) {
                AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("DELETE PROFILE?")
                    .setMessage("${existing!!.name} and its Continue Watching history and recommendations will be removed.")
                    .setPositiveButton("DELETE") { _, _ ->
                        ProfileManager.deleteProfile(this, existing.id)
                        dialog.dismiss()
                        renderProfiles()
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
            })
        }
        btnRow.addView(createButton("CANCEL", isPrimary = false) { dialog.dismiss() })
        btnRow.addView(createButton("SAVE", isPrimary = true) {
            val name = nameInput.text.toString().trim()
            when {
                name.isBlank() -> {
                    Toast.makeText(this, "Enter a profile name", Toast.LENGTH_SHORT).show()
                }
                allProfiles.any { it.id != existing?.id && it.name.equals(name, ignoreCase = true) } -> {
                    Toast.makeText(this, "That name is already used", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    dialog.dismiss()
                    if (existing != null) {
                        ProfileManager.updateProfile(this, existing.id, name, selectedColor)
                        renderProfiles()
                    } else {
                        val wasEmpty = allProfiles.isEmpty()
                        val created = ProfileManager.addProfile(this, name, selectedColor)
                        if (wasEmpty) {
                            ProfileManager.setActive(this, created.id)
                            openHome()
                        } else {
                            renderProfiles()
                        }
                    }
                }
            }
        })
        box.addView(btnRow)

        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun createButton(label: String, isPrimary: Boolean, onClick: () -> Unit): Button {
        val normalBg = if (isPrimary) MainActivity.ACCENT_TEAL_DARK else MainActivity.BG_CARD_TEAL
        val normalBorder = if (isPrimary) MainActivity.ACCENT_TEAL else MainActivity.BORDER_SUBTLE_TEAL
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            isFocusable = true
            isFocusableInTouchMode = false
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(normalBg, normalBorder, 1, 6)
            setPadding(dp(20), dp(9), dp(20), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(10) }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                    v.background = createRoundedBg(MainActivity.ACCENT_TEAL, "#FFFFFF", 2, 6)
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                } else {
                    (v as Button).setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                    v.background = createRoundedBg(normalBg, normalBorder, 1, 6)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedBg(
        fillHex: String,
        strokeHex: String,
        strokeWidthDp: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), Color.parseColor(strokeHex))
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
