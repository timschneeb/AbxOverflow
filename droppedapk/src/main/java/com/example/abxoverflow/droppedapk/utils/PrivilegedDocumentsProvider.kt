package com.example.abxoverflow.droppedapk.utils

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/**
 * A DocumentsProvider that allows access with this process's privileges
 */
open class PrivilegedDocumentsProvider : DocumentsProvider() {
    private var packageName: String? = null
    private val rootId by lazy { "${packageName}_$currentProcessName" }
    private var rootDirectory = File("/")

    open val rootShortcuts: Map<String, File> =
        mapOf(
            "#data_system" to File("/data/system"),
            "#data_system_de" to File("/data/system_de"),
            "#data_system_ce" to File("/data/system_ce"),
            "#data_misc" to File("/data/misc"),
            "#data_misc_de" to File("/data/misc_de"),
            "#data_misc_ce" to File("/data/misc_ce"),
            "#data_vendor" to File("/data/vendor"),
            "#data_vendor_de" to File("/data/vendor_de"),
            "#data_vendor_ce" to File("/data/vendor_ce"),
            "#data_data" to File("/data/data"),
            "#data_data_de" to File("/data/data_de"),
        )

    override fun onCreate(): Boolean = true

    override fun attachInfo(context: Context, providerInfo: ProviderInfo?) {
        super.attachInfo(context, providerInfo)
        packageName = context.packageName
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val file = File(
            resolveDocumentId(parentDocumentId),
            displayName
        )

        if (file.exists()) {
            throw FileNotFoundException("Document $displayName already exists in $parentDocumentId")
        }

        try {
            // Create the file or directory
            if (if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()) {
                // Return the document ID of the new entity
                return if(parentDocumentId.endsWith("/"))
                    parentDocumentId + file.name
                else
                    "$parentDocumentId/${file.name}"
            }
        } catch (e: IOException) {
            throw FileNotFoundException("Failed to create document in $parentDocumentId with name $displayName: ${e.message}")
            // Do nothing. We are throwing a FileNotFoundException later if the file could not be created.
        }
        throw FileNotFoundException("Failed to create document in $parentDocumentId with name $displayName")
    }

    override fun deleteDocument(documentId: String) {
        if (!deleteRecursively(resolveDocumentId(documentId))) {
            throw FileNotFoundException("Failed to delete document $documentId")
        }
    }

