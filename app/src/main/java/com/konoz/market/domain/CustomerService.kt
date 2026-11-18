package com.konoz.market.domain

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Customer
import kotlinx.coroutines.flow.Flow

enum class CustomerError { NAME_REQUIRED, INVALID_PHONE, PHONE_TAKEN, NOT_FOUND, FAILED }

class CustomerException(val error: CustomerError) : Exception(error.name)

/**
 * The customer list and the link between a sent invoice and its customer.
 *
 * The list is keyed by phone (WhatsApp digits): saving a number that already exists updates that customer's name.
 * An invoice keeps its own copy of the name and phone it was sent with, so later edits or deletes in the list never
 * change an old invoice.
 */
class CustomerService(private val db: KonozDatabase, private val now: () -> Long = System::currentTimeMillis) {

    fun observeAll(): Flow<List<Customer>> = db.customers().observeAll()

    /** The saved customer for what was typed as a phone number; null when it is not a valid number or not saved. */
    suspend fun findByPhone(input: String): Customer? {
        val phone = PhoneNumbers.toWhatsApp(input) ?: return null
        return db.customers().getByPhone(phone)
    }

    /** Adds a customer from the list screen. A phone that is already saved is refused (PHONE_TAKEN). */
    suspend fun add(name: String, phoneInput: String): Customer {
        val cleanName = cleanName(name)
        val phone = normalizePhone(phoneInput)
        return db.tx {
            if (db.customers().getByPhone(phone) != null) throw CustomerException(CustomerError.PHONE_TAKEN)
            val time = now()
            val customer = Customer(name = cleanName, phone = phone, createdAt = time, updatedAt = time)
            customer.copy(id = db.customers().insert(customer))
        }
    }

    /** Edits a saved customer. Another customer's phone is refused (PHONE_TAKEN). Sent invoices are not changed. */
    suspend fun update(id: Long, name: String, phoneInput: String): Customer {
        val cleanName = cleanName(name)
        val phone = normalizePhone(phoneInput)
        return db.tx {
            val existing = db.customers().getById(id) ?: throw CustomerException(CustomerError.NOT_FOUND)
            val other = db.customers().getByPhone(phone)
            if (other != null && other.id != id) throw CustomerException(CustomerError.PHONE_TAKEN)
            val updated = existing.copy(name = cleanName, phone = phone, updatedAt = now())
            db.customers().update(updated)
            updated
        }
    }

    /** Removes the customer from the list only; invoices already sent keep the name they were sent with. */
    suspend fun delete(id: Long) = db.customers().delete(id)

    /**
     * Called when a completed invoice is about to be sent. Records [name] (and the phone, when given) on the invoice and,
     * when there is a phone, saves or updates that customer in the list; both happen in one transaction.
     *
     * - Both empty: nothing is recorded (null).
     * - A phone needs a name (NAME_REQUIRED) and must be a valid mobile number (INVALID_PHONE).
     * - A name alone is recorded on the invoice only; the list is keyed by phone so nobody is added to it.
     * - An unknown or not completed invoice gives NOT_FOUND and nothing is saved.
     */
    suspend fun saveForInvoice(invoiceId: Long, name: String, phoneInput: String): Customer? {
        val cleaned = name.trim().replace(WHITESPACE, " ").take(MAX_NAME)
        val hasPhone = phoneInput.isNotBlank()
        if (!hasPhone && cleaned.isEmpty()) return null
        if (cleaned.isEmpty()) throw CustomerException(CustomerError.NAME_REQUIRED)
        val phone = if (hasPhone) normalizePhone(phoneInput) else null
        return db.tx {
            val saved = phone?.let { upsert(cleaned, it) }
            if (db.customers().setInvoiceCustomer(invoiceId, cleaned, phone) == 0) throw CustomerException(CustomerError.NOT_FOUND)
            saved
        }
    }

    private suspend fun upsert(name: String, phone: String): Customer {
        val time = now()
        val existing = db.customers().getByPhone(phone)
        if (existing == null) {
            val customer = Customer(name = name, phone = phone, createdAt = time, updatedAt = time)
            return customer.copy(id = db.customers().insert(customer))
        }
        val updated = existing.copy(name = name, updatedAt = time)
        db.customers().update(updated)
        return updated
    }

    private fun cleanName(name: String): String {
        val cleaned = name.trim().replace(WHITESPACE, " ").take(MAX_NAME)
        if (cleaned.isEmpty()) throw CustomerException(CustomerError.NAME_REQUIRED)
        return cleaned
    }

    private fun normalizePhone(input: String): String =
        PhoneNumbers.toWhatsApp(input) ?: throw CustomerException(CustomerError.INVALID_PHONE)

    companion object {
        const val MAX_NAME = 60
        private val WHITESPACE = Regex("\\s+")

        /** The stored digits as a person writes them: 01012345678 for Egypt, +966... for other countries. */
        fun displayPhone(stored: String): String =
            if (stored.startsWith("20") && stored.length == 12) "0" + stored.drop(2) else "+$stored"
    }
}
