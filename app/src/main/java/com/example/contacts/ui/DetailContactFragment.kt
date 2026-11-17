package com.example.contacts.ui

import android.os.Bundle
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.contacts.R
import com.example.contacts.data.Contact
import com.example.contacts.databinding.FragmentDetailContactBinding
import com.example.contacts.util.ContactActions
import com.example.contacts.util.PhotoStorage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class DetailContactFragment : Fragment(R.layout.fragment_detail_contact) {

    private var _binding: FragmentDetailContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()
    private val contactId: Long by lazy { requireArguments().getLong(ARG_ID, 0L) }

    private var currentPhotoPath: String? = null
    private var cameraFile: File? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // uri == null : l'utilisateur a annulé, aucune erreur à afficher.
        if (uri == null) return@registerForActivityResult
        val path = PhotoStorage.copyFromUri(requireContext(), uri, "galerie")
        if (path == null) toast(R.string.photo_error) else setPhoto(path, removePrevious = true)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) {
            cameraFile?.delete()
            cameraFile = null
            return@registerForActivityResult
        }
        cameraFile?.let { setPhoto(it.absolutePath, removePrevious = true) }
        cameraFile = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentDetailContactBinding.bind(view)
        activity?.title = if (contactId == 0L) {
            getString(R.string.contact_new)
        } else {
            getString(R.string.contact_edit)
        }

        binding.btnDelete.isEnabled = contactId != 0L
        setupListeners()
        observeEvents()
        observePreferences()

        parentFragmentManager.setFragmentResultListener(
            CarteContactFragment.REQ_LOCATION,
            viewLifecycleOwner
        ) { _, result ->
            binding.tilLatitude.editText?.setText(result.getDouble(CarteContactFragment.RESULT_LAT).toString())
            binding.tilLongitude.editText?.setText(result.getDouble(CarteContactFragment.RESULT_LNG).toString())
        }

        if (savedInstanceState != null) {
            restoreForm(savedInstanceState)
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                val contact = viewModel.loadContact(contactId)
                if (contact != null) {
                    fillForm(contact)
                } else if (contactId != 0L) {
                    toast(R.string.error_not_found)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            readForm()?.let { viewModel.save(it) }
        }
        binding.btnCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(contactId) }
                .show()
        }
        binding.btnCall.setOnClickListener {
            ContactActions.dial(requireContext(), textOf(binding.tilTelephone))
        }
        binding.btnEmail.setOnClickListener {
            ContactActions.sendEmail(requireContext(), textOf(binding.tilEmail))
        }
        binding.btnMap.setOnClickListener { openMap() }
        binding.btnGallery.setOnClickListener { pickImage.launch("image/*") }
        binding.btnCamera.setOnClickListener { launchCamera() }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ContactViewModel.UiEvent.Message ->
                            Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
                        is ContactViewModel.UiEvent.Saved,
                        ContactViewModel.UiEvent.Deleted -> parentFragmentManager.popBackStack()
                    }
                }
            }
        }
    }

    /** Les mêmes préférences que la liste sont appliquées aux champs du détail. */
    private fun observePreferences() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.preferences.collect { prefs ->
                    listOf(
                        binding.tilNom.editText,
                        binding.tilPrenom.editText,
                        binding.tilTelephone.editText
                    ).forEach { field ->
                        field?.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.textSizeSp)
                    }
                    binding.tilNom.editText?.setTextColor(prefs.textColor)
                    binding.tilPrenom.editText?.setTextColor(prefs.textColor)
                }
            }
        }
    }

    private fun fillForm(contact: Contact) {
        binding.tilNom.editText?.setText(contact.nom)
        binding.tilPrenom.editText?.setText(contact.prenom)
        binding.tilTelephone.editText?.setText(contact.telephone)
        binding.tilEmail.editText?.setText(contact.email.orEmpty())
        binding.tilLatitude.editText?.setText(contact.latitude?.toString().orEmpty())
        binding.tilLongitude.editText?.setText(contact.longitude?.toString().orEmpty())
        setPhoto(contact.photoUri.orEmpty(), removePrevious = false)
    }

    private fun setPhoto(path: String, removePrevious: Boolean) {
        if (removePrevious) PhotoStorage.deleteIfInternal(requireContext(), currentPhotoPath)
        currentPhotoPath = path.ifBlank { null }
        renderPhoto()
    }

    private fun renderPhoto() {
        val bitmap = PhotoStorage.loadBitmap(currentPhotoPath)
        if (bitmap != null) binding.ivPhoto.setImageBitmap(bitmap)
        else binding.ivPhoto.setImageResource(R.drawable.ic_person)
    }

    private fun launchCamera() {
        val file = try {
            PhotoStorage.createImageFile(requireContext(), "camera")
        } catch (_: IOException) {
            toast(R.string.photo_error)
            return
        }
        cameraFile = file
        try {
            takePicture.launch(PhotoStorage.uriFor(requireContext(), file))
        } catch (_: Exception) {
            cameraFile = null
            file.delete()
            toast(R.string.photo_error)
        }
    }

    private fun openMap() {
        val latitude = textOf(binding.tilLatitude).toDoubleOrNull()
        val longitude = textOf(binding.tilLongitude).toDoubleOrNull()
        if (latitude == null || longitude == null || !isValidPosition(latitude, longitude)) {
            toast(R.string.no_coordinates)
            return
        }
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                CarteContactFragment.newInstance(
                    latitude,
                    longitude,
                    textOf(binding.tilNom)
                ),
                TAG_MAP
            )
            .addToBackStack(TAG_MAP)
            .commit()
    }

    private fun readForm(): Contact? {
        clearErrors()
        val nom = textOf(binding.tilNom)
        val prenom = textOf(binding.tilPrenom)
        val telephone = textOf(binding.tilTelephone)
        val email = textOf(binding.tilEmail).ifBlank { null }
        val latText = textOf(binding.tilLatitude)
        val lngText = textOf(binding.tilLongitude)

        var valid = true
        if (nom.isEmpty()) {
            binding.tilNom.error = getString(R.string.error_required)
            valid = false
        }
        if (prenom.isEmpty()) {
            binding.tilPrenom.error = getString(R.string.error_required)
            valid = false
        }
        if (telephone.isEmpty()) {
            binding.tilTelephone.error = getString(R.string.error_required)
            valid = false
        }
        if (email != null && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email)
            valid = false
        }

        var latitude: Double? = null
        var longitude: Double? = null
        if (latText.isNotBlank() || lngText.isNotBlank()) {
            val lat = latText.toDoubleOrNull()
            val lng = lngText.toDoubleOrNull()
            if (lat == null || lng == null || !isValidPosition(lat, lng)) {
                binding.tilLatitude.error = getString(R.string.error_coordinates)
                binding.tilLongitude.error = getString(R.string.error_coordinates)
                valid = false
            } else {
                latitude = lat
                longitude = lng
            }
        }

        if (!valid) return null
        return Contact(
            id = contactId,
            nom = nom,
            prenom = prenom,
            telephone = telephone,
            email = email,
            photoUri = currentPhotoPath,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun clearErrors() {
        listOf(
            binding.tilNom,
            binding.tilPrenom,
            binding.tilTelephone,
            binding.tilEmail,
            binding.tilLatitude,
            binding.tilLongitude
        ).forEach { it.error = null }
    }

    private fun textOf(layout: TextInputLayout): String =
        layout.editText?.text?.toString()?.trim().orEmpty()

    private fun toast(messageRes: Int) =
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_NOM, textOf(binding.tilNom))
        outState.putString(KEY_PRENOM, textOf(binding.tilPrenom))
        outState.putString(KEY_TELEPHONE, textOf(binding.tilTelephone))
        outState.putString(KEY_EMAIL, textOf(binding.tilEmail))
        outState.putString(KEY_LAT, textOf(binding.tilLatitude))
        outState.putString(KEY_LNG, textOf(binding.tilLongitude))
        outState.putString(KEY_PHOTO, currentPhotoPath)
        outState.putString(KEY_CAMERA_FILE, cameraFile?.absolutePath)
    }

    private fun restoreForm(state: Bundle) {
        binding.tilNom.editText?.setText(state.getString(KEY_NOM).orEmpty())
        binding.tilPrenom.editText?.setText(state.getString(KEY_PRENOM).orEmpty())
        binding.tilTelephone.editText?.setText(state.getString(KEY_TELEPHONE).orEmpty())
        binding.tilEmail.editText?.setText(state.getString(KEY_EMAIL).orEmpty())
        binding.tilLatitude.editText?.setText(state.getString(KEY_LAT).orEmpty())
        binding.tilLongitude.editText?.setText(state.getString(KEY_LNG).orEmpty())
        currentPhotoPath = state.getString(KEY_PHOTO)
        cameraFile = state.getString(KEY_CAMERA_FILE)?.let(::File)
        renderPhoto()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "contact_id"
        private const val TAG_MAP = "carte"
        private const val KEY_NOM = "form_nom"
        private const val KEY_PRENOM = "form_prenom"
        private const val KEY_TELEPHONE = "form_telephone"
        private const val KEY_EMAIL = "form_email"
        private const val KEY_LAT = "form_lat"
        private const val KEY_LNG = "form_lng"
        private const val KEY_PHOTO = "form_photo"
        private const val KEY_CAMERA_FILE = "form_camera_file"

        fun newInstance(id: Long): DetailContactFragment = DetailContactFragment().apply {
            arguments = bundleOf(ARG_ID to id)
        }

        private fun isValidPosition(latitude: Double, longitude: Double): Boolean =
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
