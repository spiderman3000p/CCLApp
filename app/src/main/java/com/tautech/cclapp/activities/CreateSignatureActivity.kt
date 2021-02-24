package com.tautech.cclapp.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tautech.cclapp.R
import me.panavtec.drawableview.DrawableView
import me.panavtec.drawableview.DrawableViewConfig
import java.io.ByteArrayOutputStream


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class CreateSignatureActivity : AppCompatActivity() {
    private lateinit var drawableView: DrawableView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_signature)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val config = DrawableViewConfig()
        // Set up the user interaction to manually show or hide the system UI.
        drawableView = findViewById<DrawableView>(R.id.paintView)
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        config.strokeColor = resources.getColor(android.R.color.black)
        config.isShowCanvasBounds =
            true // If the view is bigger than canvas, with this the user will see the bounds (Recommended)
        config.strokeWidth = 15.0f
        config.minZoom = 1.0f
        config.maxZoom = 3.0f
        config.canvasHeight = 800
        config.canvasWidth = displayMetrics.widthPixels
        drawableView.setConfig(config)
        findViewById<Button>(R.id.dummy_button).setOnClickListener{
            val image = drawableView.obtainBitmap()
            val bStream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.PNG, 100, bStream)
            val byteArray: ByteArray = bStream.toByteArray()
            setResult(Activity.RESULT_OK, Intent().apply{
                putExtra("image", byteArray)
                putExtra("index", intent.getIntExtra("index", -1))
            })
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.default_menu, menu)
        menu?.findItem(R.id.default_action)?.title = getString(R.string.clear)
        menu?.findItem(R.id.default_action)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu?.findItem(R.id.default_action)?.setIcon(ContextCompat.getDrawable(this,
            R.drawable.ic_arrow_repeat___236_))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.default_action -> drawableView.clear()
        }
        return true
    }
}