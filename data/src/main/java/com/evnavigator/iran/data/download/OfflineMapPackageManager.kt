package com.evnavigator.iran.data.download

import com.evnavigator.iran.domain.error.EvDomainError
import com.evnavigator.iran.domain.model.catalog.DomainOfflinePackageInstallState
import com.evnavigator.iran.domain.model.catalog.DomainOfflinePackageMetadata
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.domain.repository.OfflinePackageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy typealias preserved for backward compatibility within `:data` tests.
 */
typealias OfflinePackageMetadata = DomainOfflinePackageMetadata

/**
 * Crash-safe, integrity-verified offline map & routing package manager implementing [OfflinePackageRepository].
 *
 * Enforces Section 3D requirements:
 * - Pre-download free storage space check (`usableSpace >= packageSizeBytes + MIN_STORAGE_HEADROOM_BYTES`).
 * - Dependency graph validation (`requiredBasePackageId` must be `ACTIVE_INSTALLED` first).
 * - Staged download file (`.staged`) separated from active installed package (`.pack`).
 * - SHA-256 cryptographic checksum verification on staged payload before activation.
 * - Atomic activation via `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` and crash-safe rollback on corruption/cancellation.
 */
@Singleton
class OfflineMapPackageManager @Inject constructor() : OfflinePackageRepository {

    private var storageDirectory: File = File(System.getProperty("java.io.tmpdir"), "ev_navigator_offline_pkgs")
    private var simulatedAvailableBytesOverride: Long? = null
    private val cancelledPackages = ConcurrentHashMap.newKeySet<String>()

    constructor(customStorageDirectory: File, availableBytesOverride: Long? = null) : this() {
        this.storageDirectory = customStorageDirectory
        this.simulatedAvailableBytesOverride = availableBytesOverride
    }

    private val _packages = MutableStateFlow(
        listOf(
            DomainOfflinePackageMetadata(
                id = "pkg_tehran_alborz",
                nameFa = "تهران و البرز (شریانی و شهری)",
                nameEn = "Tehran & Alborz Metropolitan",
                sizeMb = 145,
                description = "نقشه وکتور کامل، شبکه شارژ مپنا و عوارض شیب‌سنجی بزرگراهی تهران و کرج",
                isDownloaded = true,
                version = "11.0.0",
                sha256Checksum = sha256Hex("ev_pkg_manifest:pkg_tehran_alborz:11.0.0".toByteArray(Charsets.UTF_8)),
                activeVersion = "11.0.0",
                installState = DomainOfflinePackageInstallState.ACTIVE_INSTALLED
            ),
            DomainOfflinePackageMetadata(
                id = "pkg_isfahan_kashan",
                nameFa = "اصفهان و کاشان (کریدور مرکزی)",
                nameEn = "Isfahan & Central Desert Corridor",
                sizeMb = 120,
                description = "مسیر بزرگراه خلیج فارس، ایستگاه‌های شارژ کاشان، نطنز و اصفهان",
                isDownloaded = false,
                version = "11.0.0",
                sha256Checksum = sha256Hex("ev_pkg_manifest:pkg_isfahan_kashan:11.0.0".toByteArray(Charsets.UTF_8)),
                requiredBasePackageId = "pkg_tehran_alborz"
            ),
            DomainOfflinePackageMetadata(
                id = "pkg_shiraz_fars",
                nameFa = "فارس و شیراز (کریدور جنوب)",
                nameEn = "Fars & Southern Corridor",
                sizeMb = 110,
                description = "آزادراه شیراز-اصفهان، ایستگاه‌های شارژ ایزدخواست و آباده",
                isDownloaded = false,
                version = "11.0.0",
                sha256Checksum = sha256Hex("ev_pkg_manifest:pkg_shiraz_fars:11.0.0".toByteArray(Charsets.UTF_8)),
                requiredBasePackageId = "pkg_isfahan_kashan"
            ),
            DomainOfflinePackageMetadata(
                id = "pkg_shomal_caspian",
                nameFa = "مازندران و گیلان (گردنه‌ها و خط ساحلی)",
                nameEn = "Caspian & Mountain Passes",
                sizeMb = 160,
                description = "مدل ارتفاعی گردنه کندوان، هراز و فیروزکوه با دقت شیب ۱ متری و شارژرهای ساحلی",
                isDownloaded = false,
                version = "11.0.0",
                sha256Checksum = sha256Hex("ev_pkg_manifest:pkg_shomal_caspian:11.0.0".toByteArray(Charsets.UTF_8)),
                requiredBasePackageId = "pkg_tehran_alborz"
            ),
            DomainOfflinePackageMetadata(
                id = "pkg_mashhad_khorasan",
                nameFa = "خراسان رضوی و سمنان (کریدور شرق)",
                nameEn = "Mashhad & Silk Road Corridor",
                sizeMb = 135,
                description = "بزرگراه امام رضا، دشت کویر، سمنان، شاهرود و نیشابور تا مشهد مقدس",
                isDownloaded = false,
                version = "11.0.0",
                sha256Checksum = sha256Hex("ev_pkg_manifest:pkg_mashhad_khorasan:11.0.0".toByteArray(Charsets.UTF_8)),
                requiredBasePackageId = "pkg_tehran_alborz"
            )
        )
    )
    override val packages: StateFlow<List<DomainOfflinePackageMetadata>> = _packages.asStateFlow()

