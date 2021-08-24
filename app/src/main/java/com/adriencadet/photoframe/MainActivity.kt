package com.adriencadet.photoframe

import android.animation.Animator
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewSwitcher
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign

class MainActivity : AppCompatActivity() {

    private val compositeDisposable = CompositeDisposable()

    private val interactor by lazy {
        MainActivityInteractor(applicationContext = applicationContext)
    }

    private var currentSwitcherIndex = 0

    private lateinit var rootView: ViewGroup
    private lateinit var switcherView: ViewSwitcher
    private lateinit var firstImageView: ImageView
    private lateinit var secondImageView: ImageView
    private lateinit var folderNameView: TextView
    private lateinit var folderBackgroundView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        interactor.startNotification()

        initViews()
    }

    override fun onStart() {
        super.onStart()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupSlideShow()
    }

    override fun onResume() {
        super.onResume()
        hideWindowBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideWindowBars()
        }
    }

    override fun onStop() {
        compositeDisposable.clear()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        interactor.hideNotification()
        super.onDestroy()
    }

    private fun initViews() {
        setContentView(R.layout.activity_main)
        rootView = findViewById(R.id.root)
        switcherView = findViewById(R.id.switcher)
        firstImageView = findViewById(R.id.first)
        secondImageView = findViewById(R.id.second)
        folderBackgroundView = findViewById(R.id.folder_background)
        folderNameView = findViewById(R.id.folder_name)

        switcherView.setInAnimation(this, android.R.anim.fade_in)
        switcherView.setOutAnimation(this, android.R.anim.fade_out)

        rootView.setOnClickListener {
            interactor.onSlideshowTapped()
        }
    }

    private fun setupSlideShow() {
        firstImageView.post {
            interactor.startSlideshow(
                desiredWidth = firstImageView.width,
                desiredHeight = firstImageView.height
            )
        }

        interactor
            .observeIsRunning()
            .bind { (isRunning, folderName) ->
                when {
                    isRunning -> {
                        folderBackgroundView.hide()
                        folderNameView.hide()
                    }
                    else -> {
                        folderNameView.text = folderName
                        folderBackgroundView.show()
                        folderNameView.show()
                    }
                }
            }

        interactor
            .observePictureResult()
            .bind { handlePictureResult(it) }
    }

    private fun View.show() {
        animate().apply {
            alpha(1f)
            duration = Constants.PAUSE_ANIMATION_DURATION_MS
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                    alpha = 0f
                    visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator?) {}

                override fun onAnimationCancel(animation: Animator?) {}

                override fun onAnimationRepeat(animation: Animator?) {}
            })

            start()
        }
    }

    private fun View.hide() {
        animate().apply {
            alpha(0f)
            duration = Constants.PAUSE_ANIMATION_DURATION_MS
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}

                override fun onAnimationEnd(animation: Animator?) {
                    visibility = View.GONE
                }

                override fun onAnimationCancel(animation: Animator?) {}

                override fun onAnimationRepeat(animation: Animator?) {}
            })

            start()
        }
    }

    private fun handlePictureResult(result: PictureResult) {
        when (result) {
            is PictureResult.StorageFailure -> showError("storage failure")
            is PictureResult.BitmapOperationFailure -> showError("bitmap failure")
            is PictureResult.Success -> {
                val bitmap = result.bitmap
                val scaleType = bitmap.toScaleType()

                when (currentSwitcherIndex) {
                    0 -> {
                        secondImageView.scaleType = scaleType
                        secondImageView.setImageBitmap(bitmap)
                        currentSwitcherIndex = 1
                        switcherView.showNext()
                    }
                    1 -> {
                        firstImageView.scaleType = scaleType
                        firstImageView.setImageBitmap(bitmap)
                        currentSwitcherIndex = 0
                        switcherView.showPrevious()
                    }
                    else -> {
                        firstImageView.scaleType = scaleType
                        firstImageView.setImageBitmap(bitmap)
                        currentSwitcherIndex = 0
                    }
                }
            }
        }
    }

    private fun Bitmap.toScaleType(): ImageView.ScaleType {
        return when {
            isPortrait -> ImageView.ScaleType.CENTER_INSIDE
            else -> ImageView.ScaleType.CENTER_CROP
        }
    }

    private val Bitmap.isPortrait: Boolean
        get() = width < height

    private fun showError(message: String) {
        Log.e("PhotoFrame", "Error in MainActivity $message")
        Toast
            .makeText(this@MainActivity, message, Toast.LENGTH_LONG)
            .show()
    }

    private fun hideWindowBars() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    private fun <T> Observable<T>.bind(onNext: (T) -> Unit) {
        compositeDisposable += observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                onNext,
                {
                    Log.e("MainActivity", "Exception at binding level", it)
                    showError(it.message ?: "Exception")
                }
            )
    }
}
