package io.rami.screenrecorder.data.recorder

import io.rami.screenrecorder.domain.repository.CaptureConsentRepository
import javax.inject.Inject
import javax.inject.Singleton

/** [CaptureConsentRepository]의 메모리 보관소 구현 (CLAUDE.md 7절). */
@Singleton
class TokenHolderCaptureConsentRepository
    @Inject
    constructor(
        private val tokenHolder: MediaProjectionTokenHolder,
    ) : CaptureConsentRepository {
        override fun discardPending() = tokenHolder.clear()
    }
