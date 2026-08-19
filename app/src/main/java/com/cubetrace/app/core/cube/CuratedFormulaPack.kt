// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.cube

/**
 * The complete beginner CFOP reference set used by the local catalog.
 *
 * The strings are written for the app's fixed reference frame: yellow on U,
 * blue on F, white Cross on D. Parentheses are intentionally omitted because
 * they do not change the move sequence; lower-case face moves are supported by
 * MoveParser as the compact notation for wide turns (for example `d`).
 *
 * Recognition images are not stored separately. F2L and OLL use the explicit
 * CubeRoot setup sequences below, while the displayed algorithm remains its
 * own source field; both the nine-grid state and native 3D playback consume
 * the same setup state.
 */
object CuratedFormulaPack {
    val f2l: List<String> = listOf(
        // Primary algorithms and ordering mirror CubeRoot's 41 F2L cases.
        // A prime after a half turn is redundant; it is normalized here so
        // the local parser cannot silently discard a valid algorithm.
        "U R U' R'",
        "F R' F' R",
        "R U R'",
        "F' U' F",
        "U' R U R' U2 R U' R'",
        "U' r U' R' U R U r'",
        "U' R U2 R' U' R U2 R'",
        "d R' U2 R U R' U2 R",
        "U' R U R' U R U R'",
        "U' R U' R' U F' U' F",
        "U R U2 R' U R U' R'",
        "y' U' R' U2 R U' R' U R",
        "U2 R U R' U R U' R'",
        "r U' r' U2 r U r'",
        "R U' R' U R U' R' U2 R U' R'",
        "U' R U2 R' U F' U' F",
        "U' R U' R' U R U R'",
        "y' U R' U R U' R' U' R",
        "R' D' R U' R' D R U R U' R'",
        "R U' R' U2 F' U' F",
        "R U2 R' U' R U R'",
        "y' R' U2 R U R' U' R",
        "U R U' R' U' R U' R' U R U' R'",
        "F U R U' R' F' R U' R'",
        "U' R' F R F' R U R'",
        "U R U' R' F R' F' R",
        "R U' R' U R U' R'",
        "R U R' U' F R' F' R",
        "R U R' U' R U R'",
        "R' F R F' U R U' R'",
        "U' R' F R F' R U' R'",
        "U R U' R' U R U' R' U R U' R'",
        "U' R U' R' U2 R U' R'",
        "U R U R' U2 R U R'",
        "U' R U R' U F' U' F",
        "U F' U' F U' R U R'",
        "R2 U2 F R2 F' U2 R' U R'",
        "R U' R' U' R U R' U2 R U' R'",
        "R U' R' U R U2 R' U R U' R'",
        "r U' r' U2 r U r' R U R'",
        "R U' R' r U' r' U2 r U r'"
    )

    /**
     * CubeRoot's explicit setup state for each F2L case, in the same 1..41
     * order as [f2l].  A setup state is not interchangeable with the inverse
     * of a selected algorithm: a case can have several valid algorithms and
     * the site's diagram is keyed to this exact setup sequence.
     */
    val f2lSetup: List<String> = listOf(
        "F R' F' R",
        "R' F R F'",
        "R U' R'",
        "F' U F",
        "R U R' U2' R U' R' U",
        "F' U' F U2' F' U F U'",
        "R U R' U2' R U2' R' U",
        "r' U' R2 U' R2' U2' r",
        "R U' R' U' R U' R' U",
        "F' U F U' R U R' U",
        "R U R' U' R U2' R' U'",
        "R U R' F R' F' R2' U R' U",
        "R U' R' U2' R U R'",
        "F' L' U2' L F",
        "R U R' U2' R U R' U' R U R'",
        "F' U F U' R U2' R' U",
        "R U' R' U' R U R' U",
        "r U2' R' U R U' R' U M",
        "R U R' U' R U R' U2' R U' R'",
        "F' U F U2' R U R'",
        "R U' R' U R U2' R'",
        "R U R' U' R U R' F R' F' R",
        "R U' R' U R U' R' U2' R U' R'",
        "R U R' F R U R' U' F'",
        "F' R U R' U' R' F R",
        "F' U' F U R U R' U'",
        "R U R' U' R U R'",
        "R' F R F' U R U' R'",
        "R U' R' U R U' R'",
        "F R' F' R F R' F' R",
        "R U R' F R' F' R U",
        "R U' R' U R U' R' U R U' R'",
        "R U R' U2' R U R' U",
        "R U' R' U2' R U' R' U'",
        "F' U F U' R U' R' U",
        "R U' R' U2' F R' F' R U2'",
        "R U' R U2' F R2' F' U2' R2'",
        "R U' R' U R U2' R' U R U' R'",
        "R U' R' U' R U R' U2' R U' R'",
        "R U R' F U R U' R' F' R U R'",
        "R F U R U' R' F' U' R'"
    )

