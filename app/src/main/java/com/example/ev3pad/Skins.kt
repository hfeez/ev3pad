package com.example.ev3pad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser

/** Jenis controller yang gambarnya didukung. */
enum class CType {
    DS4, XBOX, JOYCON;

    fun title(): String = when (this) { DS4 -> "DualShock 4"; XBOX -> "Xbox"; JOYCON -> "Joy-Con (L+R)" }
}

/** Gaya gambar tiap bagian controller. */
enum class St { BODY, REC, REC2, LINE, DEC, DEC2, KEY, RING, FACE, XB, ICON, ICONI, MK, DOTS, LABEL }

/** Bentuk: path SVG, lingkaran, atau persegi bersudut bulat. Koordinat dalam satuan viewBox skin. */
class Shp private constructor(val kind: Int, val d: String, val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) {
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
    private val region: android.graphics.Region by lazy {
        val rg = android.graphics.Region()
        rg.setPath(path.asAndroidPath(), android.graphics.Region(-300, -300, 3300, 2700))
        rg
    }

    fun contains(px: Float, py: Float, tol: Float = 6f): Boolean = when (kind) {
        0 -> region.contains(px.toInt(), py.toInt())
        1 -> (px - x) * (px - x) + (py - y) * (py - y) <= (r + tol) * (r + tol)
        2 -> px in (x - tol)..(x + w + tol) && py in (y - tol)..(y + h + tol)
        else -> false
    }

    companion object {
        fun path(d: String) = Shp(0, d, 0f, 0f, 0f, 0f, 0f)
        fun circle(x: Float, y: Float, r: Float) = Shp(1, "", x, y, 0f, 0f, r)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) = Shp(2, "", x, y, w, h, r)
        val none = Shp(3, "", 0f, 0f, 0f, 0f, 0f)
    }
}

/**
 * Satu elemen gambar. pad != null -> bisa disentuh dan menyala saat ditekan.
 * cap != null -> elemen ikut bergeser mengikuti stik tersebut (tutup stik).
 */
class SItem(
    val st: St, val shp: Shp, val pad: Pad?, val color: Color?, val label: String?,
    val lx: Float, val ly: Float, val ls: Float, val cap: Pad?, val sym: Shp?, val symSt: St
)

class Skin(
    val vx: Float, val vy: Float, val vw: Float, val vh: Float,
    val items: List<SItem>, val travel: Float, val radius: Float, val centers: Map<Pad, Offset>
) {
    /** Elemen yang bisa disentuh, urutan dari paling atas. */
    val keys: List<SItem> = items.filter { it.pad != null }.asReversed()
}

private val cache = HashMap<CType, Skin>()
fun skinFor(t: CType): Skin = cache.getOrPut(t) { when (t) { CType.DS4 -> ds4(); CType.XBOX -> xbox(); CType.JOYCON -> joycon() } }

private fun mir(d: String, axis: Float): String = Regex("(-?\\d+\\.?\\d*) (-?\\d+\\.?\\d*)").replace(d) { m ->
    "${axis - m.groupValues[1].toFloat()} ${m.groupValues[2]}"
}

private fun k(st: St, shp: Shp, pad: Pad, label: String? = null, lx: Float = 0f, ly: Float = 0f, ls: Float = 44f,
              color: Color? = null, sym: Shp? = null, symSt: St = St.ICON, cap: Pad? = null) =
    SItem(st, shp, pad, color, label, lx, ly, ls, cap, sym, symSt)

private fun d(st: St, shp: Shp, cap: Pad? = null, label: String? = null, lx: Float = 0f, ly: Float = 0f, ls: Float = 44f) =
    SItem(st, shp, null, null, label, lx, ly, ls, cap, null, St.ICON)

private class FB(val pad: Pad, val x: Float, val y: Float, val c: Color, val ch: String = "")
private class AR(val pad: Pad, val x: Float, val y: Float, val w: Float, val h: Float)

private val C_TRI = Color(0xFF3DE0C0); private val C_SQR = Color(0xFFD27AF2)
private val C_CIR = Color(0xFFFF6B7A); private val C_CRS = Color(0xFF6A8FFF)

