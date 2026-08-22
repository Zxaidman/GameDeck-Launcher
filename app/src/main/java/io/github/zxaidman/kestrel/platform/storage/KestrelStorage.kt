package io.github.zxaidman.kestrel.platform.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.common.flatMap
import io.github.zxaidman.kestrel.core.storage.DocumentName
import io.github.zxaidman.kestrel.core.storage.DocumentStore
import io.github.zxaidman.kestrel.core.storage.StorageError
import io.github.zxaidman.kestrel.core.storage.StoreFolder
import java.io.File

/**
 * Where Kestrel keeps what the user made, and why it is not where an application usually keeps it.
 *
 * An application's own directory is deleted when the application is. That is acceptable for a cache
 * and unacceptable for a layout somebody spent an evening arranging — and it is also invisible: on
 * a modern phone `Android/data` cannot be opened in a file manager, so a user cannot copy their
 * work to another phone, back it up, or hand it to somebody else.
 *
 * So **Kestrel keeps its files in a folder the user chooses**, at the top level of shared storage
 * beside `Android` rather than inside it. That folder survives uninstalling Kestrel, can be copied
 * and pasted like any other folder, and can be opened on a computer.
 *
 * **Chosen rather than assumed.** Reaching a folder at the top level of shared storage is either
 * the Storage Access Framework — where the user picks it and grants access to that one folder — or
 * `MANAGE_EXTERNAL_STORAGE`, which is access to every file on the phone, is a restricted
 * permission, and on the evidence of `ADR-006` risks another Play Protect block for every user.
 * The picker is smaller, more honest, and needs nothing declared in the manifest.
 *
 * **Never required.** With no folder chosen Kestrel keeps working, using its own directory, and
 * says plainly that what it writes there will not survive being uninstalled. `docs/DEGRADED_STATE.md`
 * §2 is the rule: the application does not refuse to start because something is unavailable.
 */
public object KestrelStorage {

    /**
     * The one thing kept in the application's private preferences.
     *
     * A grant is meaningless outside the installation that holds it — uninstalling revokes it — so
     * remembering it anywhere more durable would only preserve a pointer to a permission that no
     * longer exists. Everything that is *the user's* goes in the folder.
     */
    private const val PREFS = "kestrel.storage"
    private const val KEY_TREE = "treeUri"

    /** The name suggested to the user, and the one this documentation assumes. */
    public const val SUGGESTED_FOLDER_NAME: String = "Kestrel"

    @Volatile
    private var store: DocumentStore? = null

    /**
     * Why the chosen folder is not being used, when it was chosen and then stopped working.
     *
     * Empty when there is nothing to say. A folder that has been deleted or whose grant was revoked
     * is not an error to throw — the application keeps working from its own directory — but it is
     * something the user has to be told, because everything they write from then on is somewhere
     * they did not choose.
     */
    @Volatile
    public var problem: String = ""
        private set

    private const val RECHECK_MILLIS = 3_000L

    @Volatile
    private var checkedAt = 0L

    /**
     * The store to use, chosen folder if there is one and the private directory otherwise.
     *
     * **Re-checked rather than remembered.** A grant survives in preferences long after the folder
     * it points at has been deleted, and the first version cached the store forever — so deleting
     * the folder left Kestrel reporting it was still using it while every write failed silently.
     * The check costs an inter-process call, so it is repeated at most every few seconds rather than
     * on every read.
     */
    public fun current(context: Context): DocumentStore {
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = store
        if (cached != null && now - checkedAt < RECHECK_MILLIS) return cached

        synchronized(this) {
            val saved = savedTree(context)
            val opened = saved?.let { uri -> openTree(context, uri) }
            problem = when {
                saved == null -> ""
                opened == null ->
                    "The folder you chose is no longer reachable — it may have been deleted, or " +
                        "its permission withdrawn. Kestrel is using its own directory, which is " +
                        "removed when Kestrel is uninstalled. Choose a folder again to fix it."
                else -> ""
            }
            val store = opened ?: PrivateDocumentStore(context)
            this.store = store
            checkedAt = now
            return store
        }
    }

