package com.betterdeepseek.app.github

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.betterdeepseek.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class GitHubAuthDialog(
    private val activity: Activity,
    private val gitHubService: GitHubService,
    private val onSettingsChanged: () -> Unit
) {
    private val prefs = activity.getSharedPreferences("bds_github_prefs", Context.MODE_PRIVATE)
    private val mainPrefs = activity.getSharedPreferences("bds_preferences", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var activeDialog: BottomSheetDialog? = null

    companion object {
        const val KEY_GITHUB_TOKEN = "githubToken"
        const val KEY_GITHUB_USER = "github_user_login"
        const val KEY_GITHUB_USER_NAME = "github_user_name"
        const val KEY_GITHUB_AVATAR = "github_user_avatar"
        const val KEY_TARGET_REPO = "github_target_repo"
        const val KEY_TARGET_BRANCH = "github_target_branch"
        const val KEY_AGENT_WRITE_ENABLED = "github_agent_write_enabled"
        const val KEY_PROMPT_INJECTION_ENABLED = "github_prompt_injection_enabled"
    }

    fun show() {
        activeDialog?.dismiss()
        val dialog = BottomSheetDialog(activity)
        activeDialog = dialog

        val token = getToken()
        val layout = createMainLayout(dialog, token)
        dialog.setContentView(layout)
        dialog.show()
    }

    private fun getToken(): String? {
        return prefs.getString(KEY_GITHUB_TOKEN, null)
            ?: mainPrefs.getString(KEY_GITHUB_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    private fun saveToken(token: String?) {
        if (token != null) {
            prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
            mainPrefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
        } else {
            prefs.edit().remove(KEY_GITHUB_TOKEN).remove(KEY_GITHUB_USER).remove(KEY_GITHUB_USER_NAME).apply()
            mainPrefs.edit().remove(KEY_GITHUB_TOKEN).apply()
        }
    }

    private fun createMainLayout(dialog: BottomSheetDialog, token: String?): View {
        val scrollView = ScrollView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isFillViewport = true
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }

        // Header Title with Octocat Icon
        val headerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        val iconView = ImageView(activity).apply {
            setImageResource(R.drawable.ic_github)
            setColorFilter(Color.parseColor("#4B5563"))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(12)
            }
        }

        val titleView = TextView(activity).apply {
            text = "افزونه گیت‌هاب ایجنت (GitHub Agent Plugin)"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
        }

        headerLayout.addView(iconView)
        headerLayout.addView(titleView)
        container.addView(headerLayout)

        if (token.isNullOrBlank()) {
            // Unauthenticated view
            buildUnauthenticatedView(container, dialog)
        } else {
            // Authenticated view
            buildAuthenticatedView(container, dialog, token)
        }

        scrollView.addView(container)
        return scrollView
    }

    private fun buildUnauthenticatedView(container: LinearLayout, dialog: BottomSheetDialog) {
        val descView = TextView(activity).apply {
            text = "با اتصال اکانت گیت‌هاب، مدل دیپ‌سیک به طور خودکار به عنوان یک کدینگ ایجنت عمل کرده و توانایی خواندن فایل‌ها، ایجاد تغییرات، کامیت مستقیم و ساخت PR را روی مخازن شما خواهد داشت."
            textSize = 14f
            setTextColor(Color.parseColor("#4B5563"))
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(descView)

        // Login with GitHub Button (Device Flow)
        val loginBtn = MaterialButton(activity).apply {
            text = "ورود با اکانت گیت‌هاب (GitHub Login)"
            setIconResource(R.drawable.ic_github)
            setBackgroundColor(Color.parseColor("#24292F"))
            setTextColor(Color.WHITE)
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener {
                startDeviceFlow(dialog)
            }
        }
        container.addView(loginBtn)

        // Divider with OR
        val orLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val line1 = View(activity).apply {
            setBackgroundColor(Color.parseColor("#E5E7EB"))
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }
        val orText = TextView(activity).apply {
            text = "یا با توکن شخصی (PAT)"
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(12), 0, dp(12), 0)
        }
        val line2 = View(activity).apply {
            setBackgroundColor(Color.parseColor("#E5E7EB"))
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }
        orLayout.addView(line1)
        orLayout.addView(orText)
        orLayout.addView(line2)
        container.addView(orLayout)

        // Manual PAT input Card
        val patCard = MaterialCardView(activity).apply {
            strokeColor = Color.parseColor("#E5E7EB")
            strokeWidth = dp(1)
            cardElevation = 0f
            radius = dp(10).toFloat()
            setCardBackgroundColor(Color.parseColor("#F9FAFB"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }

        val patLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val patInputLayout = TextInputLayout(activity).apply {
            hint = "Personal Access Token (ghp_...)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
        }
        val patEditText = TextInputEditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 14f
        }
        patInputLayout.addView(patEditText)
        patLayout.addView(patInputLayout)

        val connectPatBtn = MaterialButton(activity).apply {
            text = "تست و ذخیره توکن"
            setBackgroundColor(Color.parseColor("#0969DA"))
            cornerRadius = dp(8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(8)
            }
            setOnClickListener {
                val inputToken = patEditText.text?.toString()?.trim().orEmpty()
                if (inputToken.isEmpty()) {
                    Toast.makeText(activity, "لطفاً توکن را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                testAndSaveToken(inputToken, dialog)
            }
        }
        patLayout.addView(connectPatBtn)
        patCard.addView(patLayout)
        container.addView(patCard)
    }

    private fun buildAuthenticatedView(container: LinearLayout, dialog: BottomSheetDialog, token: String) {
        val userLogin = prefs.getString(KEY_GITHUB_USER, "") ?: ""
        val userName = prefs.getString(KEY_GITHUB_USER_NAME, "") ?: ""

        // User profile status card
        val profileCard = MaterialCardView(activity).apply {
            strokeColor = Color.parseColor("#BBF7D0")
            strokeWidth = dp(1)
            cardElevation = 0f
            radius = dp(10).toFloat()
            setCardBackgroundColor(Color.parseColor("#F0FDF4"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }

        val profileLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val octocatIcon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_github)
            setColorFilter(Color.parseColor("#15803D"))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginEnd = dp(12)
            }
        }

        val userDetailsLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(activity).apply {
            text = if (userLogin.isNotEmpty()) "متصل به اکانت @$userLogin" else "متصل به گیت‌هاب"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#166534"))
        }

        val scopeView = TextView(activity).apply {
            text = "دسترسی ایجنت: خواندن، ویرایش، کامیت و ساخت PR فعال است"
            textSize = 12f
            setTextColor(Color.parseColor("#15803D"))
        }

        userDetailsLayout.addView(nameView)
        userDetailsLayout.addView(scopeView)
        profileLayout.addView(octocatIcon)
        profileLayout.addView(userDetailsLayout)
        profileCard.addView(profileLayout)
        container.addView(profileCard)

        // Target Repository Section
        val targetRepoCard = MaterialCardView(activity).apply {
            strokeColor = Color.parseColor("#E5E7EB")
            strokeWidth = dp(1)
            cardElevation = 0f
            radius = dp(10).toFloat()
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }

        val targetLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val targetTitle = TextView(activity).apply {
            text = "تنظیمات مخزن فعال ایجنت (Target Repository)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, 0, 0, dp(8))
        }
        targetLayout.addView(targetTitle)

        val currentRepo = prefs.getString(KEY_TARGET_REPO, "") ?: ""
        val currentBranch = prefs.getString(KEY_TARGET_BRANCH, "main") ?: "main"

        val repoInputLayout = TextInputLayout(activity).apply {
            hint = "مخزن هدف (مثال: owner/repo)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
        }
        val repoEditText = TextInputEditText(activity).apply {
            setText(currentRepo)
            textSize = 14f
        }
        repoInputLayout.addView(repoEditText)
        targetLayout.addView(repoInputLayout)

        val branchInputLayout = TextInputLayout(activity).apply {
            hint = "شاخه کاری (Branch)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        val branchEditText = TextInputEditText(activity).apply {
            setText(currentBranch)
            textSize = 14f
        }
        branchInputLayout.addView(branchEditText)
        targetLayout.addView(branchInputLayout)

        // Switch: Enable Agent Write Access
        val isWriteEnabled = prefs.getBoolean(KEY_AGENT_WRITE_ENABLED, true)
        val writeSwitch = SwitchMaterial(activity).apply {
            text = "مجوز ایجاد تغییر و کامیت برای ایجنت"
            isChecked = isWriteEnabled
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        targetLayout.addView(writeSwitch)

        // Switch: Enable Prompt Injection
        val isPromptEnabled = prefs.getBoolean(KEY_PROMPT_INJECTION_ENABLED, true)
        val promptSwitch = SwitchMaterial(activity).apply {
            text = "تزریق خودکار اطلاعات دسترسی به مدل دیپ‌سیک"
            isChecked = isPromptEnabled
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        }
        targetLayout.addView(promptSwitch)

        // Save Target Repo Button
        val saveRepoBtn = MaterialButton(activity).apply {
            text = "ذخیره تنظیمات مخزن و ایجنت"
            setBackgroundColor(Color.parseColor("#0969DA"))
            cornerRadius = dp(8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                topMargin = dp(12)
            }
            setOnClickListener {
                val repo = repoEditText.text?.toString()?.trim().orEmpty()
                val branch = branchEditText.text?.toString()?.trim().orEmpty().ifEmpty { "main" }
                prefs.edit()
                    .putString(KEY_TARGET_REPO, repo)
                    .putString(KEY_TARGET_BRANCH, branch)
                    .putBoolean(KEY_AGENT_WRITE_ENABLED, writeSwitch.isChecked)
                    .putBoolean(KEY_PROMPT_INJECTION_ENABLED, promptSwitch.isChecked)
                    .apply()
                Toast.makeText(activity, "تنظیمات ایجنت ذخیره شد", Toast.LENGTH_SHORT).show()
                onSettingsChanged()
            }
        }
        targetLayout.addView(saveRepoBtn)
        targetRepoCard.addView(targetLayout)
        container.addView(targetRepoCard)

        // Action Buttons Row: Preview Prompt & Logout
        val actionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val previewBtn = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "پیش‌نمایش پرامپت ایجنت"
            setTextColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(8)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                showPromptPreview(userLogin, repoEditText.text?.toString()?.trim(), branchEditText.text?.toString()?.trim() ?: "main")
            }
        }

        val logoutBtn = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "خروج از حساب"
            setTextColor(Color.parseColor("#DC2626"))
            strokeColor = ContextCompat.getColorStateList(activity, android.R.color.holo_red_light)
            cornerRadius = dp(8)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
            setOnClickListener {
                saveToken(null)
                Toast.makeText(activity, "از حساب گیت‌هاب خارج شدید", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onSettingsChanged()
            }
        }

        actionsRow.addView(previewBtn)
        actionsRow.addView(logoutBtn)
        container.addView(actionsRow)
    }

    private fun showPromptPreview(user: String, repo: String?, branch: String) {
        val prompt = GitHubAgentPrompt.buildAgentSystemPrompt(user, repo, branch, true)
        MaterialAlertDialogBuilder(activity)
            .setTitle("دستورالعمل تزریقی به مدل دیپ‌سیک")
            .setMessage(prompt)
            .setPositiveButton("بستن", null)
            .setNeutralButton("کپی متن") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Prompt", prompt))
                Toast.makeText(activity, "پرامپت کپی شد", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun testAndSaveToken(token: String, dialog: BottomSheetDialog) {
        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("بررسی توکن گیت‌هاب...")
            .setMessage("در حال اتصال به GitHub API و دریافت اطلاعات کاربری")
            .setCancelable(false)
            .show()

        gitHubService.fetchUserProfile(token) { result ->
            activity.runOnUiThread {
                progressDialog.dismiss()
                result.onSuccess { user ->
                    saveToken(token)
                    prefs.edit()
                        .putString(KEY_GITHUB_USER, user.login)
                        .putString(KEY_GITHUB_USER_NAME, user.name ?: user.login)
                        .putString(KEY_GITHUB_AVATAR, user.avatarUrl ?: "")
                        .apply()
                    Toast.makeText(activity, "اتصال با موفقیت برقرار شد (@${user.login})", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    onSettingsChanged()
                }.onFailure { err ->
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("خطا در اتصال")
                        .setMessage(err.message ?: "توکن نامعتبر است یا مشکلی در ارتباط وجود دارد.")
                        .setPositiveButton("تلاش مجدد", null)
                        .show()
                }
            }
        }
    }

    private fun startDeviceFlow(parentDialog: BottomSheetDialog) {
        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle("در حال ارتباط با گیت‌هاب...")
            .setMessage("دریافت کد احراز هویت Device Flow")
            .setCancelable(false)
            .show()

        gitHubService.startDeviceCodeFlow { result ->
            activity.runOnUiThread {
                progress.dismiss()
                result.onSuccess { deviceCode ->
                    showDeviceCodeApprovalDialog(deviceCode, parentDialog)
                }.onFailure { err ->
                    Toast.makeText(activity, "خطا در برقراری ارتباط: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDeviceCodeApprovalDialog(codeResp: DeviceCodeResponse, parentDialog: BottomSheetDialog) {
        var isDismissed = false

        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val infoText = TextView(activity).apply {
            text = "کد زیر را کپی کرده و در صفحه گیت‌هاب وارد کنید تا مجوز به ایجنت داده شود:"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#374151"))
            setPadding(0, 0, 0, dp(12))
        }
        view.addView(infoText)

        // Big user code display
        val codeView = TextView(activity).apply {
            text = codeResp.userCode
            textSize = 28f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0969DA"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#F3F4F6"))
        }
        view.addView(codeView)

        val copyAndOpenBtn = MaterialButton(activity).apply {
            text = "کپی کد و باز کردن صفحه تایید گیت‌هاب"
            setBackgroundColor(Color.parseColor("#24292F"))
            setIconResource(R.drawable.ic_github)
            cornerRadius = dp(8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
                topMargin = dp(16)
            }
            setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("GitHub Code", codeResp.userCode))
                Toast.makeText(activity, "کد کپی شد: ${codeResp.userCode}", Toast.LENGTH_SHORT).show()

                // Open verification URL
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(codeResp.verificationUri))
                activity.startActivity(intent)
            }
        }
        view.addView(copyAndOpenBtn)

        val statusText = TextView(activity).apply {
            text = "در حال انتظار برای تایید شما در گیت‌هاب..."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(12), 0, 0)
        }
        view.addView(statusText)

        val codeDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("تایید لاگین گیت‌هاب")
            .setView(view)
            .setNegativeButton("انصراف") { d, _ ->
                isDismissed = true
                stopPolling()
                d.dismiss()
            }
            .setOnDismissListener {
                isDismissed = true
                stopPolling()
            }
            .show()

        // Start polling
        val pollIntervalMs = (codeResp.interval.coerceAtLeast(5) * 1000).toLong()
        val pollRunnable = object : Runnable {
            override fun run() {
                if (isDismissed) return
                gitHubService.pollDeviceToken(deviceCode = codeResp.deviceCode) { pollResult ->
                    activity.runOnUiThread {
                        if (isDismissed) return@runOnUiThread
                        pollResult.onSuccess { token ->
                            stopPolling()
                            codeDialog.dismiss()
                            testAndSaveToken(token, parentDialog)
                        }.onFailure {
                            // Continue polling unless error indicates terminal failure
                            handler.postDelayed(this, pollIntervalMs)
                        }
                    }
                }
            }
        }
        pollingRunnable = pollRunnable
        handler.postDelayed(pollRunnable, pollIntervalMs)
    }

    private fun stopPolling() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics).toInt()
}