    override fun getDocumentType(documentId: String) =
        resolveMimeType(resolveDocumentId(documentId))

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        documentId.startsWith(parentDocumentId)

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String?,
        targetParentDocumentId: String
    ): String {
        val source = resolveDocumentId(sourceDocumentId)
        val dest = resolveDocumentId(targetParentDocumentId)

        val file = File(dest, source.getName())
        if (!file.exists() && source.renameTo(file)) {
            // Return the new document ID
            if (targetParentDocumentId.endsWith("/")) {
                return targetParentDocumentId + file.getName()
            }
            return targetParentDocumentId + "/" + file.getName()
        }

        throw FileNotFoundException("Failed to move document from $sourceDocumentId to $targetParentDocumentId")
    }

    override fun openDocument(
        documentId: String,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? = ParcelFileDescriptor.open(
        resolveDocumentId(documentId),
        ParcelFileDescriptor.parseMode(mode)
    )

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String?>?,
        sortOrder: String?
    ): Cursor {
        val parentDocumentId = parentDocumentId.removeSuffix("/")

        val cursor = MatrixCursor(projection ?: directoryColumns)
        val children = resolveDocumentId(parentDocumentId)
        if (children == rootDirectory) {
            // Add root shortcuts
            rootShortcuts.forEach { (name, path) ->
                addRowForDocument(cursor, "$parentDocumentId/$name", path, name)
            }
        }

        // Collect all children
        children.listFiles()?.forEach { file ->
            addRowForDocument(cursor, "$parentDocumentId/${file.name}", file)
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<String?>?) =
        MatrixCursor(projection ?: directoryColumns).apply {
            addRowForDocument(this, documentId, null)
        }

    override fun queryRoots(projection: Array<String?>?) = MatrixCursor(
        projection ?: rootColumns
    ).apply {
        newRow().run {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, rootId)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, rootId)
            add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY or
                        DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
            )
            add(DocumentsContract.Root.COLUMN_TITLE, currentProcessName)
            add(DocumentsContract.Root.COLUMN_SUMMARY, "Access files as UID ${android.os.Process.myUid()}")
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
            add(DocumentsContract.Root.COLUMN_ICON, context?.applicationInfo?.icon)
        }
    }

    override fun removeDocument(documentId: String, parentDocumentId: String?) {
        deleteDocument(documentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = resolveDocumentId(documentId)
        if (!file.renameTo(File(file.getParentFile(), displayName))) {
            throw FileNotFoundException("Failed to rename document from $documentId to $displayName")
        }

        // Return the new document ID
        return documentId.substring(
            0,
            documentId.lastIndexOf('/', documentId.length - 2)
        ) + "/" + displayName
    }

    /**
     * Resolve a file instance for a given document ID.
     * 
     * @param fullContentPath The document ID to resolve.
     * @return File object for the given document ID.
     * @throws FileNotFoundException If the document ID is invalid or the file does not exist.
     */
    private fun resolveDocumentId(fullContentPath: String): File {
        if (!fullContentPath.startsWith(packageName!!)) {
            throw FileNotFoundException("$fullContentPath not found")
        }
        val path = fullContentPath.substring(rootId.length)

        // Resolve the relative path within the root directory
        val file: File
        if (path == "/" || path.isEmpty()) {
            file = rootDirectory
        } else {
            // Remove leading slash
            val relativePath = path.substring(1)
            // Try to resolve root shortcuts first
            file = rootShortcuts.entries
                .firstOrNull { relativePath.startsWith(it.key) }
                ?.let { (name, path) ->
                    File(relativePath.replace(name, path.absolutePath))
                }
                // If no shortcuts match, resolve normally
                ?: File(rootDirectory, relativePath)
        }

        if (!file.exists()) {
            throw FileNotFoundException("$fullContentPath not found")
        }
        return file
    }

    /**
     * Add a row containing all file properties to a MatrixCursor for a given document ID.
     * 
     * @param cursor     The cursor to add the row to.
     * @param documentId The document ID to add the row for.
     * @param file       The file to add the row for. If null, the file will be resolved from the document ID.
     * @param overrideName An optional name to override the file's actual name.
     * @throws FileNotFoundException If the file does not exist.
     */
    private fun addRowForDocument(cursor: MatrixCursor, documentId: String, file: File?, overrideName: String? = null) {
        var file = file
        if (file == null) {
            file = resolveDocumentId(documentId)
        }

        var flags = 0
        if (file.isDirectory()) {
            // Prefer list view for directories
            flags = flags or DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED
        }

        if (file.canWrite()) {
            if (file.isDirectory()) {
                flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            }

            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                    DocumentsContract.Document.FLAG_SUPPORTS_MOVE
        }

        val row = cursor.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, resolveMimeType(file))
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)

        // Custom columns
        row.add("full_path", file.absolutePath)

        // Add lstat column
        val path = file.path
        try {
            val sb = StringBuilder()
            val lstat = Os.lstat(path)
            sb.append(lstat.st_mode)
            sb.append(";")
            sb.append(lstat.st_uid)
            sb.append(";")
            sb.append(lstat.st_gid)
            // Append symlink target if it is a symlink
            if ((lstat.st_mode and S_IFLNK) == S_IFLNK) {
                sb.append(";")
                try {
                    sb.append(Os.readlink(path))
                } catch (_: Exception) {
                    sb.append("ERROR")
                }
            }
            row.add("lstat_info", sb.toString())
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, overrideName ?: file.name)
        } catch (_: Exception) {
            // Mark files with failed lstat with a ! prefix in the name
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "!" + (overrideName ?: file.name))
            // Log.e("InternalDocumentsProvider", "Failed to get lstat info for $path", ex)
        }
    }

    companion object {
        private val rootColumns = arrayOf<String?>(
            "root_id",
            "mime_types",
            "flags",
            "icon",
            "title",
            "summary",
            "document_id"
        )
        private val directoryColumns = arrayOf<String?>(
            "document_id", "mime_type", "_display_name", "last_modified", "flags",
            "_size", "full_path", "lstat_info"
        )
        private const val S_IFLNK = 0x8000

        /**
         * Recursively delete a file or directory and all its children.
         * 
         * @param root The file or directory to delete.
         * @return True if the file or directory and all its children were successfully deleted.
         */
        private fun deleteRecursively(root: File): Boolean {
            Log.e("InternalDocumentsProvider", "Deleting: " + root.path)

            // If root is a directory, delete all children first
            if (root.isDirectory()) {
                try {
                    // Only delete recursively if the directory is not a symlink
                    if ((Os.lstat(root.path).st_mode and S_IFLNK) != S_IFLNK) {
                        val files = root.listFiles()
                        if (files != null) {
                            for (file in files) {
                                if (!deleteRecursively(file)) {
                                    return false
                                }
                            }
                        }
                    }
                } catch (e: ErrnoException) {
                    Log.e("InternalDocumentsProvider", "Failed to lstat " + root.path, e)
                }
            }

            // Delete file or empty directory
            return root.delete()
        }

        /**
         * Resolve the MIME type of file based on its extension.
         * 
         * @param file The file to resolve the MIME type for.
         * @return The MIME type of the file.
         */
        private fun resolveMimeType(file: File): String {
            if (file.isDirectory()) {
                return DocumentsContract.Document.MIME_TYPE_DIR
            }

            val indexOfExtDot = file.name.lastIndexOf('.')
            if (indexOfExtDot < 0) {
                // No extension
                return "application/octet-stream"
            }

            val extension = file.name.substring(indexOfExtDot + 1).lowercase(Locale.getDefault())
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }
    }
}