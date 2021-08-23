package com.adriencadet.photoframe

import com.adriencadet.photoframe.statemachine.Action

sealed class PhotoFrameAction : Action

data class StartSlideshow(val desiredWidth: Int, val desiredHeight: Int) : PhotoFrameAction()

object TappedSlideShow : PhotoFrameAction()

object NextPicture : PhotoFrameAction()

data class NewPicture(val result: PictureResult) : PhotoFrameAction()

data class IsRunningChanged(val isRunning: Boolean) : PhotoFrameAction()