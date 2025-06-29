package org.fossify.filemanager.about

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.commons.R
import org.fossify.commons.compose.extensions.MyDevices
import org.fossify.commons.compose.lists.SimpleColumnScaffold
import org.fossify.commons.compose.settings.SettingsGroup
import org.fossify.commons.compose.settings.SettingsHorizontalDivider
import org.fossify.commons.compose.settings.SettingsListItem
import org.fossify.commons.compose.settings.SettingsTitleTextComponent
import org.fossify.commons.compose.theme.AppThemeSurface

private val titleStartPadding = Modifier.padding(start = 40.dp)

@Composable
internal fun AboutScreen(goBack: ()->Unit, helpUsSection: @Composable ()->Unit, aboutSection: @Composable ()->Unit, socialSection: @Composable ()->Unit,
	otherSection: @Composable ()->Unit) {
	SimpleColumnScaffold(title = stringResource(id = R.string.about), goBack = goBack) {
		aboutSection()
		helpUsSection()
		socialSection()
		otherSection()
	}
}

@Composable
internal fun HelpUsSection(onRateUsClick: ()->Unit, onInviteClick: ()->Unit, onContributorsClick: ()->Unit, showRateUs: Boolean, showInvite: Boolean,
	showDonate: Boolean, onDonateClick: ()->Unit) {
	SettingsGroup(title = {
		SettingsTitleTextComponent(text = stringResource(id = R.string.help_us), modifier = titleStartPadding)
	}) {
		if(showRateUs) TwoLinerTextItem(text = stringResource(id = R.string.rate_this_app), icon = R.drawable.ic_star_outline_vector, click = onRateUsClick)
		if(showInvite) TwoLinerTextItem(text = stringResource(id = R.string.invite_friends), icon = R.drawable.ic_share_outline_vector, click = onInviteClick)
		TwoLinerTextItem(click = onContributorsClick, text = stringResource(id = R.string.contributors), icon = R.drawable.ic_groups_outline_vector)
		if(showDonate) TwoLinerTextItem(click = onDonateClick, text = stringResource(id = R.string.donate_to_fossify),
			icon = R.drawable.ic_donate_outline_vector)
		SettingsHorizontalDivider()
	}
}

@Composable
internal fun OtherSection(showMoreApps: Boolean, onMoreAppsClick: ()->Unit, onPrivacyPolicyClick: ()->Unit,
	onLicenseClick: ()->Unit, versionName: String, packageName: String, onVersionClick: ()->Unit) {
	SettingsGroup(title = {
		SettingsTitleTextComponent(text = stringResource(id = R.string.other), modifier = titleStartPadding)
	}) {
		if(showMoreApps) TwoLinerTextItem(click = onMoreAppsClick, text = stringResource(id = R.string.more_fossify_apps), icon = R.drawable.ic_apps_vector)
		TwoLinerTextItem(click = onPrivacyPolicyClick, text = stringResource(id = R.string.privacy_policy), icon = R.drawable.ic_policy_outline_vector)
		TwoLinerTextItem(click = onLicenseClick, text = stringResource(id = R.string.third_party_licences), icon = R.drawable.ic_article_outline_vector)
		SettingsListItem(tint = MaterialTheme.colorScheme.onSurface, click = onVersionClick, text = versionName, description = packageName,
			icon = R.drawable.ic_info_outline_vector, maxLines = 2, overflow = TextOverflow.Ellipsis)
		SettingsHorizontalDivider()
	}
}

@Composable
internal fun AboutSection(setupFAQ: Boolean, setupKnownIssues: Boolean, onFAQClick: ()->Unit, onKnownIssuesClick: ()->Unit, onKbHintsClick: ()->Unit) {
	SettingsGroup(title = {
		SettingsTitleTextComponent(text = stringResource(id = R.string.support), modifier = titleStartPadding)
	}) {
		TwoLinerTextItem(click = onKbHintsClick, text = stringResource(id = org.fossify.filemanager.R.string.kb_hints),
			icon = R.drawable.ic_copy_vector)
		if(setupFAQ) TwoLinerTextItem(click = onFAQClick, text = stringResource(id = R.string.frequently_asked_questions),
			icon = R.drawable.ic_help_outline_vector)
		if(setupKnownIssues) TwoLinerTextItem(click = onKnownIssuesClick, text = stringResource(R.string.known_issues),
			icon = R.drawable.ic_bug_report_outline_vector)
		SettingsHorizontalDivider()
	}
}

@Composable
internal fun SocialSection(onGithubClick: ()->Unit, onRedditClick: ()->Unit, onTelegramClick: ()->Unit) {
	SettingsGroup(title = {
		SettingsTitleTextComponent(text = stringResource(id = R.string.social), modifier = titleStartPadding)
	}) {
		SocialText(click = onGithubClick, text = stringResource(id = R.string.github), icon = R.drawable.ic_github_vector,
			tint = MaterialTheme.colorScheme.onSurface)
		SocialText(click = onRedditClick, text = stringResource(id = R.string.reddit), icon = R.drawable.ic_reddit_vector)
		SocialText(click = onTelegramClick, text = stringResource(id = R.string.telegram), icon = R.drawable.ic_telegram_vector)
		SettingsHorizontalDivider()
	}
}

@Composable
internal fun SocialText(text: String, icon: Int, tint: Color? = null, click: ()->Unit) {
	SettingsListItem(click = click, text = text, icon = icon, isImage = true, tint = tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun TwoLinerTextItem(text: String, icon: Int, click: ()->Unit) {
	SettingsListItem(tint = MaterialTheme.colorScheme.onSurface, click = click, text = text, icon = icon, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@MyDevices
@Composable
private fun AboutScreenPreview() {
	AppThemeSurface {
		AboutScreen(goBack = {},
			helpUsSection = {HelpUsSection({}, {}, {}, true, true, true, {})},
			aboutSection = {AboutSection(true, true, {}, {}, {})},
			socialSection = {SocialSection({}, {}, {})}) {
			OtherSection(true, {}, {}, {}, "5.0.4", "org.fossify.commons.samples", {})
		}
	}
}