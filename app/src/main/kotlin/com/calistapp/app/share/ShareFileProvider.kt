package com.calistapp.app.share

import androidx.core.content.FileProvider

/**
 * A distinct [FileProvider] subclass so it merges as its own manifest component, separate from the
 * updater's provider (which installs APKs). Two providers declared as `androidx.core.content.
 * FileProvider` collide in the manifest merger; giving each its own class avoids that.
 */
class ShareFileProvider : FileProvider()
