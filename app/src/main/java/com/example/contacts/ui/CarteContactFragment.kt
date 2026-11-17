package com.example.contacts.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.contacts.R
import com.example.contacts.databinding.FragmentCarteContactBinding
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Carte OpenStreetMap (osmdroid) : pas de clé API, donc aucun secret à versionner.
 * Le survol long permet de renvoyer de nouvelles coordonnées à la fiche.
 */
class CarteContactFragment : Fragment(R.layout.fragment_carte_contact) {

    private var _binding: FragmentCarteContactBinding? = null
    private val binding get() = _binding!!

    private lateinit var map: MapView
    private var marker: Marker? = null

    private val latitude: Double by lazy { requireArguments().getDouble(ARG_LAT, Double.NaN) }
    private val longitude: Double by lazy { requireArguments().getDouble(ARG_LNG, Double.NaN) }
    private val contactName: String by lazy {
        requireArguments().getString(ARG_NAME).orEmpty().ifBlank { getString(R.string.app_name) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCarteContactBinding.bind(view)
        activity?.title = getString(R.string.view_map)
        binding.tvHint.text = getString(R.string.map_hint)

        configureOsmdroid()
        map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val valid = isUsable(latitude, longitude)
        binding.tvMapMessage.visibility = if (valid) View.GONE else View.VISIBLE
        if (!valid) {
            binding.tvMapMessage.text = getString(R.string.no_coordinates)
        }

        val center = if (valid) GeoPoint(latitude, longitude) else DEFAULT_CENTER
        map.controller.setZoom(if (valid) 15.0 else 8.0)
        map.controller.setCenter(center)
        if (valid) addMarker(center)

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean = false

            override fun longPressHelper(point: GeoPoint?): Boolean {
                if (point == null) return false
                addMarker(point)
                parentFragmentManager.setFragmentResult(
                    REQ_LOCATION,
                    bundleOf(
                        RESULT_LAT to point.latitude,
                        RESULT_LNG to point.longitude
                    )
                )
                parentFragmentManager.popBackStack()
                return true
            }
        }
        map.overlays.add(MapEventsOverlay(receiver))
    }

    private fun configureOsmdroid() {
        val context = requireContext().applicationContext
        Configuration.getInstance().load(
            context,
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        Configuration.getInstance().apply {
            // Cache interne : aucune permission d'écriture externe nécessaire.
            val base = File(context.cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            // User-Agent conforme à la politique d'utilisation des tuiles OSM.
            userAgentValue = "ContactsV4/4.0 (TP Android; contact: etudiant@example.com)"
        }
    }

    private fun addMarker(point: GeoPoint) {
        marker?.let { map.overlays.remove(it) }
        marker = Marker(map).apply {
            position = point
            title = contactName
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(marker)
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
    }

    override fun onPause() {
        if (::map.isInitialized) map.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        if (::map.isInitialized) map.onDetach()
        marker = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_LAT = "latitude"
        const val ARG_LNG = "longitude"
        const val ARG_NAME = "contact_name"
        const val REQ_LOCATION = "location_request"
        const val RESULT_LAT = "result_lat"
        const val RESULT_LNG = "result_lng"

        private val DEFAULT_CENTER = GeoPoint(36.8065, 10.1815)

        private fun isUsable(lat: Double, lng: Double): Boolean =
            lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

        fun newInstance(latitude: Double, longitude: Double, name: String): CarteContactFragment =
            CarteContactFragment().apply {
                arguments = bundleOf(
                    ARG_LAT to latitude,
                    ARG_LNG to longitude,
                    ARG_NAME to name
                )
            }
    }
}