    /** Whether the user's own folder is in use, as opposed to the directory that dies on uninstall. */
    public fun usingChosenFolder(context: Context): Boolean = current(context) is SafDocumentStore

    /**
     * The intent that asks the user to pick a folder, opened as close to the answer as possible.
     *
     * `EXTRA_INITIAL_URI` points at **`Kestrel` itself**, at the top level of internal storage. If
     * that folder is already there — because the user made it, or because a previous installation
     * did — the picker opens inside it and the whole interaction is one tap on *Use this folder*.
     * If it is not there, the picker falls back to somewhere near it and the user makes it, once.
     *
     * **Kestrel cannot create that folder itself, and the reason is worth stating rather than
     * leaving as an apparent oversight.** Creating a directory at the top of shared storage needs
     * `MANAGE_EXTERNAL_STORAGE` — access to every file on the phone. It is a restricted permission,
     * and declaring a permission of that class is exactly what got Kestrel blocked by Play Protect
     * when the accessibility service was declared, measured in `ADR-006`. The picker costs the user
     * one tap, once; the permission would cost every user their install.
     *
     * Whatever the user picks, [useFolder] ensures a `Kestrel` folder inside it, so picking the
     * right folder and picking its parent both end in the right place.
     */
    public fun folderPicker(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:$SUGGESTED_FOLDER_NAME",
                        ),
                    )
                }
            }

    /** The name Kestrel gives its own folder when it makes one inside what the user picked. */
    public const val FOLDER_NAME: String = "Kestrel"

    /**
     * Accepts the folder the user picked, and moves what was already written into it.
     *
     * Copying rather than switching is the difference between a setting and a migration. Somebody
     * who has been using Kestrel before choosing a folder has settings; silently starting again
     * from defaults because they answered a question would be a punishment for answering it.
     */
    public fun useFolder(context: Context, tree: Uri): Outcome<String> {
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (persisted.isFailure) {
            return Outcome.Failure(
                StorageError.NoLocation(
                    "the folder was picked but Kestrel was not given lasting access to it"
                )
            )
        }

        val chosen = openTree(context, tree)
            ?: return Outcome.Failure(
                StorageError.NoLocation(
                    "that folder cannot be opened, or Kestrel could not make a folder inside it"
                )
            )

        val previous = current(context)
        val moved = if (previous is SafDocumentStore) 0 else copyEverything(previous, chosen)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE, tree.toString())
            .apply()
        store = chosen

        return Outcome.Success(
            if (moved == 0) {
                "Kestrel now keeps its files in ${chosen.description}."
            } else {
                "Kestrel now keeps its files in ${chosen.description}, and $moved " +
                    (if (moved == 1) "document was" else "documents were") + " copied into it."
            }
        )
    }

    /** Goes back to the private directory. The folder and its contents are left untouched. */
    public fun forgetFolder(context: Context): String {
        savedTree(context)?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TREE).apply()
        store = PrivateDocumentStore(context)
        return "Kestrel is back to its own directory. Nothing in the folder was changed or removed."
    }

    private fun savedTree(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE, null)
            ?.let(Uri::parse)

    /**
     * Opens a tree, and checks it is still usable rather than trusting that it was.
     *
     * A grant can be revoked, and the folder can be deleted or on a card that is no longer in the
     * phone. Discovering that at the first write would mean a user's edit disappearing; discovering
     * it here means the screen can say so before anything is lost.
     */
    private fun openTree(context: Context, tree: Uri): SafDocumentStore? {
        val held = context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        if (!held) return null
        val folder = kestrelFolderIn(context, tree) ?: return null
        if (!folder.isDirectory || !folder.canWrite()) return null
        return SafDocumentStore(context, folder)
    }

    /**
     * Kestrel's own folder inside whatever the user picked, created if it is not there yet.
     *
     * The same rule on every path, which is what makes reopening after a restart land in the same
     * place as choosing did. Someone who selects the whole of Documents has not agreed to have
     * `settings.json` dropped among their documents; someone who made a `Kestrel` folder
     * deliberately should have that one used rather than nested inside itself.
     */
    private fun kestrelFolderIn(context: Context, tree: Uri): DocumentFile? {
        val root = runCatching { DocumentFile.fromTreeUri(context, tree) }.getOrNull() ?: return null
        if (!root.isDirectory) return null
        if (root.name.equals(FOLDER_NAME, ignoreCase = true)) return root
        root.findFile(FOLDER_NAME)?.let { if (it.isDirectory) return it }
        return runCatching { root.createDirectory(FOLDER_NAME) }.getOrNull()
    }

    private fun copyEverything(from: DocumentStore, to: DocumentStore): Int {
        var moved = 0
        StoreFolder.entries.forEach { folder ->
            val names = (from.list(folder) as? Outcome.Success)?.value.orEmpty()
            names.forEach { name ->
                val text = (from.read(folder, name) as? Outcome.Success)?.value
                if (text != null && to.write(folder, name, text) is Outcome.Success) moved += 1
            }
        }
        return moved
    }
}

