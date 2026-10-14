package com.grandma.launcher.network

/**
 * Supplies the `dt_…` device token for `/api/device/` calls.
 */
interface DeviceTokenProvider {

    /** A token believed valid, authenticating (challenge/verify) first if none is stored. */
    suspend fun currentToken(): ApiClient.Result<String>

    /**
     * Called after a 401 for [rejectedToken]. Implementations must serialise re-auth so
     * concurrent 401s trigger a single challenge/verify, and return a newer token if
     * another caller already refreshed it.
     */
    suspend fun refreshToken(rejectedToken: String?): ApiClient.Result<String>
}
