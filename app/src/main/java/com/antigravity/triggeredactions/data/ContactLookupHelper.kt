package com.antigravity.triggeredactions.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Resolved metadata for an incoming or outgoing phone number / SMS sender against the device Contacts book.
 */
data class ContactLookupResult(
	val boolIsInContacts: Boolean,
	val stringDisplayName: String,
	val boolIsStarred: Boolean
)

/**
 * Queries the Android Contacts provider to resolve phone numbers into known contacts and favorite status.
 */
object ContactLookupHelper {

	private const val TAG = "ContactLookupHelper"

	/**
	 * Looks up a phone number in [ContactsContract.PhoneLookup] if `READ_CONTACTS` is granted.
	 *
	 * @param objContext Application or receiver context.
	 * @param stringPhoneNumber Phone number or sender address to inspect.
	 * @return [ContactLookupResult] indicating contact membership, display name, and starred status.
	 */
	fun lookupPhoneNumber(objContext: Context, stringPhoneNumber: String): ContactLookupResult {
		val stringTrimmed = stringPhoneNumber.trim()
		if (stringTrimmed.isEmpty()) {
			return ContactLookupResult(boolIsInContacts = false, stringDisplayName = "", boolIsStarred = false)
		}

		val boolHasContactsPerm = ContextCompat.checkSelfPermission(
			objContext,
			Manifest.permission.READ_CONTACTS
		) == PackageManager.PERMISSION_GRANTED
		if (!boolHasContactsPerm) {
			return ContactLookupResult(boolIsInContacts = false, stringDisplayName = "", boolIsStarred = false)
		}

		return try {
			val uriLookup = Uri.withAppendedPath(
				ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
				Uri.encode(stringTrimmed)
			)
			val arrayProjection = arrayOf(
				ContactsContract.PhoneLookup.DISPLAY_NAME,
				ContactsContract.PhoneLookup.STARRED
			)
			objContext.contentResolver.query(uriLookup, arrayProjection, null, null, null)?.use { objCursor ->
				if (objCursor.moveToFirst()) {
					val intNameIdx = objCursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
					val intStarredIdx = objCursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
					val stringName = if (intNameIdx >= 0) objCursor.getString(intNameIdx).orEmpty() else ""
					val boolStarred = intStarredIdx >= 0 && objCursor.getInt(intStarredIdx) == 1
					return ContactLookupResult(
						boolIsInContacts = true,
						stringDisplayName = stringName,
						boolIsStarred = boolStarred
					)
				}
			}
			ContactLookupResult(boolIsInContacts = false, stringDisplayName = "", boolIsStarred = false)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[ContactLookupHelper] Error querying contact for $stringTrimmed - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			ContactLookupResult(boolIsInContacts = false, stringDisplayName = "", boolIsStarred = false)
		}
	}
}
