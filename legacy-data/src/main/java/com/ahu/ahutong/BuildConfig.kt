package com.ahu.ahutong

/**
 * Stand-in for the old `:app` generated BuildConfig.
 *
 * `:legacy-data` disables AGP's BuildConfig generation on purpose: the copied data layer
 * still refers to `com.ahu.ahutong.BuildConfig`, and a library-generated class would live in
 * `com.ahu.ahutong.legacy.BuildConfig`. Values are constants so the module has no dependency
 * on the hosting app's variant. `CANTEEN_WRITE_KEY` is intentionally empty (the old key came
 * from local.properties and must never be committed).
 */
object BuildConfig {
    const val DEBUG: Boolean = false
    const val VERSION_CODE: Int = 1
    const val VERSION_NAME: String = "0.1.0-legacy"
    const val CANTEEN_WRITE_KEY: String = ""
}
