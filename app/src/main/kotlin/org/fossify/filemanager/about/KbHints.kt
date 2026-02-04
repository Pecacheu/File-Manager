package org.fossify.filemanager.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import org.fossify.commons.activities.BaseComposeActivity
import org.fossify.commons.compose.extensions.MyDevices
import org.fossify.commons.compose.extensions.enableEdgeToEdgeSimple
import org.fossify.commons.compose.lists.SimpleLazyListScaffold
import org.fossify.commons.compose.settings.SettingsHorizontalDivider
import org.fossify.commons.compose.theme.AppThemeSurface
import org.fossify.commons.compose.theme.SimpleTheme
import org.fossify.filemanager.R

//TODO Resource strings?
private val hints = arrayListOf(
	"Down Arrow: Scroll down",
	"Up Arrow: Scroll up",
	"CTRL + A: Select All",
	"Main View",
	"Left Arrow: Previous tab",
	"Right Arrow: Next tab",
	"Action Mode",
	"CTRL + S: Share",
	"CTRL + C: Copy To",
	"CTRL + X: Move To",
	"CTRL + R: Rename",
	"CTRL + I: Info / Properties"
)

class KbHints: BaseComposeActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdgeSimple()
		setContent {AppThemeSurface {KbScreen(::finish, hints)}}
	}
}

@Composable
internal fun KbScreen(goBack: ()->Unit, hints: ArrayList<String>) {
	SimpleLazyListScaffold(
		title = stringResource(id = R.string.kb_hints),
		goBack = goBack,
		contentPadding = PaddingValues(bottom = SimpleTheme.dimens.padding.medium)
	) {itemsIndexed(hints) {idx, hint ->
		Column(modifier = Modifier.fillMaxWidth()) {
			val hs = hint.split(": ", limit = 2)
			if(hs.size == 2) {
				ListItem(leadingContent = {
					Text(text = hs[0], modifier = Modifier.fillMaxWidth(.4f),
						color = SimpleTheme.colorScheme.primary)
				}, headlineContent = {
					Text(text = hs[1], modifier = Modifier.fillMaxWidth(), fontSize = 14.sp)
				})
			} else {
				Spacer(Modifier.padding(bottom = SimpleTheme.dimens.padding.medium))
				SettingsHorizontalDivider(Modifier.fillMaxWidth()
					.padding(bottom = SimpleTheme.dimens.padding.small))
				ListItem(headlineContent = {
					Text(text = hint, modifier = Modifier.fillMaxWidth(),
						color = SimpleTheme.colorScheme.secondary)
				})
			}
		}
	}}
}

@MyDevices
@Composable
private fun AboutScreenPreview() {
	AppThemeSurface {KbScreen({}, hints)}
}