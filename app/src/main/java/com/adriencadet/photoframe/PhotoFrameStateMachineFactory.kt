package com.adriencadet.photoframe

import android.content.Context

object PhotoFrameStateMachineFactory {

    fun build(context: Context): PhotoFrameStateMachine {
        return PhotoFrameStateMachine(
            PhotoFrameStateEffects(
                context,
                PictureService(
                    GalleryPictureFetcher()
                )
            )
        )
    }
}