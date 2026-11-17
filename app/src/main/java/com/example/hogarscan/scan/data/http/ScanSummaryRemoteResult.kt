package com.example.hogarscan.scan.data.http

import com.example.hogarscan.scan.domain.model.ScanSummaryReceipt
import com.example.hogarscan.shared.data.http.ApiResult

sealed interface ScanSummaryRemoteResult {

    data class Success(
        val receipt: ScanSummaryReceipt
    ) : ScanSummaryRemoteResult

    data class Failure(
        val error: ApiResult
    ) : ScanSummaryRemoteResult
}