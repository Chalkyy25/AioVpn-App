package uk.co.aiovpn.android.routing

object ServerRankingCache {
    @Volatile
    var rankedServers: List<ServerScore>? = null

    @Volatile
    var lastUpdated: Long = 0

    fun isValid(): Boolean {
        return System.currentTimeMillis() - lastUpdated < 120_000
    }

    fun set(data: List<ServerScore>) {
        rankedServers = data
        lastUpdated = System.currentTimeMillis()
    }

    fun clear() {
        rankedServers = null
        lastUpdated = 0
    }
}