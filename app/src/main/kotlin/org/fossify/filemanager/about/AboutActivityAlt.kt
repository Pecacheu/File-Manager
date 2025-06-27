package org.fossify.filemanager.about

import android.content.Intent
import android.content.Intent.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.fossify.commons.R
import org.fossify.commons.activities.BaseComposeActivity
import org.fossify.commons.activities.ContributorsActivity
import org.fossify.commons.activities.DonationActivity
import org.fossify.commons.activities.FAQActivity
import org.fossify.commons.activities.LicenseActivity
import org.fossify.commons.compose.extensions.enableEdgeToEdgeSimple
import org.fossify.commons.compose.theme.AppThemeSurface
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.filemanager.extensions.config

class AboutActivityAlt: BaseComposeActivity() {
	private val appName get() = intent.getStringExtra(APP_NAME)?:""
	private var firstVersionClickTS = 0L
	private var clicksSinceFirstClick = 0

	companion object {
		private const val EASTER_EGG_TIME_LIMIT = 3000L
		private const val EASTER_EGG_REQUIRED_CLICKS = 7
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdgeSimple()
		setContent {
			val resources = LocalContext.current.resources
			AppThemeSurface {
				val showGoogleRelations = remember {!resources.getBoolean(R.bool.hide_google_relations)}
				val showGithubRelations = showGithubRelations()
				val showDonationLinks = remember {resources.getBoolean(R.bool.show_donate_in_about)}
				AboutScreen(goBack = ::finish, helpUsSection = {
					HelpUsSection(onRateUsClick = ::onInviteClick, onInviteClick = ::onInviteClick, onContributorsClick = ::onContributorsClick,
						showDonate = showDonationLinks, onDonateClick = ::onDonateClick, showInvite = showGoogleRelations || showGithubRelations,
						showRateUs = false)
				}, aboutSection = {
					val setupFAQ = showFAQ()
					if(setupFAQ || showGithubRelations) {
						AboutSection(setupFAQ = setupFAQ, setupKnownIssues = showGithubRelations, onFAQClick = ::launchFAQActivity,
							onKnownIssuesClick = ::launchIssueTracker, onKbHintsClick = ::showKbHints)
					}
				}, socialSection = {
					SocialSection(onGithubClick = ::onGithubClick, onRedditClick = ::onRedditClick, onTelegramClick = ::onTelegramClick)
				}) {
					val (versionName, packageName) = getPackageInfo()
					OtherSection(showMoreApps = showGoogleRelations, onMoreAppsClick = ::launchMoreAppsFromUsIntent,
						onPrivacyPolicyClick = ::onPrivacyPolicyClick, onLicenseClick = ::onLicenseClick, versionName = versionName, packageName = packageName,
						onVersionClick = ::onVersionClick)
				}
			}
		}
	}

	private fun getGithubUrl(): String {
		return "https://github.com/Pecacheu/${intent.getStringExtra(APP_REPOSITORY_NAME)}"
	}

	@Composable
	private fun showFAQ() = remember {!(intent.getSerializableExtra(APP_FAQ) as? ArrayList<FAQItem>).isNullOrEmpty()}

	@Composable
	private fun showGithubRelations() = remember {!intent.getStringExtra(APP_REPOSITORY_NAME).isNullOrEmpty()}

	@Composable
	private fun getPackageInfo(): Pair<String, String> {
		var versionName = remember {intent.getStringExtra(APP_VERSION_NAME)?:""}
		val packageName = remember {intent.getStringExtra(APP_PACKAGE_NAME)?:""}
		versionName += " (Chu Edition)"

		val fullVersion = stringResource(R.string.version_placeholder, versionName)
		return Pair(fullVersion, packageName)
	}

	private fun launchFAQActivity() {
		val faqItems = intent.getSerializableExtra(APP_FAQ) as ArrayList<FAQItem>
		Intent(applicationContext, FAQActivity::class.java).apply {
			putExtra(APP_ICON_IDS, intent.getIntegerArrayListExtra(APP_ICON_IDS)?:ArrayList<String>())
			putExtra(APP_LAUNCHER_NAME, intent.getStringExtra(APP_LAUNCHER_NAME)?:"")
			putExtra(APP_FAQ, faqItems)
			startActivity(this)
		}
	}

	private fun launchIssueTracker() {
		launchViewIntent("${getGithubUrl()}/issues?q=is:open+is:issue+label:bug")
	}

	private fun showKbHints() {
		startActivity(Intent(applicationContext, KbHints::class.java))
	}

	private fun onInviteClick() {
		val storeUrl = when {
			resources.getBoolean(R.bool.hide_google_relations) -> getGithubUrl()
			else -> getStoreUrl()
		}
		val text = String.format(getString(R.string.share_text), appName, storeUrl)
		Intent().apply {
			action = ACTION_SEND
			putExtra(EXTRA_SUBJECT, appName)
			putExtra(EXTRA_TEXT, text)
			type = "text/plain"
			startActivity(createChooser(this, getString(R.string.invite_via)))
		}
	}

	private fun onContributorsClick() {
		val intent = Intent(applicationContext, ContributorsActivity::class.java)
		startActivity(intent)
	}

	private fun onDonateClick() {
		startActivity(Intent(applicationContext, DonationActivity::class.java))
	}

	private fun onGithubClick() {launchViewIntent("https://github.com/Pecacheu")}
	private fun onRedditClick() {launchViewIntent("https://www.reddit.com/r/Fossify")}
	private fun onTelegramClick() {launchViewIntent("https://t.me/Fossify")}

	private fun onPrivacyPolicyClick() {
		val appId = config.appId.removeSuffix(".debug").removeSuffix(".pro").removePrefix("org.fossify.")
		val url = "https://www.fossify.org/policy/$appId"
		launchViewIntent(url)
	}

	private fun onLicenseClick() {
		Intent(applicationContext, LicenseActivity::class.java).apply {
			putExtra(APP_ICON_IDS, intent.getIntegerArrayListExtra(APP_ICON_IDS)?:ArrayList<String>())
			putExtra(APP_LAUNCHER_NAME, intent.getStringExtra(APP_LAUNCHER_NAME)?:"")
			putExtra(APP_LICENSES, intent.getLongExtra(APP_LICENSES, 0))
			startActivity(this)
		}
	}

	private fun onVersionClick() {
		if(firstVersionClickTS == 0L) {
			firstVersionClickTS = System.currentTimeMillis()
			Handler(Looper.getMainLooper()).postDelayed({
				firstVersionClickTS = 0L
				clicksSinceFirstClick = 0
			}, EASTER_EGG_TIME_LIMIT)
		}
		clicksSinceFirstClick++
		if(clicksSinceFirstClick >= EASTER_EGG_REQUIRED_CLICKS) {
			toast("⚡ Rai Rai :3 ⚡")
			firstVersionClickTS = 0L
			clicksSinceFirstClick = 0
		}
	}
}