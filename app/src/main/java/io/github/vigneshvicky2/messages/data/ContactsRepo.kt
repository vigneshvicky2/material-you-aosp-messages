package io.github.vigneshvicky2.messages.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import java.util.concurrent.ConcurrentHashMap

class ContactsRepo(private val ctx: Context) {
    private val cache = ConcurrentHashMap<String, Contact>()

    private fun granted(p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun clearCache() = cache.clear()

    fun lookup(address: String): Contact {
        if (Demo.on) return Demo.lookup(address) ?: Contact(Classifier.prettySender(address).takeIf { Classifier.isBusinessSender(address) }, address)
        cache[address]?.let { return it }
        var c = Contact(name = null, number = address)
        if (granted(Manifest.permission.READ_CONTACTS) && address.any { it.isDigit() }) {
            runCatching {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
                ctx.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                        ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ),
                    null, null, null,
                )?.use { cur ->
                    if (cur.moveToFirst()) {
                        c = Contact(
                            name = cur.getString(0),
                            number = address,
                            photoUri = cur.getString(1)?.let(Uri::parse),
                            lookupKey = cur.getString(2),
                        )
                    }
                }
            }
        }
        if (c.name == null && Classifier.isBusinessSender(address)) {
            c = c.copy(name = Classifier.prettySender(address))
        }
        cache[address] = c
        return c
    }

    fun isSavedContact(address: String): Boolean = lookup(address).lookupKey != null

    /** All phone contacts, sorted, deduped by normalized number. */
    fun all(): List<Contact> {
        if (Demo.on) return Demo.contacts
        if (!granted(Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = LinkedHashMap<String, Contact>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
        )?.use { cur ->
            while (cur.moveToNext()) {
                val num = cur.getString(1) ?: continue
                val norm = PhoneNumberUtils.normalizeNumber(num).takeLast(10)
                if (out.containsKey(norm)) continue
                out[norm] = Contact(cur.getString(0), num, cur.getString(2)?.let(Uri::parse), cur.getString(3))
            }
        }
        return out.values.toList()
    }
}

class SimRepo(private val ctx: Context) {
    @SuppressLint("MissingPermission")
    fun sims(): List<SimInfo> {
        if (Demo.on) return Demo.sims
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map {
                SimInfo(
                    subId = it.subscriptionId,
                    slot = it.simSlotIndex,
                    name = "SIM ${it.simSlotIndex + 1}",
                    carrier = it.displayName?.toString() ?: it.carrierName?.toString() ?: "",
                    color = it.iconTint,
                )
            }.sortedBy { it.slot }
        }.getOrDefault(emptyList())
    }

    fun defaultSmsSubId(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()
}
