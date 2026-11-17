package com.example.contacts

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.contacts.databinding.ActivityMainBinding
import com.example.contacts.ui.ListeContactsFragment
import com.example.contacts.ui.ParametresFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Évite d'empiler un second exemplaire de la liste après une rotation.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ListeContactsFragment(), TAG_LIST)
                .commit()
        }

        supportFragmentManager.addOnBackStackChangedListener { updateUpArrow() }
        updateUpArrow()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            supportFragmentManager.popBackStack()
            true
        }
        R.id.action_settings -> {
            openFragment(ParametresFragment(), TAG_SETTINGS)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .addToBackStack(tag)
            .commit()
    }

    private fun updateUpArrow() {
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
    }

    companion object {
        private const val TAG_LIST = "liste"
        private const val TAG_SETTINGS = "parametres"
    }
}
