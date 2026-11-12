package com.rinwave.sakuro.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.ui.AppInfo

/** About tab: version, project links and credits. */
internal class AboutSettingsComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext {

    val version: String = AppInfo.VERSION
    val githubUrl: String = AppInfo.GITHUB_URL
    val issuesUrl: String = AppInfo.ISSUES_URL
    val releasesUrl: String = AppInfo.RELEASES_URL
    val donateUrl: String = AppInfo.DONATE_URL
    val licenseUrl: String = AppInfo.LICENSE_URL
}
