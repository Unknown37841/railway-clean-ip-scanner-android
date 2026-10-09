package com.spider.railwayipscanner

data class ScanResult(
    val ip: String,
    val isSuccess: Boolean,
    val delayMs: Long,
    val error: String? = null
)