    override suspend fun installPackage(packId: String): Result<DomainOfflinePackageMetadata> {
        return installPackageWithVerification(packId, corruptedPayloadOverride = null)
    }

    /**
     * Installs a package with staged writing, dependency validation, free space validation,
     * SHA-256 checksum verification, and atomic activation.
     */
    suspend fun installPackageWithVerification(
        packId: String,
        corruptedPayloadOverride: ByteArray? = null
    ): Result<DomainOfflinePackageMetadata> {
        val target = _packages.value.find { it.id == packId }
            ?: return Result.failure(IllegalArgumentException("Unknown offline package ID: $packId"))

        cancelledPackages.remove(packId)

        // 1. Validate dependency graph
        val requiredDepId = target.requiredBasePackageId
        if (requiredDepId != null) {
            val depPkg = _packages.value.find { it.id == requiredDepId }
            if (depPkg == null || !depPkg.isDownloaded || depPkg.installState != DomainOfflinePackageInstallState.ACTIVE_INSTALLED) {
                val updated = markFailedState(
                    packId = packId,
                    state = DomainOfflinePackageInstallState.FAILED_DEPENDENCY,
                    reasonFa = "ابتدا بسته پیش‌نیاز (${depPkg?.nameFa ?: requiredDepId}) باید نصب و فعال شود."
                )
                return Result.failure(
                    Exception(
                        EvDomainError.PackageIntegrityFailure(
                            packageId = packId,
                            failureKind = "DEPENDENCY_MISSING",
                            messageEn = "Required base package $requiredDepId is not active",
                            messageFa = updated?.lastErrorReasonFa ?: "بسته پیش‌نیاز نصب نشده است"
                        ).messageEn
                    )
                )
            }
        }

        // 2. Validate storage directory and free disk space
        if (!storageDirectory.exists()) {
            storageDirectory.mkdirs()
        }
        val requiredBytes = (target.sizeMb.toLong() * 1024L * 1024L) + EvDomainPolicyConfig.MIN_STORAGE_HEADROOM_BYTES
        val availableBytes = simulatedAvailableBytesOverride ?: storageDirectory.usableSpace
        if (availableBytes in 1 until requiredBytes) {
            val updated = markFailedState(
                packId = packId,
                state = DomainOfflinePackageInstallState.FAILED_INSUFFICIENT_STORAGE,
                reasonFa = "فضای ذخیره‌سازی کافی نیست (نیاز به حداقل ${target.sizeMb + 50} مگابایت فضای خالی)."
            )
            return Result.failure(
                Exception(
                    EvDomainError.PackageIntegrityFailure(
                        packageId = packId,
                        failureKind = "INSUFFICIENT_STORAGE",
                        messageEn = "Insufficient storage space ($availableBytes < $requiredBytes)",
                        messageFa = updated?.lastErrorReasonFa ?: "فضای کافی وجود ندارد"
                    ).messageEn
                )
            )
        }

        val stagedFile = File(storageDirectory, "${packId}_${target.version}.staged")
        val activeFile = File(storageDirectory, "${packId}_${target.version}.pack")

        try {
            _packages.update { list ->
                list.map {
                    if (it.id == packId) {
                        it.copy(
                            isDownloading = true,
                            progressPercent = 0,
                            stagedVersion = target.version,
                            installState = DomainOfflinePackageInstallState.DOWNLOADING,
                            lastErrorReasonFa = null
                        )
                    } else it
                }
            }

            for (step in 1..5) {
                if (cancelledPackages.contains(packId)) {
                    stagedFile.delete()
                    _packages.update { list ->
                        list.map {
                            if (it.id == packId) {
                                it.copy(
                                    isDownloading = false,
                                    progressPercent = 0,
                                    stagedVersion = null,
                                    installState = DomainOfflinePackageInstallState.CANCELLED,
                                    lastErrorReasonFa = "دانلود بسته توسط کاربر لغو شد."
                                )
                            } else it
                        }
                    }
                    return Result.failure(CancellationException("Package install cancelled for $packId"))
                }
                delay(20)
                _packages.update { list ->
                    list.map {
                        if (it.id == packId) it.copy(progressPercent = step * 20) else it
                    }
                }
            }

            // 3. Write staged payload and flush to disk
            val payloadBytes = corruptedPayloadOverride
                ?: "ev_pkg_manifest:$packId:${target.version}".toByteArray(Charsets.UTF_8)
            FileOutputStream(stagedFile).use { fos ->
                fos.write(payloadBytes)
                fos.flush()
                fos.fd.sync()
            }

            _packages.update { list ->
                list.map {
                    if (it.id == packId) {
                        it.copy(installState = DomainOfflinePackageInstallState.STAGED_VERIFYING)
                    } else it
                }
            }

            // 4. Verify SHA-256 checksum on staged file before atomic promotion
            val stagedBytes = stagedFile.readBytes()
            if (!verifyChecksum(stagedBytes, target.sha256Checksum)) {
                stagedFile.delete()
                val updated = markFailedState(
                    packId = packId,
                    state = DomainOfflinePackageInstallState.FAILED_CHECKSUM,
                    reasonFa = "عدم تطابق امضای دیجیتال (SHA-256 Checksum)؛ فایل ناقص پاک‌سازی و به نسخه قبلی بازگردانی شد."
                )
                return Result.failure(
                    Exception(
                        EvDomainError.PackageIntegrityFailure(
                            packageId = packId,
                            failureKind = "CHECKSUM_MISMATCH",
                            messageEn = "SHA-256 checksum mismatch for staged package $packId",
                            messageFa = updated?.lastErrorReasonFa ?: "خطای Checksum"
                        ).messageEn
                    )
                )
            }

            // 5. Atomic promotion from staged (.staged) to active (.pack)
            try {
                Files.move(
                    stagedFile.toPath(),
                    activeFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(
                    stagedFile.toPath(),
                    activeFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            _packages.update { list ->
                list.map {
                    if (it.id == packId) {
                        it.copy(
                            isDownloading = false,
                            isDownloaded = true,
                            progressPercent = 100,
                            stagedVersion = null,
                            activeVersion = target.version,
                            installState = DomainOfflinePackageInstallState.ACTIVE_INSTALLED,
                            lastErrorReasonFa = null
                        )
                    } else it
                }
            }

            return Result.success(_packages.value.first { it.id == packId })
        } catch (ce: CancellationException) {
            stagedFile.delete()
            _packages.update { list ->
                list.map {
                    if (it.id == packId) {
                        it.copy(
                            isDownloading = false,
                            progressPercent = 0,
                            stagedVersion = null,
                            installState = DomainOfflinePackageInstallState.CANCELLED,
                            lastErrorReasonFa = "عملیات نصب لغو و فایل موقت پاک شد."
                        )
                    } else it
                }
            }
            throw ce
        } catch (e: Exception) {
            stagedFile.delete()
            markFailedState(
                packId = packId,
                state = DomainOfflinePackageInstallState.ROLLED_BACK,
                reasonFa = "خطا در ذخیره‌سازی اتمیک بسته؛ بازگردانی ایمن انجام شد."
            )
            return Result.failure(e)
        }
    }

    override suspend fun cancelInstall(packId: String) {
        cancelledPackages.add(packId)
        val target = _packages.value.find { it.id == packId } ?: return
        val stagedFile = File(storageDirectory, "${packId}_${target.version}.staged")
        if (stagedFile.exists()) {
            stagedFile.delete()
        }
        _packages.update { list ->
            list.map {
                if (it.id == packId) {
                    it.copy(
                        isDownloading = false,
                        progressPercent = 0,
                        stagedVersion = null,
                        installState = if (it.isDownloaded) {
                            DomainOfflinePackageInstallState.ACTIVE_INSTALLED
                        } else {
                            DomainOfflinePackageInstallState.CANCELLED
                        },
                        lastErrorReasonFa = "نصب بسته لغو شد."
                    )
                } else it
            }
        }
    }

    override suspend fun retryInstall(packId: String): Result<DomainOfflinePackageMetadata> {
        cancelledPackages.remove(packId)
        return installPackage(packId)
    }

    override suspend fun deletePackage(packId: String) {
        val target = _packages.value.find { it.id == packId } ?: return
        File(storageDirectory, "${packId}_${target.version}.staged").delete()
        File(storageDirectory, "${packId}_${target.version}.pack").delete()
        _packages.update { list ->
            list.map {
                if (it.id == packId) {
                    it.copy(
                        isDownloaded = false,
                        isDownloading = false,
                        progressPercent = 0,
                        stagedVersion = null,
                        activeVersion = null,
                        installState = DomainOfflinePackageInstallState.NOT_INSTALLED,
                        lastErrorReasonFa = null
                    )
                } else it
            }
        }
    }

    override fun verifyChecksum(payloadBytes: ByteArray, expectedSha256Hex: String): Boolean {
        if (expectedSha256Hex.isBlank()) return false
        val actualHex = sha256Hex(payloadBytes)
        return actualHex.equals(expectedSha256Hex.trim(), ignoreCase = true)
    }

    private fun markFailedState(
        packId: String,
        state: DomainOfflinePackageInstallState,
        reasonFa: String
    ): DomainOfflinePackageMetadata? {
        _packages.update { list ->
            list.map {
                if (it.id == packId) {
                    // Preserve existing active version if previously installed (crash-safe rollback)
                    val keepActive = it.isDownloaded && it.activeVersion != null
                    it.copy(
                        isDownloading = false,
                        isDownloaded = keepActive,
                        progressPercent = if (keepActive) 100 else 0,
                        stagedVersion = null,
                        installState = state,
                        lastErrorReasonFa = reasonFa
                    )
                } else it
            }
        }
        return _packages.value.find { it.id == packId }
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
