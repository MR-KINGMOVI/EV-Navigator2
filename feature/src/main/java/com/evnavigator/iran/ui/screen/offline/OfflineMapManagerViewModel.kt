package com.evnavigator.iran.ui.screen.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.domain.model.catalog.DomainOfflinePackageMetadata
import com.evnavigator.iran.domain.repository.OfflinePackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineMapManagerViewModel @Inject constructor(
    private val packageRepository: OfflinePackageRepository
) : ViewModel() {

    val packages: StateFlow<List<DomainOfflinePackageMetadata>> = packageRepository.packages

    fun downloadPackage(packId: String) {
        viewModelScope.launch {
            packageRepository.installPackage(packId)
        }
    }

    fun cancelDownload(packId: String) {
        packageRepository.cancelInstall(packId)
    }

    fun deletePackage(packId: String) {
        viewModelScope.launch {
            packageRepository.deletePackage(packId)
        }
    }
}