    /** Previous local OLL list kept here only as a migration reference. */
    private val legacyOll: List<String> = listOf(
        "M' U M U2 M' U M",
        "R U R' U' M' U R U' Rw'",
        "R U2 R' U' R U R' U' R U' R'",
        "R U2 R2 U' R2 U' R2 U2 R",
        "R2 D' R U2 R' D R U2 R",
        "Rw U R' U' Rw' F R F'",
        "F' Rw U R' U' Rw' F R",
        "R U2 R' U' R U' R'",
        "R U R' U R U2 R'",
        "Rw' R U R U R' U' Rw2 R2 U R U' Rw'",
        "Fw R U R' U' Fw' U' F R U R' U' F'",
        "Fw R U R' U' Fw' U F R U R' U' F'",
        "R U R' U R' F R F' U2 R' F R F'",
        "Rw' R U R U R' U' Rw x R2 U R U' x'",
        "F R U R' U y' R' U2 R' F R F'",
        "F R U R' U' S R U R' U' Fw'",
        "R U2 R2 F R F' U2 R' F R F'",
        "R U R' U' R' F R F'",
        "F R U R' U' F'",
        "Fw R U R' U' Fw'",
        "Fw' L' U' L U Fw",
        "R Dw L' Dw' R' U Lw U Lw'",
        "R' U' F U R U' R' F' R",
        "R U R' U R U' R' U' R' F R F'",
        "L' U' L U L' U L U L F' L' F",
        "Rw U R' U R U' R' U R U2 Rw'",
        "Rw' U' R U R' U R U' R' U2 Rw",
        "R B' R B R2 U2 F R' F' R",
        "R' F R' F' R2 U2 y R' F R F'",
        "F R U R' U' R U R' U' F'",
        "F' L' U' L U L' U' L U F",
        "L F' L' U' L U F U' L'",
        "R' F R U R' U' F' U R",
        "R U R2 U' R' F R U R U' F'",
        "R' U' R' F R F' U R",
        "Rw' U2 R U R' U Rw",
        "Rw U2 R' U' R U' Rw'",
        "Rw U R' U R U2 Rw'",
        "M U2 R' U' R U' R' U2 R U M'",
        "Rw' U' R U R' U2 Rw",
        "Rw' R2 U R' U R U2 R' U M'",
        "F R U' R' U' R U R' F'",
        "R U2 R2 F R F' R U2 R'",
        "R U R' U R' F R F' R U2 R'",
        "R U R' U' R' F R2 U R' U' F'",
        "Fw R U R' U' R U R' U' Fw'",
        "R U R' U R Dw' R U' R' F'",
        "Fw R U R' U' Fw' F R U R' U' R U R' U' F'",
        "R U2 R2 U' R U' R' U2 F R F'",
        "Rw U' Rw' U' Rw U Rw' y' R' U R",
        "Rw U Rw' R U R' U' Rw U' Rw'",
        "R' F R U R' F' R y' R U' R'",
        "Lw' U' Lw L' U' L U Lw' U Lw",
        "R U' R' U2 R U y R U' R' U' F'",
        "R2 U R' B' R U' R2 U Lw U Lw'",
        "L' U L U2 L' U' y' L' U L U F",
        "L2 U L B L' U L2 U Rw' U Rw"
    )

