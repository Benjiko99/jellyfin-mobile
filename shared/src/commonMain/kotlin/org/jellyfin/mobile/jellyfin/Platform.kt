package org.jellyfin.mobile.jellyfin

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform