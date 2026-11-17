package com.example.contacts.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.contacts.R
import com.example.contacts.data.Contact
import com.example.contacts.data.PreferencesManager
import com.example.contacts.databinding.ItemContactBinding
import com.example.contacts.util.PhotoStorage
import android.util.TypedValue

class ContactListAdapter(
    private val onContactClick: (Contact) -> Unit,
    private val onCallClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactListAdapter.ContactViewHolder>(Diff) {

    private var preferences = PreferencesManager.UiPreferences(16f, Color.BLACK, false)

    /** Reçoit des valeurs déjà converties par PreferencesManager. */
    fun applyPreferences(newPreferences: PreferencesManager.UiPreferences) {
        preferences = newPreferences
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.tvNom.text = contact.nom
            binding.tvPrenom.text = contact.prenom
            binding.tvTelephone.text = contact.telephone

            listOf<TextView>(binding.tvNom, binding.tvPrenom, binding.tvTelephone).forEach { view ->
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, preferences.textSizeSp)
            }
            binding.tvNom.setTextColor(preferences.textColor)
            binding.tvPrenom.setTextColor(preferences.textColor)

            binding.btnCall.visibility =
                if (preferences.showCallButton) View.VISIBLE else View.GONE

            val bitmap = PhotoStorage.loadBitmap(contact.photoUri)
            if (bitmap != null) binding.ivPhoto.setImageBitmap(bitmap)
            else binding.ivPhoto.setImageResource(R.drawable.ic_person)

            binding.root.setOnClickListener { onContactClick(contact) }
            binding.btnCall.setOnClickListener { onCallClick(contact) }
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(oldItem: Contact, newItem: Contact) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Contact, newItem: Contact) = oldItem == newItem
        }
    }
}
