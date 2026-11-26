package com.grayguava.legato.ui.player

import android.graphics.Bitmap
import java.io.File

/** A cover to draw with: a real one from LEGATO_SHOT_ART when set, else a red study in light and shadow. */
internal fun coverArt(): Bitmap {
    System.getenv("LEGATO_SHOT_ART")?.let { File(it, "00hd.jpg") }?.takeIf { it.isFile }?.let { f ->
        android.graphics.BitmapFactory.decodeFile(f.path)?.let { return it }
    }
    val b = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    c.drawColor(0xFF1A0505.toInt())
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.RadialGradient(380f, 260f, 320f, 0xFFD3271F.toInt(), 0x001A0505, android.graphics.Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, 600f, 600f, p)
    p.shader = null
    p.color = 0xFF0A0202.toInt()
    c.drawCircle(180f, 470f, 210f, p)
    return b
}