    /**
     * Explicit CubeRoot OLL setup states in the chart's numeric order.
     *
     * The case number, current-state diagram, and 3D starting state now share
     * this exact order. The state generator normalizes whole-cube rotations so
     * every case remains in the yellow-top / blue-front reference frame.
     */
    val ollSetup: List<String> = listOf(
        "F R' F' R U2' F R' F' R2' U2' R'",
        "f U R U' R' f' F U R U' R' F'",
        "F U R U' R' F' U f U R U' R' f' y",
        "F U R U' R' F' U' f U R U' R' f' y",
        "r' U' R U' R' U2' r",
        "r U R' U R U2' r'",
        "r U2' R' U' R U' r'",
        "r' U2' R U R' U r y2'",
        "F U R U' R2' F' R U R U' R' y'",
        "R U2' R' F R' F' R U' R U' R'",
        "M U' R U2' R' U' R U' R2' r",
        "F U R U' R' F' U' F U R U' R' F'",
        "F' U' F r U' r' U r U r'",
        "F U F' R' F R U' R' F' R",
        "r' U' r U' R' U R r' U r",
        "r U r' U R U' R' r U' r'",
        "F R' F' R U2' F R' F' R U' R U' R'",
        "r' U2' R U R' U r2' U2' R' U' R U' r'",
        "F R' F' R M U R U' R' U' M'",
        "r U R' U' M2' U R U' R' U' M'",
        "R U R' U R U' R' U R U2' R' y'",
        "R' U2' R2' U R2' U R2' U2' R'",
        "R U2' R D R' U2' R D' R2'",
        "F R' F' r U R U' r'",
        "R' F' r U R U' r' F y'",
        "R U R' U R U2' R' y'",
        "R U2' R' U' R U' R'",
        "R U R' U' M' U R U' r'",
        "M F R' F' R U R U' R' U' M'",
        "F U R U2' R' U R U2' R' U' F' y2'",
        "R' F R U R' U' F' U R",
        "f R' F' R U R U' R' S'",
        "F R' F' R U R U' R'",
        "F U R' U' R' F' R U R2' U' R' y2'",
        "R U2' R' F R' F' R2' U2' R'",
        "F' L F L' U' L' U' L U L' U L y2'",
        "F R U' R' U R U R' F'",
        "F R' F' R U R U R' U' R U' R'",
        "L U F' U' L' U L F L' y'",
        "R' U' F U R U' R' F' R y'",
        "F U R U' R' F' R U2' R' U' R U' R' y2'",
        "F U R U' R' F' R' U2' R U R' U R",
        "f' U' L' U L f",
        "f U R U' R' f'",
        "F U R U' R' F'",
        "R' U' F R' F' R U R",
        "F' U' L' U L U' L' U L F",
        "F U R U' R' U R U' R' F'",
        "r' U r2' U' r2' U' r2' U r' y2'",
        "r U' r2' U r2' U r2' U' r",
        "f U R U' R' U R U' R' f'",
        "F R U R' d R' U' R U' R'",
        "r' U2' R U R' U' R U R' U r",
        "r U2' R' U' R U R' U' R U' r'",
        "F R' F' U2' R U R' U R2' U2' R'",
        "r U r' R U R' U' R U R' U' r U' r'",
        "r U R' U' M U R U' R'"
    )