// ============================================================ DualShock 4
private const val OUT_DS4 = "M290 460 L272 496 L238 549 L218 586 L202 625 L202 630 L199 634 L188 668 L183 689 L177 702 L176 711 L173 715 L171 729 L157 774 L128 890 L101 1013 L97 1040 L93 1051 L80 1127 L77 1134 L63 1225 L61 1251 L56 1275 L48 1353 L46 1394 L46 1488 L53 1559 L67 1612 L87 1652 L116 1686 L144 1712 L159 1721 L198 1738 L243 1747 L292 1749 L321 1745 L362 1732 L385 1721 L418 1696 L432 1683 L453 1658 L488 1602 L524 1524 L528 1509 L535 1495 L539 1479 L582 1359 L595 1314 L605 1298 L630 1278 L636 1275 L645 1275 L678 1299 L719 1317 L753 1325 L788 1328 L817 1326 L842 1321 L871 1311 L908 1290 L938 1266 L1325 1266 L1357 1291 L1396 1312 L1423 1321 L1450 1326 L1483 1328 L1515 1325 L1550 1316 L1578 1304 L1616 1278 L1624 1278 L1647 1293 L1656 1303 L1662 1314 L1691 1410 L1705 1444 L1706 1452 L1715 1473 L1722 1497 L1759 1585 L1762 1587 L1770 1603 L1772 1611 L1778 1618 L1790 1641 L1810 1668 L1850 1707 L1888 1730 L1923 1742 L1968 1749 L2021 1747 L2060 1738 L2093 1724 L2113 1711 L2138 1690 L2161 1663 L2182 1626 L2196 1585 L2207 1519 L2209 1455 L2209 1384 L2205 1314 L2190 1204 L2184 1175 L2184 1162 L2178 1141 L2178 1132 L2166 1068 L2156 1025 L2153 1002 L2128 902 L2120 860 L2108 818 L2108 810 L2087 730 L2084 726 L2055 628 L2042 599 L2042 595 L2026 563 L1993 511 L1987 498 L1984 496 L1965 459 L1953 445 L1936 434 L1925 412 L1913 403 L1878 388 L1838 378 L1800 373 L1746 374 L1717 379 L1674 390 L1662 398 L1658 405 L1656 420 L1616 441 L1589 449 L1485 449 L1473 436 L1450 432 L802 432 L787 436 L772 449 L663 449 L641 442 L622 430 L605 424 L602 420 L600 403 L595 396 L586 391 L559 384 L552 380 L501 373 L466 373 L445 375 L395 385 L362 395 L347 402 L326 417 L322 422 L318 437 L303 447 Z"

