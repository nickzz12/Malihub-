package com.malihub.app

import android.content.Context
import org.json.JSONObject

object Engine {
    private val ENV = Regex("^(?:[A-Z][A-Z0-9_]*|mesa_glthread)=(.*)$")

    fun envFrom(profile: String): Map<String, String> =
        profile.lineSequence().map { it.trim() }
            .filter { !it.startsWith("#") && ENV.matches(it) }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    fun coresFrom(profile: String): String? =
        Regex("taskset -c ([0-9,]+)").find(profile)?.groupValues?.get(1)

    fun launch(ctx: Context, exeUri: String, profile: String): String {
        val env = envFrom(profile)
        val msg = "Motor Wine + Box64 ainda não integrado. Perfil lido: ${env.size} variáveis, núcleos ${coresFrom(profile) ?: "todos"}."
        return JSONObject().put("ok", false).put("msg", msg).toString()
    }
}
