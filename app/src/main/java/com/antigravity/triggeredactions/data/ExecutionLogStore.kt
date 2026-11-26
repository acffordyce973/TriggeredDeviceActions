package com.antigravity.triggeredactions.data

import android.content.Context
import android.util.Log
import com.antigravity.triggeredactions.model.ExecutionLogEntry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent bounded ring-buffer store for Action Set execution history logs.
 */
class ExecutionLogStore private constructor(appContext: Context) {

	private val fileLog = File(appContext.filesDir, "execution_logs.json")
	private val objMutex = Mutex()
	private val objScope = CoroutineScope(Dispatchers.IO)
	private val objJson = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	private val _flowLogs = MutableStateFlow<List<ExecutionLogEntry>>(emptyList())
	val flowLogs: StateFlow<List<ExecutionLogEntry>> = _flowLogs.asStateFlow()

	init {
		objScope.launch {
			loadLogsInternal()
		}
	}

	private suspend fun loadLogsInternal() {
		objMutex.withLock {
			try {
				if (fileLog.exists()) {
					val stringContent = fileLog.readText()
					_flowLogs.value = objJson.decodeFromString<List<ExecutionLogEntry>>(stringContent)
				}
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[ExecutionLogStore] Error reading execution logs from ${fileLog.absolutePath} - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			}
		}
	}

	/**
	 * Appends a new [ExecutionLogEntry] at the top of the log history and trims to [intMaxEntries].
	 *
	 * @param objEntry Newly completed execution entry.
	 * @param intMaxEntries Maximum number of log entries to retain on disk.
	 */
	suspend fun appendLogEntry(objEntry: ExecutionLogEntry, intMaxEntries: Int = 100) {
		objMutex.withLock {
			try {
				val listUpdated = (listOf(objEntry) + _flowLogs.value).take(intMaxEntries.coerceAtLeast(10))
				_flowLogs.value = listUpdated
				fileLog.writeText(objJson.encodeToString(listUpdated))
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[ExecutionLogStore] Error writing execution logs to ${fileLog.absolutePath} - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			}
		}
	}

	/**
	 * Clears all stored execution logs from memory and disk.
	 */
	suspend fun clearAllLogs() {
		objMutex.withLock {
			try {
				_flowLogs.value = emptyList()
				if (fileLog.exists()) {
					fileLog.delete()
				}
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[ExecutionLogStore] Error clearing execution logs - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			}
		}
	}

	companion object {
		private const val TAG = "ExecutionLogStore"

		@Volatile
		private var objInstance: ExecutionLogStore? = null

		/**
		 * Returns the singleton [ExecutionLogStore] instance.
		 *
		 * @param objContext Application context.
		 * @return Singleton [ExecutionLogStore].
		 */
		fun getInstance(objContext: Context): ExecutionLogStore {
			return objInstance ?: synchronized(this) {
				objInstance ?: ExecutionLogStore(objContext.applicationContext).also { objInstance = it }
			}
		}
	}
}