private fun ds4(): Skin {
    val l2 = "M345 392 C350 330 400 295 470 292 C530 292 575 320 590 385 Z"
    val l1 = "M332 452 C332 408 365 384 420 382 L520 384 C575 386 600 408 602 452 Z"
    val gr = "M66 1432 C130 1565 420 1628 500 1592"
    val a = 2256f
    val l = ArrayList<SItem>()
    l += k(St.KEY, Shp.path(l2), Pad.L2, "L2", 467f, 352f, 52f)
    l += k(St.KEY, Shp.path(mir(l2, a)), Pad.R2, "R2", 1789f, 352f, 52f)
    l += d(St.BODY, Shp.path(OUT_DS4))
    l += d(St.LINE, Shp.path(gr)); l += d(St.LINE, Shp.path(mir(gr, a)))
    l += k(St.KEY, Shp.path(l1), Pad.L1, "L1", 467f, 425f, 46f)
    l += k(St.KEY, Shp.path(mir(l1, a)), Pad.R1, "R1", 1789f, 425f, 46f)
    l += d(St.REC, Shp.circle(458f, 797f, 262f)); l += d(St.REC, Shp.circle(1798f, 797f, 262f))
    l += k(St.KEY, Shp.rect(780f, 442f, 700f, 436f, 44f), Pad.TP)
    l += d(St.DOTS, Shp.rect(815f, 570f, 630f, 280f, 0f))
    l += d(St.LABEL, Shp.none, label = "TOUCHPAD", lx = 1130f, ly = 505f, ls = 40f)
    l += k(St.KEY, Shp.rect(659f, 522f, 66f, 114f, 33f), Pad.SHARE)
    l += k(St.KEY, Shp.rect(1531f, 522f, 66f, 114f, 33f), Pad.OPT)
    l += d(St.DEC, Shp.path("M458 574 L486 614 H430 Z M458 1020 L486 980 H430 Z M240 797 L280 769 V825 Z M676 797 L636 769 V825 Z"))
    for (r in listOf(AR(Pad.UP, 402f, 622f, 108f, 150f), AR(Pad.DOWN, 402f, 822f, 108f, 150f), AR(Pad.LEFT, 283f, 742f, 150f, 112f), AR(Pad.RIGHT, 482f, 742f, 150f, 112f)))
        l += k(St.KEY, Shp.rect(r.x, r.y, r.w, r.h, 30f), r.pad)
    for (f in listOf(FB(Pad.TRI, 1798f, 640f, C_TRI), FB(Pad.SQR, 1642f, 798f, C_SQR), FB(Pad.CIR, 1954f, 798f, C_CIR), FB(Pad.CRS, 1798f, 958f, C_CRS))) {
        val px = f.x; val py = f.y
        val sym = when (f.pad) {
            Pad.TRI -> Shp.path("M$px ${py - 36f} L${px + 40f} ${py + 28f} H${px - 40f} Z")
            Pad.SQR -> Shp.rect(px - 32f, py - 32f, 64f, 64f, 0f)
            Pad.CIR -> Shp.circle(px, py, 35f)
            else -> Shp.path("M${px - 30f} ${py - 30f} L${px + 30f} ${py + 30f} M${px + 30f} ${py - 30f} L${px - 30f} ${py + 30f}")
        }
        l += k(St.FACE, Shp.circle(px, py, 76f), f.pad, color = f.c, sym = sym, symSt = St.FACE)
    }
    for ((ring, cap, x) in listOf(Triple(Pad.LS, Pad.L3, 788f), Triple(Pad.RS, Pad.R3, 1482f))) {
        l += k(St.RING, Shp.circle(x, 1101f, 198f), ring)
        l += k(St.KEY, Shp.circle(x, 1101f, 136f), cap, if (cap == Pad.L3) "L3" else "R3", x, 1101f, 50f, cap = ring)
        l += d(St.LINE, Shp.circle(x, 1101f, 94f), cap = ring)
    }
    l += k(St.KEY, Shp.circle(1130f, 1104f, 62f), Pad.PS, "PS", 1130f, 1104f, 44f)
    for ((n, y) in listOf(5 to 941f, 4 to 970f, 3 to 999f)) for (i in 0 until n) l += d(St.REC, Shp.circle(1130f + (i - (n - 1) / 2f) * 42f, y, 11f))
    return Skin(20f, 280f, 2230f, 1490f, l, 55f, 110f, mapOf(Pad.LS to Offset(788f, 1101f), Pad.RS to Offset(1482f, 1101f)))
}

