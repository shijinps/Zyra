package com.zyra.app.data

import android.content.Context
import android.provider.ContactsContract

data class Contact(val id: String, val name: String, val phoneNumber: String)

class ContactRepository(private val context: Context) {
    fun getContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                if (idIndex != -1 && nameIndex != -1 && phoneIndex != -1) {
                    contacts.add(
                        Contact(
                            id = cursor.getString(idIndex),
                            name = cursor.getString(nameIndex),
                            phoneNumber = cursor.getString(phoneIndex)
                        )
                    )
                }
            }
        }
        return contacts
    }
}
