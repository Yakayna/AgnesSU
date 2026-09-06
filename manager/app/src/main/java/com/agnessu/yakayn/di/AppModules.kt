package com.agnessu.yakayn.di

import coil.ImageLoader
import com.agnessu.yakayn.BuildConfig
import com.agnessu.yakayn.data.AppSettingsRepository
import com.agnessu.yakayn.data.application.ApplicationControlRepository
import com.agnessu.yakayn.data.application.DynamicManagerRepository
import com.agnessu.yakayn.data.download.DownloadRepository
import com.agnessu.yakayn.data.file.ModuleFileRepository
import com.agnessu.yakayn.data.flash.FlashRepository
import com.agnessu.yakayn.data.kernel.KernelRepository
import com.agnessu.yakayn.data.kernel.UmountRepository
import com.agnessu.yakayn.data.logging.BugreportRepository
import com.agnessu.yakayn.data.logging.SulogRepository
import com.agnessu.yakayn.data.module.ModuleActionRepository
import com.agnessu.yakayn.data.module.ModuleCatalogRepository
import com.agnessu.yakayn.data.module.ModulePreferencesRepository
import com.agnessu.yakayn.data.module.ModuleRepository
import com.agnessu.yakayn.data.network.NetworkRequestRepository
import com.agnessu.yakayn.data.network.NetworkStatusRepository
import com.agnessu.yakayn.data.network.WebResourceRepository
import com.agnessu.yakayn.data.packageinfo.AppIconDataSource
import com.agnessu.yakayn.data.packageinfo.InstalledPackageCache
import com.agnessu.yakayn.data.packageinfo.InstalledPackageRepository
import com.agnessu.yakayn.data.packageinfo.RootServiceRepository
import com.agnessu.yakayn.data.packageinfo.SuperUserRepository
import com.agnessu.yakayn.data.profile.ProfileRepository
import com.agnessu.yakayn.data.profile.ProfileTemplateRepository
import com.agnessu.yakayn.data.settings.LocaleHelper
import com.agnessu.yakayn.data.settings.LocaleRepository
import com.agnessu.yakayn.data.settings.SettingsPlatformRepository
import com.agnessu.yakayn.data.shell.KsuCliRepository
import com.agnessu.yakayn.data.shell.ShortcutRepository
import com.agnessu.yakayn.data.startup.ApplicationInitializationRepository
import com.agnessu.yakayn.data.startup.StartupRepository
import com.agnessu.yakayn.data.susfs.SuSFSConfigHelper
import com.agnessu.yakayn.data.susfs.SuSFSRepository
import com.agnessu.yakayn.data.system.HomeRuntimeRepository
import com.agnessu.yakayn.data.system.HomeStateRepository
import com.agnessu.yakayn.data.text.HanziToPinyin
import com.agnessu.yakayn.data.theme.MonetCompatColorSource
import com.agnessu.yakayn.data.theme.ThemeRepository
import com.agnessu.yakayn.data.update.ManagerUpdateRepository
import com.agnessu.yakayn.data.webui.WebUiRepository
import com.agnessu.yakayn.domain.text.TextTransliterator
import com.agnessu.yakayn.domain.usecase.AddUmountPathUseCase
import com.agnessu.yakayn.domain.usecase.ApplyLanguageUseCase
import com.agnessu.yakayn.domain.usecase.BackupAllowlistUseCase
import com.agnessu.yakayn.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.agnessu.yakayn.domain.usecase.CheckFlashModuleMountUseCase
import com.agnessu.yakayn.domain.usecase.CheckManagerUpdateUseCase
import com.agnessu.yakayn.domain.usecase.CleanSulogUseCase
import com.agnessu.yakayn.domain.usecase.ClearDynamicManagerUseCase
import com.agnessu.yakayn.domain.usecase.ConfigureSuLogUseCase
import com.agnessu.yakayn.domain.usecase.ControlAppUseCase
import com.agnessu.yakayn.domain.usecase.DeleteProfileTemplateUseCase
import com.agnessu.yakayn.domain.usecase.EnableSulogUseCase
import com.agnessu.yakayn.domain.usecase.EnqueueDownloadUseCase
import com.agnessu.yakayn.domain.usecase.EnqueueManagerUpdateUseCase
import com.agnessu.yakayn.domain.usecase.EnsureManagerInstalledUseCase
import com.agnessu.yakayn.domain.usecase.ExecuteFlashOperationUseCase
import com.agnessu.yakayn.domain.usecase.ExecuteModuleActionUseCase
import com.agnessu.yakayn.domain.usecase.ExportProfileTemplatesUseCase
import com.agnessu.yakayn.domain.usecase.ExtractModuleIdUseCase
import com.agnessu.yakayn.domain.usecase.ExtractModuleNameUseCase
import com.agnessu.yakayn.domain.usecase.FetchRemoteTextUseCase
import com.agnessu.yakayn.domain.usecase.GenerateBugreportUseCase
import com.agnessu.yakayn.domain.usecase.GetAppProfileUseCase
import com.agnessu.yakayn.domain.usecase.GetAppSepolicyUseCase
import com.agnessu.yakayn.domain.usecase.GetBooleanPreferenceUseCase
import com.agnessu.yakayn.domain.usecase.GetCatalogModuleUseCase
import com.agnessu.yakayn.domain.usecase.GetDefaultUmountModulesUseCase
import com.agnessu.yakayn.domain.usecase.GetHomeBasicInfoUseCase
import com.agnessu.yakayn.domain.usecase.GetHomeModuleOverviewUseCase
import com.agnessu.yakayn.domain.usecase.GetHomeSuperuserCountUseCase
import com.agnessu.yakayn.domain.usecase.GetInstallEnvironmentUseCase
import com.agnessu.yakayn.domain.usecase.GetKernelFeatureSettingsUseCase
import com.agnessu.yakayn.domain.usecase.GetKernelStatusUseCase
import com.agnessu.yakayn.domain.usecase.GetManagerRuntimeInfoUseCase
import com.agnessu.yakayn.domain.usecase.GetPlatformFeatureStatusUseCase
import com.agnessu.yakayn.domain.usecase.GetProfileTemplateUseCase
import com.agnessu.yakayn.domain.usecase.GetStringPreferenceUseCase
import com.agnessu.yakayn.domain.usecase.GetStringSetPreferenceUseCase
import com.agnessu.yakayn.domain.usecase.GetSuSFSStatusUseCase
import com.agnessu.yakayn.domain.usecase.GetSuperUserAppGroupUseCase
import com.agnessu.yakayn.domain.usecase.ImportAllowlistUseCase
import com.agnessu.yakayn.domain.usecase.ImportProfileTemplatesUseCase
import com.agnessu.yakayn.domain.usecase.InitializeApplicationUseCase
import com.agnessu.yakayn.domain.usecase.IsLateLoadModeUseCase
import com.agnessu.yakayn.domain.usecase.IsModuleUriAccessibleUseCase
import com.agnessu.yakayn.domain.usecase.IsNetworkAvailableUseCase
import com.agnessu.yakayn.domain.usecase.IsSystemLanguageSettingsUseCase
import com.agnessu.yakayn.domain.usecase.LaunchSystemLanguageSettingsUseCase
import com.agnessu.yakayn.domain.usecase.LoadSettingsPlatformUseCase
import com.agnessu.yakayn.domain.usecase.ObserveCatalogModulesUseCase
import com.agnessu.yakayn.domain.usecase.ObserveDownloadUseCase
import com.agnessu.yakayn.domain.usecase.ObserveDynamicManagerStateUseCase
import com.agnessu.yakayn.domain.usecase.ObserveInstalledModulesUseCase
import com.agnessu.yakayn.domain.usecase.ObserveKernelFlashUseCase
import com.agnessu.yakayn.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.agnessu.yakayn.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.agnessu.yakayn.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.agnessu.yakayn.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.agnessu.yakayn.domain.usecase.ObserveProfileTemplatesUseCase
import com.agnessu.yakayn.domain.usecase.ObserveStartupStateUseCase
import com.agnessu.yakayn.domain.usecase.ObserveSulogStateUseCase
import com.agnessu.yakayn.domain.usecase.ObserveSuperUserStateUseCase
import com.agnessu.yakayn.domain.usecase.ObserveUmountStateUseCase
import com.agnessu.yakayn.domain.usecase.RebootUseCase
import com.agnessu.yakayn.domain.usecase.RefreshDynamicManagerUseCase
import com.agnessu.yakayn.domain.usecase.RefreshInstalledModulesUseCase
import com.agnessu.yakayn.domain.usecase.RefreshModuleCatalogUseCase
import com.agnessu.yakayn.domain.usecase.RefreshProfileTemplatesUseCase
import com.agnessu.yakayn.domain.usecase.RefreshSulogUseCase
import com.agnessu.yakayn.domain.usecase.RefreshSuperUsersUseCase
import com.agnessu.yakayn.domain.usecase.RefreshUmountPathsUseCase
import com.agnessu.yakayn.domain.usecase.RemovePreferenceUseCase
import com.agnessu.yakayn.domain.usecase.RemoveUmountPathUseCase
import com.agnessu.yakayn.domain.usecase.SaveModuleActionLogUseCase
import com.agnessu.yakayn.domain.usecase.SaveProfileTemplateUseCase
import com.agnessu.yakayn.domain.usecase.SelectDynamicManagerUseCase
import com.agnessu.yakayn.domain.usecase.SetAppProfileUseCase
import com.agnessu.yakayn.domain.usecase.SetAppSepolicyUseCase
import com.agnessu.yakayn.domain.usecase.SetBooleanPreferenceUseCase
import com.agnessu.yakayn.domain.usecase.SetDefaultUmountModulesUseCase
import com.agnessu.yakayn.domain.usecase.SetKernelUmountEnabledUseCase
import com.agnessu.yakayn.domain.usecase.SetManualDynamicManagerUseCase
import com.agnessu.yakayn.domain.usecase.SetModuleEnabledUseCase
import com.agnessu.yakayn.domain.usecase.SetModuleRemovedUseCase
import com.agnessu.yakayn.domain.usecase.SetSelinuxHideEnabledUseCase
import com.agnessu.yakayn.domain.usecase.SetStringPreferenceUseCase
import com.agnessu.yakayn.domain.usecase.SetStringSetPreferenceUseCase
import com.agnessu.yakayn.domain.usecase.SetSuEnabledUseCase
import com.agnessu.yakayn.domain.usecase.SetWebViewZygoteUmountEnabledUseCase
import com.agnessu.yakayn.domain.usecase.StartKernelFlashUseCase
import com.agnessu.yakayn.domain.usecase.SuSFSConfigUseCase
import com.agnessu.yakayn.domain.usecase.TakeModuleUriPermissionUseCase
import com.agnessu.yakayn.domain.usecase.TransliterateTextUseCase
import com.agnessu.yakayn.domain.usecase.UpdateAppearanceUseCase
import com.agnessu.yakayn.domain.usecase.UpdateCachedModuleEnabledUseCase
import com.agnessu.yakayn.domain.usecase.UpdatePlatformSettingUseCase
import com.agnessu.yakayn.domain.usecase.ValidateSepolicyUseCase
import com.agnessu.yakayn.ui.activity.util.ThemeUtils
import com.agnessu.yakayn.ui.component.ZipFileDetector
import com.agnessu.yakayn.ui.theme.BackgroundManager
import com.agnessu.yakayn.ui.theme.CardConfig
import com.agnessu.yakayn.ui.theme.ThemeConfig
import com.agnessu.yakayn.ui.util.module.Shortcut
import com.agnessu.yakayn.ui.viewmodel.AppProfileViewModel
import com.agnessu.yakayn.ui.viewmodel.DynamicManagerViewModel
import com.agnessu.yakayn.ui.viewmodel.ExecuteModuleActionViewModel
import com.agnessu.yakayn.ui.viewmodel.FlashViewModel
import com.agnessu.yakayn.ui.viewmodel.HomeViewModel
import com.agnessu.yakayn.ui.viewmodel.InstallViewModel
import com.agnessu.yakayn.ui.viewmodel.KernelFlashViewModel
import com.agnessu.yakayn.ui.viewmodel.MainIntentViewModel
import com.agnessu.yakayn.ui.viewmodel.ModuleDetailViewModel
import com.agnessu.yakayn.ui.viewmodel.ModuleRepoViewModel
import com.agnessu.yakayn.ui.viewmodel.ModuleViewModel
import com.agnessu.yakayn.ui.viewmodel.SettingsViewModel
import com.agnessu.yakayn.ui.viewmodel.SuSFSViewModel
import com.agnessu.yakayn.ui.viewmodel.SulogViewModel
import com.agnessu.yakayn.ui.viewmodel.SuperUserViewModel
import com.agnessu.yakayn.ui.viewmodel.TemplateEditorViewModel
import com.agnessu.yakayn.ui.viewmodel.TemplateViewModel
import com.agnessu.yakayn.ui.viewmodel.UmountManagerScreenViewModel
import com.agnessu.yakayn.ui.webui.MonetColorsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "AgnesSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build()
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
            monetCompatColorSource = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::GetHomeModuleOverviewUseCase)
    factoryOf(::GetHomeSuperuserCountUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::SetWebViewZygoteUmountEnabledUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::ObserveModuleCatalogOfflineUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModelOf(::ModuleRepoViewModel)
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], get()) }
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