// ============================================================ Xbox
private const val OUT_XBOX = "M442 316 L422 350 L411 382 L396 396 L374 433 L358 478 L355 481 L351 495 L345 505 L345 510 L342 513 L332 538 L331 547 L327 553 L319 578 L315 583 L312 596 L304 614 L303 623 L300 626 L289 662 L284 671 L281 686 L274 701 L274 706 L268 719 L244 794 L240 814 L231 836 L230 847 L220 872 L207 927 L204 930 L198 958 L195 963 L192 981 L188 987 L186 1007 L177 1037 L175 1055 L164 1096 L160 1124 L156 1131 L156 1144 L146 1196 L134 1300 L132 1374 L137 1438 L148 1492 L164 1535 L186 1574 L219 1611 L249 1632 L253 1637 L258 1637 L284 1652 L311 1663 L318 1663 L329 1668 L349 1671 L375 1669 L411 1652 L413 1648 L434 1637 L449 1622 L453 1621 L517 1556 L518 1552 L532 1538 L543 1524 L543 1521 L549 1516 L591 1460 L591 1457 L608 1437 L614 1425 L619 1421 L653 1372 L689 1329 L712 1308 L737 1290 L761 1281 L817 1271 L863 1268 L1535 1268 L1583 1271 L1623 1277 L1662 1290 L1693 1312 L1697 1318 L1711 1329 L1712 1333 L1735 1357 L1740 1366 L1748 1374 L1787 1430 L1797 1441 L1810 1463 L1882 1555 L1946 1620 L1950 1621 L1963 1634 L1982 1645 L1997 1657 L2022 1668 L2049 1672 L2088 1663 L2110 1655 L2122 1648 L2126 1648 L2128 1645 L2143 1637 L2147 1637 L2152 1631 L2171 1619 L2173 1615 L2179 1612 L2203 1588 L2212 1576 L2215 1569 L2219 1566 L2220 1561 L2227 1553 L2248 1504 L2260 1458 L2267 1397 L2267 1327 L2259 1232 L2256 1222 L2249 1164 L2212 992 L2208 984 L2204 961 L2184 894 L2180 873 L2175 863 L2174 854 L2163 817 L2160 814 L2159 805 L2147 771 L2146 763 L2137 740 L2131 715 L2128 712 L2126 701 L2119 686 L2116 668 L2112 664 L2110 654 L2105 644 L2105 638 L2102 635 L2092 604 L2085 590 L2084 582 L2073 558 L2073 551 L2057 516 L2055 505 L2051 496 L2048 494 L2048 488 L2042 478 L2025 432 L2007 400 L1989 382 L1979 353 L1957 315 L1923 281 L1902 266 L1873 249 L1870 245 L1864 244 L1847 234 L1841 233 L1822 223 L1817 223 L1809 218 L1767 207 L1764 204 L1745 202 L1716 195 L1665 191 L1663 189 L1628 188 L1607 192 L1584 204 L1582 208 L1578 208 L1575 213 L1564 219 L1558 226 L1541 235 L1488 237 L1486 239 L1213 235 L1056 236 L991 239 L873 237 L858 235 L845 228 L837 220 L820 208 L817 208 L815 204 L798 194 L771 188 L737 189 L735 191 L710 192 L635 204 L629 208 L609 212 L596 218 L577 223 L529 246 L526 250 L506 260 L496 267 L494 271 L491 271 L476 282 Z"

