package app.gamenative.wow.touch

import android.content.Context
import com.winlator.container.Container
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The input profile the container uses while the touch pane is on. The pane draws its own controls, so the
 * profile only exists to switch on upstream's virtual gamepad: WinHandler sends the profile's GamepadState
 * while the profile is a virtual gamepad and InputControlsView is showing. `loadElements` only marks a
 * profile as a virtual gamepad when one of its elements has a gamepad binding, so it carries one hidden
 * button. It sits behind the pane, which covers it, so it is never drawn over the game or touched.
 */
object TouchProfile {
    const val NAME = "WoW Touch"
    private const val VERSION = 1
    private const val PREFS = "wow_touch_pane"
    private const val KEY_VERSION = "profileVersion"

    /** Uses the profile while the pane is on, and goes back to the default profile when it is turned off. */
    fun applyTo(context: Context, container: Container, paneOn: Boolean) {
        val manager = InputControlsManager(context)
        val current = container.getExtra("profileId", "0").toIntOrNull() ?: 0
        val existing = manager.getProfiles(false).firstOrNull { it.name == NAME }
        if (!paneOn) {
            if (existing != null && current == existing.id) container.putExtra("profileId", null)
            return
        }
        val id = existing?.id ?: ((manager.getProfiles(false).maxOfOrNull { it.id } ?: 0) + 1)
        writeIfOutdated(context, id)
        container.putExtra("profileId", id.toString())
    }

    private fun writeIfOutdated(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = ControlsProfile.getProfileFile(context, id)
        if (file.isFile && prefs.getInt(KEY_VERSION, 0) >= VERSION) return
        file.parentFile?.mkdirs()
        file.writeText(profileJson(id).toString())
        prefs.edit().putInt(KEY_VERSION, VERSION).apply()
    }

    internal fun profileJson(id: Int): JSONObject = JSONObject()
        .put("id", id)
        .put("name", NAME)
        .put("cursorSpeed", 1)
        .put(
            "elements",
            JSONArray().put(
                JSONObject()
                    .put("type", "BUTTON")
                    .put("shape", "CIRCLE")
                    // Only gamepad bindings may be listed: a NONE here would stop the profile counting as a virtual gamepad.
                    .put("bindings", JSONArray(listOf("GAMEPAD_BUTTON_A")))
                    .put("scale", 0.1)
                    .put("x", 0.0)
                    .put("y", 0.0)
                    .put("toggleSwitch", false)
                    .put("text", "")
                    .put("iconId", 0)
                    .put("buttonOpacity", 0.0),
            ),
        )
        .put("controllers", JSONArray())
}
