package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Each host's [HostSnapshot], kept in a secure setting of its own that SystemUI
 * and Wallpaper & style can both read and write.
 *
 * Safe Mode is answered through the same place: whoever asks writes the answer
 * ([request]), and the host it is for, which alone can change what it holds
 * off, carries it out and clears it ([observeRequests]).
 */
class SnapshotStore(private val resolver: ContentResolver) {

    fun read(host: Host): HostSnapshot? =
        Settings.Secure.getString(resolver, snapshotKey(host))
            ?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }

    fun write(snapshot: HostSnapshot) {
        Settings.Secure.putString(resolver, snapshotKey(snapshot.host), encode(snapshot).toString())
    }

    /** Asks [host] to carry out [answer] for what its Safe Mode holds off. */
    fun request(host: Host, answer: SafeModeAnswer) {
        Settings.Secure.putString(resolver, requestKey(host), "${answer.name}:${System.currentTimeMillis()}")
    }

    /** Calls [onAnswer] on the main thread with every answer asked of [host], now and from now on, clearing each. */
    fun observeRequests(host: Host, onAnswer: (SafeModeAnswer) -> Unit) {
        val key = requestKey(host)
        fun take() {
            val value = Settings.Secure.getString(resolver, key) ?: return
            Settings.Secure.putString(resolver, key, null)
            SafeModeAnswer.entries.firstOrNull { value.startsWith(it.name) }?.let(onAnswer)
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = take()
            },
        )
        take()
    }

    private fun encode(snapshot: HostSnapshot): JSONObject = JSONObject()
        .put("host", snapshot.host.name)
        .put("hostVersion", snapshot.hostVersion)
        .put("build", snapshot.build)
        .put("bootedAt", snapshot.bootedAt)
        .put("xposedApi", snapshot.xposedApiVersion)
        .put("framework", snapshot.frameworkName)
        .put("frameworkVersion", snapshot.frameworkVersion)
        .put("held", JSONArray(snapshot.held.map { it.name }))
        .put("answered", snapshot.isAnswered)
        .put("faulted", JSONArray(snapshot.faulted.map { it.name }))
        .put("results", JSONArray(snapshot.report.results.map(::encode)))

    private fun encode(result: ContractResult): JSONObject = JSONObject()
        .put("signature", encode(result.contract.signature))
        .put("features", JSONArray(result.contract.features.map { it.name }))
        .put("required", result.contract.isRequired)
        .put("outcome", result.outcome.name)
        .apply { result.alternative?.let { put("alternative", encode(it)) } }

    private fun encode(signature: Signature): JSONObject = JSONObject()
        .put("owner", signature.owner)
        .apply {
            when (val member = signature.member) {
                null -> Unit
                is Member.Field -> put("field", member.name)
                is Member.Method -> {
                    put("method", member.name)
                    member.parameters?.let { put("parameters", JSONArray(it)) }
                }
            }
        }

    private fun decode(json: JSONObject): HostSnapshot = HostSnapshot(
        host = Host.valueOf(json.getString("host")),
        hostVersion = json.getString("hostVersion"),
        build = json.getString("build"),
        bootedAt = json.getLong("bootedAt"),
        xposedApiVersion = json.getInt("xposedApi"),
        frameworkName = json.getString("framework"),
        frameworkVersion = json.getString("frameworkVersion"),
        report = ContractReport(json.getJSONArray("results").objects().map(::decodeResult)),
        held = json.getJSONArray("held").strings().mapNotNull(Feature::of).toSet(),
        isAnswered = json.getBoolean("answered"),
        faulted = json.optJSONArray("faulted")?.strings()?.mapNotNull(Feature::of)?.toSet().orEmpty(),
    )

    private fun decodeResult(json: JSONObject): ContractResult = ContractResult(
        contract = HostContract(
            signature = decodeSignature(json.getJSONObject("signature")),
            features = json.getJSONArray("features").strings().mapNotNull(Feature::of).toSet(),
            isRequired = json.getBoolean("required"),
        ),
        outcome = ContractOutcome.valueOf(json.getString("outcome")),
        alternative = json.optJSONObject("alternative")?.let(::decodeSignature),
    )

    private fun decodeSignature(json: JSONObject): Signature {
        val member = when {
            json.has("field") -> Member.Field(json.getString("field"))
            json.has("method") -> Member.Method(json.getString("method"), json.optJSONArray("parameters")?.strings())
            else -> null
        }
        return Signature(json.getString("owner"), member)
    }

    private fun JSONArray.strings(): List<String> = List(length(), ::getString)

    private fun JSONArray.objects(): List<JSONObject> = List(length(), ::getJSONObject)

    private companion object {
        fun snapshotKey(host: Host) = "pixel_lock_screen_evolved_snapshot_${host.name.lowercase()}"
        fun requestKey(host: Host) = "pixel_lock_screen_evolved_safe_mode_${host.name.lowercase()}"
    }
}

/** What a person chose for the features Safe Mode holds off. */
enum class SafeModeAnswer { RESTORE, KEEP_DISABLED }