private fun xbox(): Skin {
    val lb = "M418 378 C470 288 600 214 764 197 C805 197 830 225 862 247 C700 256 520 292 418 378 Z"
    val lt = "M520 228 C570 140 690 108 792 126 C816 134 826 164 812 200 C720 190 600 200 520 228 Z"
    val gl = "M363 1659 C500 1500 620 1360 700 1290"
    val a = 2400f
    val l = ArrayList<SItem>()
    l += k(St.KEY, Shp.path(lt), Pad.L2, "LT", 690f, 172f, 50f)
    l += k(St.KEY, Shp.path(mir(lt, a)), Pad.R2, "RT", 1710f, 172f, 50f)
    l += d(St.BODY, Shp.path(OUT_XBOX))
    l += d(St.LINE, Shp.path(gl)); l += d(St.LINE, Shp.path(mir(gl, a)))
    l += d(St.LINE, Shp.path("M470 292 C700 238 900 230 1200 230 C1500 230 1700 238 1930 292"))
    l += k(St.KEY, Shp.path(lb), Pad.L1, "LB", 650f, 256f, 44f)
    l += k(St.KEY, Shp.path(mir(lb, a)), Pad.R1, "RB", 1750f, 256f, 44f)
    l += d(St.REC2, Shp.circle(624f, 634f, 200f)); l += d(St.REC2, Shp.circle(1486f, 960f, 195f))
    for ((ring, cap, p) in listOf(Triple(Pad.LS, Pad.L3, Offset(631f, 613f)), Triple(Pad.RS, Pad.R3, Offset(1486f, 941f)))) {
        l += k(St.RING, Shp.circle(p.x, p.y, 138f), ring)
        l += k(St.KEY, Shp.circle(p.x, p.y, 102f), cap, if (cap == Pad.L3) "L3" else "R3", p.x, p.y, 44f, cap = ring)
        l += d(St.LINE, Shp.circle(p.x, p.y, 70f), cap = ring)
    }
    l += d(St.REC, Shp.circle(909f, 965f, 180f)); l += d(St.DEC2, Shp.rect(857f, 911f, 108f, 108f, 0f))
    l += k(St.KEY, Shp.rect(857f, 813f, 108f, 98f, 18f), Pad.UP); l += k(St.KEY, Shp.rect(857f, 1019f, 108f, 98f, 18f), Pad.DOWN)
    l += k(St.KEY, Shp.rect(759f, 911f, 98f, 108f, 18f), Pad.LEFT); l += k(St.KEY, Shp.rect(965f, 911f, 98f, 108f, 18f), Pad.RIGHT)
    for (f in listOf(FB(Pad.TRI, 1770f, 472f, Color(0xFF5DE27A), "Y"), FB(Pad.CIR, 1920f, 618f, Color(0xFFFF6B6B), "B"), FB(Pad.SQR, 1619f, 618f, Color(0xFF5B8CFF), "X"), FB(Pad.CRS, 1770f, 768f, Color(0xFF4DE8C0), "A")))
        l += k(St.FACE, Shp.circle(f.x, f.y, 80f), f.pad, f.ch, f.x, f.y + 4f, 84f, f.c)
    l += k(St.XB, Shp.circle(1200f, 396f, 86f), Pad.PS, sym = Shp.path("M1160 356 C1190 392 1212 402 1240 438 M1240 356 C1212 392 1190 402 1160 438"), symSt = St.MK)
    l += k(St.KEY, Shp.circle(1037f, 616f, 52f), Pad.SHARE, sym = Shp.path("M1018 600 h24 v20 h-24 Z M1032 612 h22 v22 h-22"))
    l += k(St.KEY, Shp.circle(1363f, 616f, 52f), Pad.OPT, sym = Shp.path("M1340 600 h46 M1340 616 h46 M1340 632 h46"))
    l += k(St.KEY, Shp.rect(1134f, 699f, 130f, 76f, 38f), Pad.TP, sym = Shp.path("M1170 742 v14 h58 v-14 M1199 748 v-30 m-10 10 l10 -10 l10 10"))
    return Skin(100f, 90f, 2200f, 1620f, l, 36f, 90f, mapOf(Pad.LS to Offset(631f, 613f), Pad.RS to Offset(1486f, 941f)))
}

// ============================================================ Joy-Con (kiri + kanan)
private const val OUT_JL = "M644 196 L607 204 L597 209 L586 211 L575 217 L568 218 L563 223 L556 224 L534 235 L531 239 L523 242 L491 264 L442 312 L412 356 L396 388 L397 399 L404 410 L408 412 L408 420 L401 439 L399 452 L396 456 L389 529 L389 1379 L391 1411 L396 1434 L396 1444 L399 1447 L404 1470 L419 1507 L432 1532 L457 1566 L458 1570 L474 1588 L508 1620 L519 1625 L530 1635 L542 1641 L544 1644 L555 1648 L557 1651 L577 1659 L582 1663 L590 1664 L592 1667 L606 1670 L620 1676 L650 1683 L664 1683 L677 1686 L873 1686 L886 1679 L894 1662 L894 1538 L903 1537 L913 1543 L926 1543 L937 1536 L941 1523 L941 1292 L934 1271 L934 1262 L940 1253 L940 1151 L934 1143 L934 1131 L940 1119 L941 699 L940 687 L934 672 L934 664 L940 659 L940 552 L934 544 L934 535 L938 527 L941 510 L940 304 L937 297 L927 290 L896 290 L894 288 L894 241 L892 232 L885 223 L875 217 L796 216 L793 214 L789 200 L783 195 L773 192 L668 193 Z"
private const val OUT_JR = "M1757 196 L1711 192 L1628 192 L1616 196 L1610 202 L1606 216 L1526 217 L1515 225 L1508 236 L1507 288 L1472 291 L1468 293 L1461 302 L1460 515 L1467 536 L1467 544 L1461 552 L1461 654 L1467 665 L1467 673 L1462 684 L1460 696 L1460 1107 L1463 1125 L1467 1132 L1467 1142 L1464 1144 L1461 1152 L1460 1251 L1462 1257 L1467 1262 L1467 1271 L1461 1289 L1460 1526 L1466 1539 L1472 1542 L1486 1543 L1494 1540 L1499 1535 L1508 1536 L1508 1668 L1520 1683 L1527 1686 L1707 1687 L1746 1684 L1756 1680 L1791 1673 L1843 1652 L1891 1621 L1926 1589 L1955 1554 L1974 1520 L1977 1518 L1984 1499 L1988 1494 L1998 1466 L2007 1430 L2011 1399 L2012 544 L2009 482 L2003 450 L2000 446 L2000 439 L1993 421 L1993 410 L1999 408 L2004 399 L2004 387 L1993 363 L1983 351 L1983 347 L1973 331 L1968 328 L1957 310 L1951 306 L1947 299 L1912 266 L1875 240 L1872 240 L1855 228 L1841 224 L1838 220 L1792 204 Z"

