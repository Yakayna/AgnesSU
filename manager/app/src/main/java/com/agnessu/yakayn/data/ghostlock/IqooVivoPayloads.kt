package com.agnessu.yakayn.data.ghostlock

/**
 * Bundled iQOO/Vivo preload payloads.
 *
 * Each entry mirrors a folder from the IQOOToolkit's "all preload.so files"
 * collection: model + optional device codename + kernel (or a variant tag), so
 * two payloads that share a codename stay distinguishable. The binaries live
 * under assets/ghostlock/iqoo_vivo/ and are content-addressed by the sha256
 * prefix, so entries that share the same binary also share one asset.
 *
 * Codename matching is exact (e.g. "PD2507"); universal / variant-only entries
 * have a null codename and are never auto-matched, only offered for manual pick.
 */
data class IqooVivoPayload(
    val model: String,
    val codename: String?,
    val kernel: String?,
    val variant: String?,
    val asset: String,
    val sha256: String,
) {
    /** Human title mirroring the folder name, e.g. "iQOO Neo 11 (PD2520 · 6.6.89)". */
    val title: String
        get() = buildString {
            append(model)
            if (codename != null || kernel != null) {
                append(" (")
                if (codename != null) append(codename)
                if (codename != null && kernel != null) append(" · ")
                if (kernel != null) append(kernel)
                append(")")
            }
            if (variant != null) append(" · $variant")
        }
}

object IqooVivoPayloads {
    private const val ROOT = "ghostlock/iqoo_vivo"

    val all: List<IqooVivoPayload> = listOf(
        // iQOO 13
        IqooVivoPayload("iQOO 13 (China)", "V2408A", "6.6.89", null,
            "$ROOT/b3c4557e.so", "b3c4557e1f5fbb95745a9a1d9796c73fc02b19a3a03184aee2c03f686b87ca2e"),
        IqooVivoPayload("iQOO 13 (India)", "I2401", "6.6.89", null,
            "$ROOT/6a4450e0.so", "6a4450e016005b0e5e03dcffdb5877b8050aa09eb16252e3aed2ca29f8ed3b4c"),

        // iQOO Neo 11 — duplicate codename, split by kernel
        IqooVivoPayload("iQOO Neo 11", "PD2520", "6.6.127", null,
            "$ROOT/754125b4.so", "754125b4816d4dfe3defab94cda37cddcecba0d857212e6ecaa1bec0107e21b3"),
        IqooVivoPayload("iQOO Neo 11", "PD2520", "6.6.89", null,
            "$ROOT/b3c4557e.so", "b3c4557e1f5fbb95745a9a1d9796c73fc02b19a3a03184aee2c03f686b87ca2e"),

        // iQOO Neo10 Pro — duplicate codename, split by kernel build
        IqooVivoPayload("iQOO Neo10 Pro", "V2426A", "6.6.89-b57", null,
            "$ROOT/a711a89f.so", "a711a89f6824fcc1d5bdc2a13814d9541a83585d08162c2aa64255530ffbda68"),
        IqooVivoPayload("iQOO Neo10 Pro", "V2426A", "6.6.89-gf2c", null,
            "$ROOT/96c63306.so", "96c63306bd4cec7fe3edb3178995092dd2a587c10dd4c19cc66e9306636bb2d3"),

        IqooVivoPayload("iQOO Neo10 Pro+", "V2463A", "6.6.89", null,
            "$ROOT/b3c4557e.so", "b3c4557e1f5fbb95745a9a1d9796c73fc02b19a3a03184aee2c03f686b87ca2e"),

        // iQOO Z10 Turbo — original payload without a codename, plus the RMV/original pair
        IqooVivoPayload("iQOO Z10 Turbo", null, null, "Original",
            "$ROOT/c0474820.so", "c0474820eb0bef03fb2a086b1b4dec99c8015f0f03a85eaf399ef52decb563e5"),
        IqooVivoPayload("iQOO Z10 Turbo", "V2452A", "6.6.89-b57", "RMV",
            "$ROOT/a711a89f.so", "a711a89f6824fcc1d5bdc2a13814d9541a83585d08162c2aa64255530ffbda68"),
        IqooVivoPayload("iQOO Z10 Turbo", "V2452A", "6.6.89-b57", "Original",
            "$ROOT/c0474820.so", "c0474820eb0bef03fb2a086b1b4dec99c8015f0f03a85eaf399ef52decb563e5"),

        // iQOO Z10 Turbo Pro — RMV + original pair
        IqooVivoPayload("iQOO Z10 Turbo Pro", "V2453A", "6.6.89", "RMV",
            "$ROOT/b3c4557e.so", "b3c4557e1f5fbb95745a9a1d9796c73fc02b19a3a03184aee2c03f686b87ca2e"),
        IqooVivoPayload("iQOO Z10 Turbo Pro", "V2453A", "6.6.89", "Original",
            "$ROOT/87bf839f.so", "87bf839fc8524ed25831a22166ebc6cef898e7ddc77ce77736add5ca49b78b61"),

        // iQOO Z10 Turbo+ — original payload + codename-keyed entry (PD2507)
        IqooVivoPayload("iQOO Z10 Turbo+", null, null, "Original",
            "$ROOT/c0474820.so", "c0474820eb0bef03fb2a086b1b4dec99c8015f0f03a85eaf399ef52decb563e5"),
        IqooVivoPayload("iQOO Z10 Turbo+", "PD2507", "6.6.127", null,
            "$ROOT/c0474820.so", "c0474820eb0bef03fb2a086b1b4dec99c8015f0f03a85eaf399ef52decb563e5"),

        IqooVivoPayload("iQOO Z9 Turbo", "PD2352B", null, null,
            "$ROOT/c9faae52.so", "c9faae5265dfd745008c6f5a3fc557643f28dadee0ae3412cd1d44423156bced"),

        // vivo X200 series
        IqooVivoPayload("vivo X200", "PD2415", "6.6.89", null,
            "$ROOT/a711a89f.so", "a711a89f6824fcc1d5bdc2a13814d9541a83585d08162c2aa64255530ffbda68"),
        IqooVivoPayload("vivo X200 Pro", "PD2405", "6.6.89", null,
            "$ROOT/38e3abdd.so", "38e3abddaca7746f579ea2e76e1810aca9cb0adca963f8f5f634d3ec2c4cbe14"),
        IqooVivoPayload("vivo X200 Ultra", "V2454A", "6.6.89", null,
            "$ROOT/b3c4557e.so", "b3c4557e1f5fbb95745a9a1d9796c73fc02b19a3a03184aee2c03f686b87ca2e"),

        // Universal fallbacks (no codename — manual pick only)
        IqooVivoPayload("Snapdragon 8s Gen4", null, null, "universal",
            "$ROOT/9033f24d.so", "9033f24d366d78e2ea6a19d8e38a2c8c086dc174afd2c14af1d1efaa1db566de"),
        IqooVivoPayload("Universal backup", null, null, "universal",
            "$ROOT/e9075c25.so", "e9075c256d67aae9110241b3cdf3c418a7db4f970bb8e6172b2d24f27c7127b0"),
    )

    /** Entries whose codename equals [codename] (exact, case-insensitive). */
    fun matching(codename: String?): List<IqooVivoPayload> =
        if (codename.isNullOrBlank()) emptyList()
        else all.filter { it.codename?.equals(codename, ignoreCase = true) == true }
}
