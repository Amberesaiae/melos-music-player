package com.amberesaiae.melos.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

object R {
    @Composable
    fun string(id: Int): String = ""
    
    // App
    const val app_name = 0x7f010001
    
    // Settings Main
    const val settings = 0x7f020001
    const val back = 0x7f020002
    const val servers = 0x7f020003
    const val playback = 0x7f020004
    const val storage = 0x7f020005
    const val appearance = 0x7f020006
    const val settings_info = 0x7f020007
    
    // Server Settings
    const val server_settings = 0x7f020010
    const val add_server = 0x7f020011
    const val edit_server_title = 0x7f020012
    const val server_name = 0x7f020013
    const val server_name_hint = 0x7f020014
    const val server_url = 0x7f020015
    const val server_url_hint = 0x7f020016
    const val server_type = 0x7f020017
    const val username = 0x7f020018
    const val username_hint = 0x7f020019
    const val password = 0x7f02001a
    const val password_hint = 0x7f02001b
    const val test = 0x7f02001c
    const val test_connection = 0x7f02001d
    const val add = 0x7f02001e
    const val save = 0x7f02001f
    const val cancel = 0x7f020020
    const val connection_successful = 0x7f020021
    const val connection_failed = 0x7f020022
    const val connected = 0x7f020023
    const val disconnected = 0x7f020024
    const val testing = 0x7f020025
    const val not_tested = 0x7f020026
    const val subsonic = 0x7f020027
    const val jellyfin = 0x7f020028
    const val active_server = 0x7f020029
    const val select_server = 0x7f020030
    const val current_server = 0x7f020031
    const val edit_server = 0x7f020032
    const val delete_server = 0x7f020033
    const val move_up = 0x7f020034
    const val move_down = 0x7f020035
    const val no_servers_configured = 0x7f020036
    const val add_server_to_get_started = 0x7f020037
    const val show_password = 0x7f020038
    const val hide_password = 0x7f020039
    const val manage_music_servers = 0x7f020040
    
    // Audio Settings
    const val audio_settings = 0x7f020050
    const val equalizer_audio_effects = 0x7f020051
    
    // Cache Settings  
    const val cache_settings = 0x7f020060
    const val offline_cache_management = 0x7f020061
    
    // Appearance Settings
    const val appearance_settings = 0x7f020070
    const val themes_colors_display = 0x7f020071
}