private fun joycon(): Skin {
    val sh = "M404 410 C412 305 520 208 690 198 L792 198 L792 224 C650 226 530 262 462 384 Z"
    val zl = "M606 198 L628 146 L784 146 L792 198 Z"
    val a = 2402f
    val l = ArrayList<SItem>()
    l += k(St.KEY, Shp.path(zl), Pad.L2, "ZL", 703f, 174f, 40f)
    l += k(St.KEY, Shp.path(mir(zl, a)), Pad.R2, "ZR", 1699f, 174f, 40f)
    l += d(St.BODY, Shp.path(OUT_JL)); l += d(St.BODY, Shp.path(OUT_JR))
    l += k(St.KEY, Shp.path(sh), Pad.L1, "L", 700f, 214f, 36f)
    l += k(St.KEY, Shp.path(mir(sh, a)), Pad.R1, "R", 1702f, 214f, 36f)
    for ((ring, cap, p) in listOf(Triple(Pad.LS, Pad.L3, Offset(656f, 582f)), Triple(Pad.RS, Pad.R3, Offset(1746f, 1003f)))) {
        l += k(St.RING, Shp.circle(p.x, p.y, 122f), ring)
        l += k(St.KEY, Shp.circle(p.x, p.y, 88f), cap, if (cap == Pad.L3) "L3" else "R3", p.x, p.y, 40f, cap = ring)
        l += d(St.LINE, Shp.circle(p.x, p.y, 60f), cap = ring)
    }
    for (r in listOf(Triple(Pad.UP, Offset(657f, 881f), "▲"), Triple(Pad.LEFT, Offset(539f, 996f), "◀"), Triple(Pad.RIGHT, Offset(775f, 996f), "▶"), Triple(Pad.DOWN, Offset(657f, 1112f), "▼")))
        l += k(St.KEY, Shp.circle(r.second.x, r.second.y, 60f), r.first, r.third, r.second.x, r.second.y + 2f, 40f)
    l += k(St.KEY, Shp.rect(766f, 345f, 82f, 27f, 13f), Pad.SHARE)
    l += k(St.KEY, Shp.rect(694f, 1231f, 97f, 94f, 16f), Pad.TP)
    l += d(St.LINE, Shp.circle(742f, 1278f, 24f))
    for (f in listOf(FB(Pad.TRI, 1746f, 477f, Color(0xFF5EE8FF), "X"), FB(Pad.SQR, 1625f, 593f, Color(0xFF6EF0B0), "Y"), FB(Pad.CIR, 1865f, 593f, Color(0xFFFF6B7A), "A"), FB(Pad.CRS, 1746f, 708f, Color(0xFF8E96FF), "B")))
        l += k(St.FACE, Shp.circle(f.x, f.y, 62f), f.pad, f.ch, f.x, f.y + 3f, 66f, f.c)
    l += k(St.KEY, Shp.rect(1548f, 312f, 92f, 92f, 20f), Pad.OPT, sym = Shp.path("M1594 334 v48 M1570 358 h48"))
    l += k(St.KEY, Shp.circle(1659f, 1280f, 62f), Pad.PS, sym = Shp.path("M1635 1282 L1659 1260 L1683 1282 M1642 1278 v24 h34 v-24"), symSt = St.ICONI)
    return Skin(370f, 120f, 1670f, 1590f, l, 30f, 80f, mapOf(Pad.LS to Offset(656f, 582f), Pad.RS to Offset(1746f, 1003f)))
}
