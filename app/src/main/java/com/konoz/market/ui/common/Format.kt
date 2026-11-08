package com.konoz.market.ui.common

import androidx.annotation.StringRes
import com.konoz.market.R
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.UnitType

fun formatQty(milli: Long): String = Quantities.format(milli)

@StringRes
fun unitLabelRes(unit: UnitType): Int = when (unit) {
    UnitType.PIECE -> R.string.unit_piece
    UnitType.CARTON -> R.string.unit_carton
    UnitType.KG -> R.string.unit_kg
    UnitType.GRAM -> R.string.unit_gram
    UnitType.LITER -> R.string.unit_liter
    UnitType.PACK -> R.string.unit_pack
}
