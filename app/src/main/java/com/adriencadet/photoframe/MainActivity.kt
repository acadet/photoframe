package com.adriencadet.photoframe

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewSwitcher
import com.jakewharton.rxrelay2.PublishRelay
import hu.akarnokd.rxjava2.operators.ObservableTransformers
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var compositeDisposable: CompositeDisposable

    private lateinit var pictureService: PictureService
    private lateinit var notificationService: NotificationService

    private lateinit var rootView: ViewGroup
    private lateinit var switcherView: ViewSwitcher
    private lateinit var firstImageView: ImageView
    private lateinit var secondImageView: ImageView
    private lateinit var folderNameView: TextView

    private var currentSwitcherIndex = -1
    private var currentFolderName = ""
    private var isValveOpen = true

    private val restartRelay = PublishRelay.create<Unit>()
    private val isValveOpenRelay = PublishRelay.create<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationService = NotificationService()
        startNotification()

        initViews()
    }

    override fun onStart() {
        super.onStart()
        initOS()
        initBindings()
    }

    override fun onStop() {
        compositeDisposable?.dispose()
        cleanOS()
        super.onStop()
    }

    override fun onDestroy() {
        hideNotification()
        super.onDestroy()
    }

    private fun initViews() {
        currentSwitcherIndex = -1
        currentFolderName = ""
        isValveOpen = true

        setContentView(R.layout.activity_main)
        rootView = findViewById(R.id.root)
        switcherView = findViewById(R.id.switcher)
        firstImageView = findViewById(R.id.first)
        secondImageView = findViewById(R.id.second)
        folderNameView = findViewById(R.id.folder_name)

        switcherView.setInAnimation(this, android.R.anim.fade_in)
        switcherView.setOutAnimation(this, android.R.anim.fade_out)

        rootView.setOnClickListener {
            if (currentFolderName.isNotEmpty()) {
                isValveOpenRelay.accept(!isValveOpen)
                isValveOpen = !isValveOpen
            }
        }
    }

    private fun initBindings() {
        pictureService = PictureService(
            GalleryPictureFetcher()
        )

        compositeDisposable = CompositeDisposable()
        compositeDisposable += Completable.timer(Constants.INIT_DURATION_SEC, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { startSlideShow() }

        compositeDisposable += isValveOpenRelay
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { isOpening ->
                if (isOpening) {
                    folderNameView.visibility = View.GONE
                } else {
                    folderNameView.text = currentFolderName
                    folderNameView.visibility = View.VISIBLE
                }
            }
    }

    private fun startSlideShow() {
        compositeDisposable +=
            restartRelay.switchMapSingle {
                Single.timer(Constants.DURATION_SEC_BEFORE_RESTART, TimeUnit.SECONDS, Schedulers.io()).map { Unit }
            }
                .startWith(Unit)
                .switchMap {
                    pictureService.observeImages(this, firstImageView.width, firstImageView.height)
                        .doOnComplete { restartRelay.accept(Unit) }
                }
                .compose(ObservableTransformers.valve(isValveOpenRelay, true, 1))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { (bitmap, folderName) ->
                        val isPortrait = bitmap.width < bitmap.height
                        val scaleType =
                            if (isPortrait) ImageView.ScaleType.CENTER_INSIDE else ImageView.ScaleType.CENTER_CROP

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

                        currentFolderName = folderName
                    },
                    { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG) }
                )
    }

    private fun initOS() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun cleanOS() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startNotification() {
        notificationService.start(applicationContext)
    }

    private fun hideNotification() {
        notificationService.stop(applicationContext)
    }
}