    /** The second (primary) formula shown under each CubeRoot OLL setup. */
    private val legacyOllWithUnfixedFrame: List<String> = """
        R U2' R2' F R F' U2' R' F R F'
        U' R U' R2 D' r U r' D R2 U R'
        U' f R U R' U' f' U' F R U R' U' F'
        U' R' F2 R2 U2 R' F' R U2 R2 F2 R
        r' U2 R U R' U r
        r U2 R' U' R U' r'
        r U R' U R U2 r'
        U2 r' U' R U' R' U2 r
        U R U R' U' R' F R2 U R' U' F'
        R U R' U R' F R F' R U2 R'
        r' R2 U R' U R U2 R' U M'
        U' M' R' U' R U' R' U2 R U' M
        F U R U2 R' U' R U R' F'
        R' F R U R' F' R F U' F'
        r' U' r R' U' R U r' U r
        r U r' R U R' U' r U' r'
        R U R' U R' F R F' U2 R' F R F'
        U R U2 R2 F R F' U2 M' U R U' r'
        U S' R U R' S U' R' F R F'
        r U R' U' M2 U R U' R' U' M'
        R U R' U R U' R' U R U2 R'
        R U2 R2 U' R2 U' R2 U2 R
        R2 D R' U2 R D' R' U2 R'
        r U R' U' r' F R F'
        R U2 R D R' U2 R D' R2
        U R U2 R' U' R U' R'
        R U R' U R U2' R'
        r U R' U' M U R U' R'
        r2 D' r U r' D r2 U' r' U' r
        U' r' D' r U' r' D r2 U' r' U r U r'
        R' U' F U R U' R' F' R
        S R U R' U' R' F R f'
        R U R' U' R' F R F'
        U f R f' U' r' U' R U M'
        R U2 R2 F R F' R U2 R'
        U R U R2 F' U' F U R2 U2 R'
        F R' F' R U R U' R'
        R U R' U R U' R' U' R' F R F'
        U' f' r U r' U' r' F r S
        U R' F R U R' U' F' U R
        U2 R U R' U R U2 R' F R U R' U' F'
        R' U' R U' R' U2 R F R U R' U' F'
        U R' U' F' U F R
        f R U R' U' f'
        F R U R' U' F'
        R' U' R' F R F' U R
        U' F R' F' R U2 R U' R' U R U2 R'
        F R U R' U' R U R' U' F'
        U2 r U' r2 U r2 U r2 U' r
        r' U r2 U' r2 U' r2 U r'
        U2 F U R U' R' U R U' R' F'
        U2 R' F' U' F U' R U R' U R
        r' U' R U' R' U R U' R' U2 r
        r U R' U R U' R' U R U2 r'
        U R' F U R U' R2 F' R2 U R' U' R
        r U r' U R U' R' U R U' R' r U' r'
        R U R' U' M' U R U' r'
    """.trimIndent().lines()

