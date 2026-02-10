/*
 * Copyright (C) 2026 Akshay Sharma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ak1.pix.helpers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import io.ak1.pix.models.Img
import io.ak1.pix.models.Mode
import io.ak1.pix.models.ModelList
import io.ak1.pix.utility.IMAGE_SELECTION
import io.ak1.pix.utility.IMAGE_VIDEO_ORDER_BY
import io.ak1.pix.utility.IMAGE_VIDEO_PROJECTION
import io.ak1.pix.utility.IMAGE_VIDEO_SELECTION
import io.ak1.pix.utility.IMAGE_VIDEO_URI
import io.ak1.pix.utility.TAG
import io.ak1.pix.utility.VIDEO_SELECTION
import java.io.File
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException

/**
 * Created By Akshay Sharma on 17,June,2021
 * https://ak1.io
 */

fun Context.getImageVideoCursor(mode: Mode): Cursor? {
    val projection = when (mode) {
        Mode.Video -> VIDEO_SELECTION
        Mode.Picture -> IMAGE_SELECTION
        else -> IMAGE_VIDEO_SELECTION
    }
    return contentResolver
        .query(
            IMAGE_VIDEO_URI,
            IMAGE_VIDEO_PROJECTION,
            projection,
            null,
            IMAGE_VIDEO_ORDER_BY
        )
}

internal class LocalResourceManager(private val context: Context) {
    private val className = LocalResourceManager::class.java.simpleName

    init {
        Log.v(TAG, "$className initiated")
    }

    var preSelectedUrls: List<Uri> = ArrayList()
    fun retrieveMedia(start: Int = 0, limit: Int = 0, mode: Mode = Mode.All): ModelList {
        val guardsProDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GuardsPro/Pix")

        if (!guardsProDir.exists() || !guardsProDir.isDirectory) {
            return ModelList(list = ArrayList(), selection = ArrayList()) // Return empty list if folder doesn't exist
        }

        // Define allowed file extensions based on mode
        val validExtensions = when (mode) {
            Mode.Picture -> listOf("jpg", "jpeg", "png", "webp")
            Mode.Video -> listOf("mp4", "avi", "mkv", "mov")
            Mode.All -> listOf("jpg", "jpeg", "png", "webp", "mp4", "avi", "mkv", "mov")
        }

        // Get and filter files based on mode
        val files = guardsProDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in validExtensions }
            ?.sortedByDescending { it.lastModified() }
            ?: return ModelList(list = ArrayList(), selection = ArrayList()) // Return empty list if no files

        // Clear lists before adding new data
        val list = mutableListOf<Img>()
        val selectionList = mutableListOf<Img>()
        val addedFiles = mutableSetOf<String>() // Track unique file paths
        var lastHeader = ""

        for ((index, file) in files.withIndex()) {
            val path = Uri.fromFile(file) // Convert file to URI
            val filePath = file.absolutePath
            if (filePath in addedFiles) continue // Skip duplicate file

            val mediaType = if (file.extension.lowercase() in listOf("mp4", "avi", "mkv", "mov"))
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            else
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE

            val dateDifference = context.resources.getDateDifference(Calendar.getInstance().apply { timeInMillis = file.lastModified() })

            // Ensure header is added only once per date group
            if (lastHeader != dateDifference) {
                lastHeader = dateDifference
                if (list.none { it.headerDate == dateDifference }) {
                    list.add(
                        Img(
                            headerDate = dateDifference,
                            mediaType = mediaType
                        )
                    )
                }
            }

            val img = Img(
                headerDate = lastHeader,
                contentUrl = path,
                scrollerDate = index.toString(),
                mediaType = mediaType
            )

            img.position = index

            if (preSelectedUrls.contains(img.contentUrl)) {
                img.selected = true
                selectionList.add(img)
            }

            list.add(img)
            addedFiles.add(filePath) // Mark file as added
        }

        return ModelList(list = list as java.util.ArrayList<Img>, selection = selectionList as java.util.ArrayList<Img>)
    }
}
