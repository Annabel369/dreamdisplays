package com.dreamdisplays.utils

/**
 * Platform-specific checks. Right now it includes `Folia` detection.
 *
 * `Paper` implementation.
 */
object PlatformUtil {

    val isFolia: Boolean by lazy {
        runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess
    }
}