    /**
     * OLL algorithms re-expressed for the normalized yellow-top/blue-front
     * state. These are the inverses of the explicit setup sequences, with the
     * same whole-cube normalization applied at both ends. Keeping this list
     * paired with ollSetup guarantees that the 3D playback starts from the
     * diagrammed case and actually performs that case's orientation.
     */
    val oll: List<String> = listOf(
        "R U2 R2 F R F' U2 R' F R F'",
        "F R U R' U' F' f R U R' U' f'",
        "f R U R' U' f' U' F R U R' U' F'",
        "f R U R' U' f' U F R U R' U' F'",
        "r' U2 R U R' U r",
        "r U2 R' U' R U' r'",
        "r U R' U R U2 r'",
        "r' U' R U' R' U2 r",
        "R U R' U' R' F R2 U R' U' F'",
        "R U R' U R' F R F' R U2 R'",
        "r' R2 U R' U R U2 R' U M'",
        "F R U R' U' F' U F R U R' U' F'",
        "r U' r' U' r U r' F' U F",
        "R' F R U R' F' R F U' F'",
        "r' U' r R' U' R U r' U r",
        "r U r' R U R' U' r U' r'",
        "R U R' U R' F R F' U2 R' F R F'",
        "r U R' U R U2 r2 U' R U' R' U2 r",
        "M U R U R' U' M' R' F R F'",
        "M U R U R' U' M2 U R U' r'",
        "R U2 R' U' R U R' U' R U' R'",
        "R U2 R2 U' R2 U' R2 U2 R",
        "R2 D R' U2 R D' R' U2 R'",
        "r U R' U' r' F R F'",
        "F' r U R' U' r' F R",
        "R U2 R' U' R U' R'",
        "R U R' U R U2 R'",
        "r U R' U' M U R U' R'",
        "M U R U R' U' R' F R F' M'",
        "F U R U2 R' U' R U2 R' U' F'",
        "R' U' F U R U' R' F' R",
        "S R U R' U' R' F R f'",
        "R U R' U' R' F R F'",
        "R U R2 U' R' F R U R U' F'",
        "R U2 R2 F R F' R U2 R'",
        "L' U' L U' L' U L U L F' L' F",
        "F R U' R' U' R U R' F'",
        "R U R' U R U' R' U' R' F R F'",
        "L F' L' U' L U F U' L'",
        "R' F R U R' U' F' U R",
        "R U R' U R U2 R' F R U R' U' F'",
        "R' U' R U' R' U2 R F R U R' U' F'",
        "f' L' U' L U f",
        "f R U R' U' f'",
        "F R U R' U' F'",
        "R' U' R' F R F' U R",
        "F' L' U' L U L' U' L U F",
        "F R U R' U' R U R' U' F'",
        "r U' r2 U r2 U r2 U' r",
        "r' U r2 U' r2 U' r2 U r'",
        "f R U R' U' R U R' U' f'",
        "y' R U R' U R d' R U' R' F'",
        "r' U' R U' R' U R U' R' U2 r",
        "r U R' U R U' R' U R U2 r'",
        "F U R U' R' F' R U2 R' U' R U' R'",
        "r U r' U R U' R' U R U' R' r U' r'",
        "R U R' U' M' U R U' r'"
    )

    /**
     * Preferred PLL algorithms from the user's GANCube reference chart.
     * Parentheses in the chart are finger-trick grouping, so this catalog
     * keeps the same move order in a parser-friendly space-separated form.
     */
    val pll: List<String> = listOf(
        // 01 Ua
        "M2 U M U2 M' U M2",
        // 02 Ub
        "M2 U' M U2 M' U' M2",
        // 03 H
        "M2 U M2 U2 M2 U M2",
        // 04 Z
        "M' U M2 U M2 U M' U2 M2 U'",
        // 05 Aa
        "x' R2 D2 R' U' R D2 R' U R'",
        // 06 Ab
        "x' R U' R D2 R' U R D2 R2",
        // 07 E
        "x' R U' R' D R U R' D' R U R' D R U' R' D'",
        // 08 T
        "R U R' U' R' F R2 U' R' U' R U R' F'",
        // 09 F
        "R' U' F' R U R' U' R' F R2 U' R' U' R U R' U R",
        // 10 V
        "R' U R' d' R' F' R2 U' R' U R' F R F",
        // 11 Y
        "F R U' R' U' R U R' F' R U R' U' R' F R F'",
        // 12 Ja
        "z U' R D' R2 U R' U' R2 U D R'",
        // 13 Jb
        "R U R' F' R U R' U' R' F R2 U' R' U'",
        // 14 Rb
        "R' U2 R U' U' R' F R U R' U' R' F' R2 U'",
        // 15 Ra
        "R U' R' U' R U R D R' U' R D' R' U2 R' U'",
        // 16 Gc
        "R2 u' R U' R U R' u R2 f R' f'",
        // 17 Gd
        "R U R' y' R2 u' R U' R' U R' u R2",
        // 18 Ga
        "R2 u R' U R' U' R u' R2 F' U F",
        // 19 Gb
        "R' d' F R2 u R' U R U' R u' R2",
        // 20 Nb
        "R' U R U' R' F' U' F R U R' F R' F' R U' R",
        // 21 Na
        "R U R' U R U R' F' R U R' U' R' F R2 U' R' U2 R U' R'"
    )
}
