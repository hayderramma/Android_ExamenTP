package com.example.contacts.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.contacts.R
import com.example.contacts.databinding.FragmentListeContactsBinding
import com.example.contacts.util.ContactActions
import kotlinx.coroutines.launch

class ListeContactsFragment : Fragment(R.layout.fragment_liste_contacts) {

    private var _binding: FragmentListeContactsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()
    private lateinit var adapter: ContactListAdapter

    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        viewModel.refreshPreferences()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentListeContactsBinding.bind(view)
        activity?.title = getString(R.string.app_name)

        adapter = ContactListAdapter(
            onContactClick = { openDetail(it.id) },
            onCallClick = { ContactActions.dial(requireContext(), it.telephone) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.etSearch.doAfterTextChanged { editable ->
            viewModel.setQuery(editable?.toString().orEmpty())
        }
        binding.fabAdd.setOnClickListener { openDetail(0L) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.visibleContacts.collect { contacts ->
                        adapter.submitList(contacts)
                        binding.emptyView.isVisible = contacts.isEmpty()
                    }
                }
                launch {
                    viewModel.preferences.collect { adapter.applyPreferences(it) }
                }
                launch {
                    viewModel.events.collect { event ->
                        if (event is ContactViewModel.UiEvent.Message) {
                            Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        viewModel.refresh()
        viewModel.refreshPreferences()
    }

    override fun onResume() {
        super.onResume()
        // Retour depuis Paramètres ou Détail : on relit la base et les préférences.
        viewModel.refresh()
        viewModel.refreshPreferences()
        viewModel.registerPreferencesListener(preferencesListener)
    }

    override fun onPause() {
        viewModel.unregisterPreferencesListener(preferencesListener)
        super.onPause()
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun openDetail(id: Long) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DetailContactFragment.newInstance(id), TAG_DETAIL)
            .addToBackStack(TAG_DETAIL)
            .commit()
    }

    companion object {
        private const val TAG_DETAIL = "detail"
    }
}
