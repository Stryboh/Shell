package org.stryboh.shell

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unpackAssets()
        setContentView(R.layout.layout_main)
        drawerLayout = findViewById(R.id.my_drawer_layout)
        actionBarDrawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, R.string.nav_open, R.string.nav_close)
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        navigationView = findViewById(R.id.nav_view)
        navigationView.itemIconTintList =ContextCompat.getColorStateList(applicationContext,R.color.lime)
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_shell -> {
                    loadFragment(ShellFragment())
                    true
                }
                R.id.nav_gui -> {
                    loadFragment(GUIFragment())
                    true
                }
                R.id.nav_nmap -> {
                    loadFragment(NmapFragment())
                    true
                }
                R.id.nav_ncat-> {
                    loadFragment(NcatFragment())
                    true
                }
                R.id.nav_autoscan-> {
                    loadFragment(AutoscanFragment())
                    true
                }
                R.id.nav_nping-> {
                    loadFragment(NpingFragment())
                    true
                }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            loadFragment(ShellFragment())
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun unpackAssets() {
        val destDirPath = applicationContext.filesDir.path + "/nmap/"
        val destDir = File(destDirPath)
        if (!destDir.exists()) {
            destDir.mkdir()
            try {
                copyAssetsRecursively("nmap", destDirPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val nmapFile = File(filesDir, "nmap/bin/nmap")
        val ncatFile = File(filesDir, "nmap/bin/ncat")
        val npingFile = File(filesDir, "nmap/bin/nping")
        nmapFile.setExecutable(true)
        ncatFile.setExecutable(true)
        npingFile.setExecutable(true)
    }

    private fun copyAssetsRecursively(assetDir: String, destDirPath: String) {
        try {
            val assetFiles = assets.list(assetDir)
            if (assetFiles != null) {
                val destDir = File(destDirPath)
                if (!destDir.exists()) destDir.mkdir()

                for (filename in assetFiles) {
                    val assetPath = "$assetDir/$filename"
                    val destPath = "$destDirPath/$filename"

                    if (assets.list(assetPath)?.isEmpty() == true) {
                        copyAsset(assetPath, destPath)
                    } else {
                        copyAssetsRecursively(assetPath, destPath)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAsset(assetPath: String, destPath: String) {
        try {
            val `in` = assets.open(assetPath)
            val out: OutputStream = FileOutputStream(destPath)
            val buffer = ByteArray(1024)
            var read: Int
            while (`in`.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
            `in`.close()
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}