/**
 * Kestrel's own directory: always available, and deleted when Kestrel is.
 *
 * The fallback, not the destination. It exists so that Kestrel works before anyone has answered a
 * question about folders, and so that a user who never answers it still has a working product.
 */
public class PrivateDocumentStore(context: Context) : DocumentStore {

    private val root: File = File(context.filesDir, "kestrel")

    override val description: String =
        "Kestrel's own directory, which is deleted if Kestrel is uninstalled"

    private fun folderOf(folder: StoreFolder): File =
        if (folder.folderName.isEmpty()) root else File(root, folder.folderName)

    private fun fileOf(folder: StoreFolder, name: String): File = File(folderOf(folder), name)

    override fun read(folder: StoreFolder, name: String): Outcome<String> =
        DocumentName.validate(name).flatMap {
            val file = fileOf(folder, name)
            if (!file.isFile) {
                Outcome.Failure(StorageError.NotFound(folder, name))
            } else {
                runCatching { Outcome.Success(file.readText()) }
                    .getOrElse {
                        Outcome.Failure(
                            StorageError.Unreadable(folder, name, it.javaClass.simpleName)
                        )
                    }
            }
        }

    override fun write(folder: StoreFolder, name: String, text: String): Outcome<Unit> =
        DocumentName.validate(name).flatMap {
            val size = text.encodeToByteArray().size.toLong()
            if (size > DocumentName.MAX_DOCUMENT_BYTES) {
                return@flatMap Outcome.Failure(
                    StorageError.TooLarge(name, size, DocumentName.MAX_DOCUMENT_BYTES)
                )
            }
            runCatching {
                folderOf(folder).mkdirs()
                fileOf(folder, name).writeText(text)
                Outcome.Success(Unit)
            }.getOrElse {
                Outcome.Failure(StorageError.Unwritable(folder, name, it.javaClass.simpleName))
            }
        }

    override fun list(folder: StoreFolder): Outcome<List<String>> =
        Outcome.Success(
            folderOf(folder).listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()
        )

    override fun delete(folder: StoreFolder, name: String): Outcome<Unit> =
        DocumentName.validate(name).flatMap {
            val file = fileOf(folder, name)
            when {
                !file.isFile -> Outcome.Failure(StorageError.NotFound(folder, name))
                file.delete() -> Outcome.Success(Unit)
                else -> Outcome.Failure(StorageError.Unwritable(folder, name, "the file could not be deleted"))
            }
        }

    override fun exists(folder: StoreFolder, name: String): Boolean = fileOf(folder, name).isFile
}

/**
 * The folder the user picked, reached through the Storage Access Framework.
 *
 * Every operation resolves the sub-folder by name each time rather than caching it. That is slower
 * and it is correct: the folder is one the user can open in a file manager and rearrange while
 * Kestrel is running, and a cached handle to a folder somebody has deleted writes to nowhere.
 */
