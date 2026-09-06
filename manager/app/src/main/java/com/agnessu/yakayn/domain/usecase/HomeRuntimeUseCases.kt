package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.network.NetworkStatusRepository
import com.agnessu.yakayn.data.system.HomeRuntimeRepository

class GetHomeBasicInfoUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke(managerUapiVersion: Int) =
        repository.getBasicInfo(managerUapiVersion)
}

class GetHomeModuleOverviewUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke() = repository.getModuleOverview()
}

class GetHomeSuperuserCountUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke() = repository.getSuperuserCount()
}

class IsNetworkAvailableUseCase(private val repository: NetworkStatusRepository) {
    operator fun invoke() = repository.isAvailable()
}
