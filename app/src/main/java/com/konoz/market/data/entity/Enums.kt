package com.konoz.market.data.entity

enum class UnitType(val isFractional: Boolean) {
    PIECE(false), CARTON(false), KG(true), GRAM(true), LITER(true), PACK(false)
}

enum class MovementType {
    OPENING, PURCHASE, SALE, RETURN_IN, PURCHASE_RETURN,
    ADJUSTMENT, DAMAGED, EXPIRED, UNIT_CONVERSION, COUNT_ADJUSTMENT
}

enum class InvoiceStatus { HELD, COMPLETED, VOIDED }
enum class PaymentMethod { CASH, CARD, WALLET }
enum class PurchaseStatus { DRAFT, APPROVED, CANCELLED }
enum class ExpenseCategory {
    ELECTRICITY, WATER, RENT, WAGES, TRANSPORT, MAINTENANCE, OPERATING_PURCHASES, MISC
}
enum class CountStatus { DRAFT, APPROVED }
enum class CountScope { ALL, CATEGORY }
enum class ShiftStatus { OPEN, CLOSED }
enum class UserRole { ADMIN, CASHIER, STOCK_KEEPER }
enum class ReturnCondition { RESELLABLE, DAMAGED }