public class SafDocumentStore(
    private val context: Context,
    private val root: DocumentFile,
) : DocumentStore {

    override val description: String =
        root.name?.let { "the $it folder you chose" } ?: "the folder you chose"

    private fun folderOf(folder: StoreFolder, create: Boolean): DocumentFile? {
        if (folder.folderName.isEmpty()) return root
        val existing = root.findFile(folder.folderName)
        if (existing != null && existing.isDirectory) return existing
        // A *file* with the folder's name is somebody else's file, and Kestrel does not delete it
        // to make room. It reports that it cannot write instead.
        if (existing != null) return null
        return if (create) root.createDirectory(folder.folderName) else null
    }

    override fun read(folder: StoreFolder, name: String): Outcome<String> =
        DocumentName.validate(name).flatMap {
            val file = folderOf(folder, create = false)?.findFile(name)
            if (file == null || !file.isFile) {
                Outcome.Failure(StorageError.NotFound(folder, name))
            } else {
                runCatching {
                    context.contentResolver.openInputStream(file.uri).use { stream ->
                        Outcome.Success(stream!!.readBytes().decodeToString())
                    }
                }.getOrElse {
                    Outcome.Failure(StorageError.Unreadable(folder, name, it.javaClass.simpleName))
                }
            }
        }

    override fun write(folder: StoreFolder, name: String, text: String): Outcome<Unit> =
        DocumentName.validate(name).flatMap {
            val size = text.encodeToByteArray().size.toLong()
            if (size > DocumentName.MAX_DOCUMENT_BYTES) {
                return@flatMap Outcome.Failure(
                    StorageError.TooLarge(name, size, DocumentName.MAX_DOCUMENT_BYTES)
                )
            }
            val directory = folderOf(folder, create = true)
                ?: return@flatMap Outcome.Failure(
                    StorageError.Unwritable(folder, name, "the folder could not be created")
                )

            // Found first, created only if absent. createDocument invents a new name when one is
            // taken — "settings (1).json" — so creating blindly would leave the real settings file
            // untouched while appearing to succeed.
            val existing = directory.findFile(name)?.takeIf { it.isFile }
            val file = existing ?: directory.createFile(MIME, name)
                ?: return@flatMap Outcome.Failure(
                    StorageError.Unwritable(folder, name, "the file could not be created")
                )

            runCatching {
                // "wt" truncates. Without it a shorter document leaves the tail of the longer one
                // it replaced, and the result is a file that parses as neither.
                context.contentResolver.openOutputStream(file.uri, "wt").use { stream ->
                    stream!!.write(text.encodeToByteArray())
                }
                Outcome.Success(Unit)
            }.getOrElse {
                Outcome.Failure(StorageError.Unwritable(folder, name, it.javaClass.simpleName))
            }
        }

    override fun list(folder: StoreFolder): Outcome<List<String>> =
        Outcome.Success(
            folderOf(folder, create = false)
                ?.listFiles()
                ?.filter { it.isFile }
                ?.mapNotNull { it.name }
                ?.sorted()
                .orEmpty()
        )

    override fun delete(folder: StoreFolder, name: String): Outcome<Unit> =
        DocumentName.validate(name).flatMap {
            val file = folderOf(folder, create = false)?.findFile(name)
            when {
                file == null || !file.isFile -> Outcome.Failure(StorageError.NotFound(folder, name))
                file.delete() -> Outcome.Success(Unit)
                else -> Outcome.Failure(
                    StorageError.Unwritable(folder, name, "the file could not be deleted")
                )
            }
        }

    override fun exists(folder: StoreFolder, name: String): Boolean =
        folderOf(folder, create = false)?.findFile(name)?.isFile == true

    private companion object {
        /** What these documents are. A file manager showing them as text is the point. */
        const val MIME = "application/json"
    }
}
